package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.maplayers.base.visibility.ColonyKindLookup;
import kmu.maplayers.base.visibility.ColonyKnowledge;

/**
 * What a box knows about one system's colonies beyond what the contest made of them: what kind of
 * place each is, and how old its news of each is.
 *
 * <p>For an account whose rows arrive as scores rather than as colonies. A claim row carries the id
 * of the market it was weighed from and nothing of the place behind it, so the two things a line
 * says that no arithmetic can supply - that a colony is a collapse rather than a hulk, and that
 * nobody has looked at it in four cycles - have to be read from the system itself and matched back
 * by id.
 *
 * <p>The two travel as one value because they are one reading. Both are folded from the single walk
 * of the system the box already makes, and a line asks them together - so passed apart, a later
 * edit could fold one off a second walk, and the box would state a kind and a date read from two
 * different moments of the same system. Held together, that cannot be expressed.
 *
 * <p>Folded once for a whole box rather than per row, since an account lists a system's colonies
 * once per faction standing: resolved where a row is drawn, both would be re-read for every faction
 * the contest holds.
 *
 * <p>The kinds and the observations are nevertheless read under different rules, and deliberately.
 * A kind is classified under the box's own rule, so the account names places as the map drew them;
 * an observation is read under the fog alone, a reveal having no business dating a colony the
 * player was never told about ({@link ColonyObservationNotes}).
 */
public final class SystemColonyReading {

    /**
     * A box with no system to read: every colony reads as the ordinary kind and none carries a
     * remark. What a caller with nothing walked states, rather than inventing an empty pair.
     */
    public static final SystemColonyReading NONE =
        new SystemColonyReading(ColonyKindLookup.NONE, ColonyObservationNotes.NONE);

    private final ColonyKindLookup colonyKinds;
    private final ColonyObservationNotes notes;

    /**
     * Pairs two folds of one system that were made together. {@link #readColoniesIn} is how a box
     * arrives at the pair; this is the pair itself, for a caller already holding both halves.
     *
     * @param colonyKinds what kind of place each of the system's colonies is
     * @param notes       how old the news of each of them is
     */
    SystemColonyReading(ColonyKindLookup colonyKinds, ColonyObservationNotes notes) {

        this.colonyKinds = colonyKinds == null
            ? ColonyKindLookup.NONE
            : colonyKinds;

        this.notes = notes == null
            ? ColonyObservationNotes.NONE
            : notes;
    }

    /**
     * Reads one system's colonies into what an account may say about them, off the walk the box is
     * listing them from.
     *
     * @param sector    the sector the system stands in - where the player's fleet is, and what
     *                  clock the dates are read on
     * @param system    the hovered system
     * @param colonies  that system's colony set, as the box's own walk reported it
     * @param knowledge the box's own classification and register, so the kinds stated are the
     *                  kinds the map is drawn under and the dates come off the very observations
     *                  its projection was resolved against
     * @return what the box may say about those colonies; never null
     */
    public static SystemColonyReading readColoniesIn(
            SectorAPI sector,
            StarSystemAPI system,
            Colonies colonies,
            ColonyKnowledge knowledge) {

        return new SystemColonyReading(
            ColonyKindLookup.readKindsIn(colonies, knowledge),
            ColonyObservationNotes.readNotesFor(
                sector,
                system,
                colonies,
                knowledge == null ? null : knowledge.sightings()));
    }

    /**
     * What kind of place the colony with this id is.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return that colony's kind
     */
    public ColonyKind readKindOf(String colonyId) {
        return colonyKinds.readKindOf(colonyId);
    }

    /**
     * Runs a colony's line on into when it was last seen, where nobody is looking at it now.
     *
     * @param line     the colony's own line, as its account built it
     * @param colonyId the colony's market id, as the account listing it carries
     * @return the line, remarked on where a remark is due and untouched where none is
     */
    public CellTooltipEntryLine remarkOnColony(CellTooltipEntryLine line, String colonyId) {
        return notes.remarkOnColony(line, colonyId);
    }
}
