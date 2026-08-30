package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.colonies.ColonyDiscoveryLookup;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyKindLookup;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyLookup;

/**
 * What a box knows about one system's colonies beyond what the contest made of them: what kind of
 * place each is, whether the player has found it, whether a concealed one is concealed in name
 * only, and how old its news of each is.
 *
 * <p>For an account whose rows arrive as scores rather than as colonies. A claim row carries the id
 * of the market it was weighed from and nothing of the place behind it, so the things a line says
 * that no arithmetic can supply - that a colony is a collapse rather than a hulk, that nobody has
 * found it, that nobody has looked at it in four cycles - have to be read from the system itself
 * and matched back by id.
 *
 * <p>The four travel as one value because they are one reading. All are folded from the single
 * walk of the system the box already makes, and a line asks them together - so passed apart, a
 * later edit could fold one off a second walk, and the box would state a kind and a date read from
 * two different moments of the same system. Held together, that cannot be expressed.
 *
 * <p>Folded once for a whole box rather than per row, since an account lists a system's colonies
 * once per faction standing: resolved where a row is drawn, each would be re-read for every faction
 * the contest holds.
 *
 * <p>The four are nevertheless read under different rules, and deliberately. A kind is classified
 * under the box's own rule, so the account names places as the map drew them; discovery is the
 * entity's own flag, which no rule reaches; whether a concealment is public knowledge is an
 * identity the composition root supplies
 * ({@link kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyRegistry}); an observation is read under
 * the fog alone, a reveal having no business dating a colony the player was never told about
 * ({@link ColonyObservationNotes}).
 *
 * <p>Laying what it knows onto a line is this value's own ({@link #describeColony}) rather than
 * each account's, so a colony's line carries the same things wherever it was built. The date is
 * reachable no other way, which is what keeps that true: an account cannot lay half of what a
 * colony's line owes it and still compile.
 */
public final class SystemColonyReading {

    /**
     * A box with no system to read: every colony reads as the ordinary kind, every one as found,
     * every concealment as a secret, and none carries a remark. What a caller with nothing walked
     * states, rather than inventing an empty reading of its own.
     */
    public static final SystemColonyReading NONE = new SystemColonyReading(
        ColonyKindLookup.NONE,
        ColonyDiscoveryLookup.NONE,
        OpenlyKnownColonyLookup.NONE,
        ColonyObservationNotes.NONE);

    private final ColonyKindLookup colonyKinds;
    private final ColonyDiscoveryLookup colonyDiscoveries;
    private final OpenlyKnownColonyLookup openlyKnownColonies;
    private final ColonyObservationNotes notes;

    /**
     * Gathers four folds of one system that were made together. {@link #readColoniesIn} is how a
     * box arrives at them; this is the gathering itself, for a caller already holding the parts.
     *
     * @param colonyKinds         what kind of place each of the system's colonies is
     * @param colonyDiscoveries   which of them the player has yet to find
     * @param openlyKnownColonies which of the concealed ones the sector openly points at
     * @param notes               how old the news of each of them is
     */
    SystemColonyReading(
            ColonyKindLookup colonyKinds,
            ColonyDiscoveryLookup colonyDiscoveries,
            OpenlyKnownColonyLookup openlyKnownColonies,
            ColonyObservationNotes notes) {

        this.colonyKinds = colonyKinds == null
            ? ColonyKindLookup.NONE
            : colonyKinds;

        this.colonyDiscoveries = colonyDiscoveries == null
            ? ColonyDiscoveryLookup.NONE
            : colonyDiscoveries;

        this.openlyKnownColonies = openlyKnownColonies == null
            ? OpenlyKnownColonyLookup.NONE
            : openlyKnownColonies;

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
            ColonyDiscoveryLookup.readDiscoveriesIn(colonies),
            OpenlyKnownColonyLookup.readOpenlyKnownIn(colonies),
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
     * Whether the player has found the entity the colony with this id sits on.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return true when the colony's entity has been found
     */
    public boolean isDiscoveredColony(String colonyId) {
        return colonyDiscoveries.isDiscoveredColony(colonyId);
    }

    /**
     * Whether the colony with this id conceals itself only in the sector's bookkeeping - a landmark
     * that keeps no comm directory rather than a base hiding from anyone.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return true when the colony is one the sector openly points at
     */
    public boolean isOpenlyKnownColony(String colonyId) {
        return openlyKnownColonies.isOpenlyKnownColony(colonyId);
    }

    /**
     * The three ways the colony with this id may be out of plain view, gathered into the value a
     * line's wording is chosen from.
     *
     * <p>Assembled here because two of the three are this reading's own answers and only the third
     * comes off the account. Left to each account, the assembly is the same three arguments written
     * out per box, where transposing two of them still compiles and simply calls out the wrong word.
     *
     * @param colonyId       the colony's market id, as the account listing it carries
     * @param isHiddenMarket whether the colony conceals itself, which only the account's own
     *                       breakdown carries
     * @return the three facts, in the order the value names them
     */
    public ColonyConcealment readConcealmentOf(String colonyId, boolean isHiddenMarket) {
        return new ColonyConcealment(
            isDiscoveredColony(colonyId),
            isHiddenMarket,
            isOpenlyKnownColony(colonyId));
    }

    /**
     * Runs a colony's line on into everything a box has to say about the place beside its own
     * number: what it has found out about it, and how current that account is.
     *
     * <p>The two are laid together rather than offered apart, because they are due on the same lines
     * for the same reason - a row naming a colony says what the arithmetic could not - and an
     * account reaching them separately is one that can lay one and forget the other. Which it
     * forgets is invisible in the code that forgot it: a line missing its date reads exactly like a
     * colony somebody is standing over.
     *
     * <p>The findings are handed in rather than read here, because only the account holds them. A
     * claim is the contest's own outcome and a concealment travels on the breakdown, so a reading
     * folded off the system could state neither - which is also why the kind and the discovery
     * answer stay separately readable ({@link #readKindOf}, {@link #isDiscoveredColony}): an
     * account that has the place itself in hand fills them in from that instead.
     *
     * @param line     the colony's own line, as its account built it
     * @param colonyId the colony's market id, as the account listing it carries
     * @param facts    what the account has found out about the colony; null states nothing
     * @return the line, called out and remarked on where either is due and untouched where neither
     *         is
     */
    public CellTooltipEntryLine describeColony(
            CellTooltipEntryLine line,
            String colonyId,
            ColonyQualifierFacts facts) {

        return ColonyQualifier.qualifyColony(notes.remarkOnColony(line, colonyId), facts);
    }
}
