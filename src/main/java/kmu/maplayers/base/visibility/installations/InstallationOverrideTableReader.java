package kmu.maplayers.base.visibility.installations;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static kmu.util.KmuValues.normalizeText;

/**
 * Reads the shipped installation table out of the game's data files.
 *
 * <p>Read as a <em>merged</em> spreadsheet, which is the whole reason the disposition of a place
 * lives in a file rather than in code: any mod shipping the same path adds rows to it. A mod that
 * tags its own station needs nothing from anybody; one that does not costs a single row, and that
 * row can be written by its own author instead of arriving here as a patch.
 *
 * <p>Every failure is an empty answer rather than an exception, at both scales. A file a mod update
 * half-broke must still leave the map drawing: the alternative is a parse error taking down the
 * layer that was only consulting the file, over rows about entity types the player may not even
 * have in their sector. So an unreadable file reads as a table stating nothing, an unreadable row
 * is dropped while its neighbours load, and an unreadable cell falls to its own column's default -
 * each with a log line, so a modder who mistyped a row can find out why it did nothing.
 *
 * <p>The three narrowing defaults all point the same way. Absent means "the facts decide", never
 * "admitted" and never a kind - the timid direction, since a row that fails to admit a station
 * leaves one place off the map while a row that invents a holder hands somebody a system.
 *
 * <p>Takes its settings rather than reaching for them, so the parsing can be exercised without a
 * running game - and so the reach that only works inside one is made where the rest of the mod is
 * assembled.
 */
public final class InstallationOverrideTableReader {

    // The file the game is handed. Package-private so that whatever holds the shipped rows against
    // this reader opens that very file: a second spelling of the path would let the two drift apart
    // silently, each still passing on a file the other never sees.
    static final String TABLE_PATH = "data/config/kmu/installations.csv";

    // The columns a row is read by, and the id column the game folds every mod's rows onto.
    private static final String ENTITY_TYPE_COLUMN = "entityTypeId";
    private static final String ADMISSION_COLUMN = "isAdmitted";
    private static final String KIND_COLUMN = "kind";

    // Which mod owns the table, which is what makes the read a merge: the game gathers this path
    // from every enabled mod and folds the rows onto the ones shipped here, keyed by entity type.
    private static final String OWNING_MOD_ID = "kmu";

    // The two words the admission column understands. Written out rather than parsed through
    // Boolean.parseBoolean, which reads every other word in the language as false - so a mistyped
    // "yes" would silently suppress the very entity type its author meant to list.
    private static final String STATED_ADMITTED = "true";
    private static final String STATED_SUPPRESSED = "false";

    private static final Logger LOG = Global.getLogger(InstallationOverrideTableReader.class);

    private final SettingsAPI settings;

    public InstallationOverrideTableReader(SettingsAPI settings) {
        this.settings = Objects.requireNonNull(
            settings, "An installation table is read out of the game's settings: they are needed.");
    }

    /**
     * Reads what every mod's copy of the table states.
     *
     * <p>Answers a table however badly the read went, so a caller has one shape to handle rather
     * than a shape and a failure.
     *
     * @return what the merged file states, or {@link InstallationOverrideTable#NONE} where nothing
     *         readable stands behind it
     */
    public InstallationOverrideTable readTable() {

        var rows = readRows();

        if (rows == null) {
            return InstallationOverrideTable.NONE;
        }
        var overridesByEntityTypeId = new HashMap<String, InstallationOverride>();

        for (var rowIndex = 0; rowIndex < rows.length(); rowIndex++) {
            readRowInto(overridesByEntityTypeId, rows.opt(rowIndex), rowIndex);
        }
        return new InstallationOverrideTable(overridesByEntityTypeId);
    }

    // The merged rows of the table, or null where there are none to be had - an absent file, one
    // that will not parse, or a game not up far enough to have data files at all.
    //
    // Broad on purpose: the call throws two checked kinds and the disk beneath it can raise
    // anything, while every one of them costs the caller the same thing - a table it must go on
    // without. Letting one out would take down whichever pass was consulting the file.
    private JSONArray readRows() {
        try {
            return settings.getMergedSpreadsheetDataForMod(
                ENTITY_TYPE_COLUMN, TABLE_PATH, OWNING_MOD_ID);

        } catch (Exception readFailed) {
            LOG.warn(
                "Could not read '" + TABLE_PATH + "'; every installation will be listed and "
                    + "classified from what the sector says about it alone.",
                readFailed);
            return null;
        }
    }

    // One row folded into the table under the entity type it names.
    //
    // The id is what a row cannot do without: a statement about no type is a statement about
    // nothing, and filing one under a blank key would hand its answers to every other unnamed row.
    // A row stating only its id is kept as it is - it says nothing, which is a fair thing for a row
    // to say, and dropping it would make an author's placeholder look like a row that failed.
    private void readRowInto(
            Map<String, InstallationOverride> overridesByEntityTypeId,
            Object row,
            int rowIndex) {

        if (!(row instanceof JSONObject rowFields)) {
            LOG.warn(
                "Row " + rowIndex + " of '" + TABLE_PATH + "' is not a row of the table and was "
                    + "skipped; the rows around it were read.");
            return;
        }
        var entityTypeId = normalizeText(rowFields.optString(ENTITY_TYPE_COLUMN, null));

        if (entityTypeId == null) {
            LOG.warn(
                "Row " + rowIndex + " of '" + TABLE_PATH + "' names no '" + ENTITY_TYPE_COLUMN
                    + "' and was skipped; the rows around it were read.");
            return;
        }
        overridesByEntityTypeId.put(
            entityTypeId,
            new InstallationOverride(
                readAdmission(rowFields, entityTypeId),
                readKind(rowFields, entityTypeId)));
    }

    // Whether a row lists its type on the map at all. A blank cell is the ordinary way to leave
    // the question to the facts and passes without comment; anything else that is not one of the
    // two words is a row its author meant something by, so it says so before falling back.
    private Optional<Boolean> readAdmission(JSONObject rowFields, String entityTypeId) {

        var stated = normalizeText(rowFields.optString(ADMISSION_COLUMN, null));

        if (stated == null) {
            return Optional.empty();
        }
        if (STATED_ADMITTED.equalsIgnoreCase(stated)) {
            return Optional.of(Boolean.TRUE);
        }
        if (STATED_SUPPRESSED.equalsIgnoreCase(stated)) {
            return Optional.of(Boolean.FALSE);
        }
        LOG.warn(
            "'" + entityTypeId + "' in '" + TABLE_PATH + "' states '" + stated + "' for '"
                + ADMISSION_COLUMN + "', which is neither '" + STATED_ADMITTED + "' nor '"
                + STATED_SUPPRESSED + "'; whether it is listed was left to the sector.");
        return Optional.empty();
    }

    // What a row says its type counts as, where it says. A name no kind answers to falls to the
    // facts for the reason every default here does: a kind guessed from a misspelling is a holder
    // nobody wrote down.
    private Optional<InstallationKind> readKind(JSONObject rowFields, String entityTypeId) {

        // Normalised here as well as inside the lookup, because a blank cell and a cell nothing
        // answers to are the same absent answer and must not be the same log line: the first is
        // how most rows leave the kind to the facts.
        var stated = normalizeText(rowFields.optString(KIND_COLUMN, null));
        var kind = InstallationKind.findKindNamed(stated);

        if (stated != null && kind.isEmpty()) {
            LOG.warn(
                "'" + entityTypeId + "' in '" + TABLE_PATH + "' states the unknown '" + KIND_COLUMN
                    + "' of '" + stated + "'; what it counts as was left to the sector.");
        }
        return kind;
    }
}
