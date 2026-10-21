package kmu.maplayers.politicalmap.tooltip;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.colonies.ColonyFactsLookup;
import kmu.maplayers.base.visibility.colonies.ColonyKind;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The box's walk of a system, posed as a case needs it: one colony of a stated kind, one the player
 * has yet to find, one the sector openly points at, or one the box has something to remark on.
 *
 * <p>Shared by every row resolver's suite because they pose the same systems. Each resolver holds a
 * carrier the others have never heard of, but none carries what a reading answers, which is exactly
 * why one reading serves them all.
 */
public final class SystemColonyReadingFixture {

    /**
     * What a remarked line runs on into. Stated verbatim rather than composed, the suites taking
     * this being about which line carries a remark rather than about how one reads.
     */
    public static final String LAST_SEEN = "last seen 34 days ago (c206.05.12)";

    // What a walk with nothing to remark on lays on every line: nothing at all.
    private static final BiFunction<CellTooltipEntryLine, String, CellTooltipEntryLine> NO_REMARK =
        (line, colonyId) -> line;

    // A fold that met no colony of the kind the case leaves unposed.
    private static final Map<String, ColonyKind> NO_KINDS = Map.of();
    private static final Set<String> NO_COLONY_IDS = Set.of();

    private SystemColonyReadingFixture() {
    }

    /**
     * A walk of a system holding one colony of the given kind and nothing else worth stating.
     *
     * @param colonyId the colony's market ID, as the account listing it carries
     * @param kind     what sort of place the walk classified the colony as
     * @return a reading answering that colony's kind and the ordinary colony for every other
     */
    public static SystemColonyReading buildReadingWithKind(String colonyId, ColonyKind kind) {

        var kindByColonyId = Map.of(colonyId, kind);

        return new SystemColonyReading(
            new ColonyFactsLookup(kindByColonyId, NO_COLONY_IDS, NO_COLONY_IDS),
            NO_REMARK);
    }

    /**
     * A walk of a system holding one colony whose entity the player has yet to discover, which is
     * the half of a line's account neither a score nor a weight can supply.
     *
     * @param colonyId the colony's market ID, as the account listing it carries
     * @return a reading answering that colony undiscovered and every other discovered
     */
    public static SystemColonyReading buildReadingWithUndiscovered(String colonyId) {

        var undiscoveredColonyIds = Set.of(colonyId);

        return new SystemColonyReading(
            new ColonyFactsLookup(NO_KINDS, undiscoveredColonyIds, NO_COLONY_IDS),
            NO_REMARK);
    }

    /**
     * A walk of a system holding one concealed colony the sector openly points at, which is the
     * other half of a line's account no carrier of either box holds.
     *
     * @param colonyId the colony's market ID, as the account listing it carries
     * @return a reading answering that colony openly known and every other a secret
     */
    public static SystemColonyReading buildReadingWithOpenlyKnown(String colonyId) {

        var openlyKnownColonyIds = Set.of(colonyId);

        return new SystemColonyReading(
            new ColonyFactsLookup(NO_KINDS, NO_COLONY_IDS, openlyKnownColonyIds),
            NO_REMARK);
    }

    /**
     * A walk remarking on exactly one colony. The remark is posed directly because what makes one
     * due is the notes' own question, pinned beside the notes; what the suites taking this are about
     * is which line carries the answer.
     *
     * @param colonyId the colony's market ID, as the account listing it carries
     * @return a reading remarking on that colony and on nothing else
     */
    public static SystemColonyReading buildReadingRemarkingOn(String colonyId) {

        return new SystemColonyReading(
            ColonyFactsLookup.NONE,
            (line, remarkedColonyId) -> colonyId.equals(remarkedColonyId)
                ? line.notedWith(LAST_SEEN)
                : line);
    }
}
