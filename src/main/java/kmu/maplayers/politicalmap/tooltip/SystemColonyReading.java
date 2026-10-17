package kmu.maplayers.politicalmap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.colonies.ColonyFactsLookup;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * What a box knows about one system's colonies beyond what the painting mechanic made of them: what
 * kind of place each is, whether the player has found it, whether a concealed one is concealed in
 * name only, and how old its news of each is.
 *
 * <p>For an account whose rows arrive as scores rather than as colonies. Such a row carries the ID
 * of the market it was weighed from and nothing of the place behind it, so the things a line says
 * that no arithmetic can supply - that a colony is a collapse rather than a derelict, that nobody
 * has found it, that nobody has looked at it in four cycles - have to be read from the system
 * itself and matched back by ID.
 *
 * <p>The facts and the notes travel as one value because they are one reading. Both are folded from
 * the single walk of the system the box already makes, and a line asks them together - so passed
 * apart, one could be folded off a second walk, and the box would state a kind and a date read from
 * two different moments of the same system. Held together, that cannot be expressed.
 *
 * <p>Folded once for a whole box rather than per row, since an account lists a system's colonies
 * once per faction standing: resolved where a row is drawn, each would be re-read for every faction
 * the box lists.
 *
 * <p>The four are nevertheless read under different rules, and deliberately. A kind is classified
 * under the box's own rule, so the account names places as the map drew them; discovery is the
 * entity's own flag, which no rule reaches; whether a concealment is public knowledge is an
 * identity the composition root supplies
 * ({@link kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyRegistry}); an observation is read under
 * the fog alone, a reveal having no business dating a colony the player was never told about
 * ({@link ColonyObservationNotes}).
 */
public final class SystemColonyReading {

    // What a line not led by the painting layer's own finding carries in its place.
    private static final String NO_LEADING_FINDING = null;

    /**
     * A box with no system to read: every colony reads as the ordinary kind, every one as found,
     * every concealment as a secret, and none carries a remark. What a caller with nothing walked
     * states, rather than inventing an empty reading of its own.
     */
    public static final SystemColonyReading NONE = new SystemColonyReading(
        ColonyFactsLookup.NONE,
        ColonyObservationNotes.NONE::remarkOnColony);

    private final ColonyFactsLookup colonyFacts;
    private final BiFunction<CellTooltipEntryLine, String, CellTooltipEntryLine> remarkOnColony;

    /**
     * Gathers two folds of one system that were made together.
     *
     * <p>The notes arrive as the one thing this reading does with them - running a colony's line on
     * into how old its news is - so the reading depends on laying the remark and not on how the
     * notes were gathered.
     *
     * @param colonyFacts    what kind of place each of the system's colonies is, which of them the
     *                       player has yet to find, and which concealed ones the sector openly
     *                       points at
     * @param remarkOnColony runs a colony's line, named by its market ID, on into how old the news of
     *                       it is
     */
    SystemColonyReading(
            ColonyFactsLookup colonyFacts,
            BiFunction<CellTooltipEntryLine, String, CellTooltipEntryLine> remarkOnColony) {

        this.colonyFacts = Objects.requireNonNull(colonyFacts, "colonyFacts");
        this.remarkOnColony = Objects.requireNonNull(remarkOnColony, "remarkOnColony");
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
    public static SystemColonyReading readColonyReadingOf(
            SectorAPI sector,
            StarSystemAPI system,
            Colonies colonies,
            ColonyKnowledge knowledge) {

        var notes = ColonyObservationNotes.readNotesFor(
            sector,
            system,
            colonies,
            knowledge == null ? null : knowledge.sightings());

        return new SystemColonyReading(
            ColonyFactsLookup.readFactsIn(colonies, knowledge),
            notes::remarkOnColony);
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
     * @param line   the colony's own line, as its account built it
     * @param colony the colony the line names, with the facts only its account holds
     * @return the line, called out and remarked on where either is due and untouched where neither
     *         is
     */
    public CellTooltipEntryLine describeColony(CellTooltipEntryLine line, AccountedColony colony) {
        return describeColonyWith(line, colony, colonyFacts.readKindOf(colony.marketId()), NO_LEADING_FINDING);
    }

    /**
     * The same description, led by a finding the painting layer states ahead of every other.
     *
     * <p>The leading finding is handed in rather than read here, because it is the painting
     * mechanic's own outcome and a reading folded off the system cannot state it.
     *
     * @param line           the colony's own line, as its account built it
     * @param colony         the colony the line names, with the facts only its account holds
     * @param leadingFinding the finding, already worded
     * @return the line, called out and remarked on where either is due and untouched where neither
     *         is
     */
    public CellTooltipEntryLine describeColony(
            CellTooltipEntryLine line,
            AccountedColony colony,
            String leadingFinding) {

        return describeColonyWith(line, colony, colonyFacts.readKindOf(colony.marketId()), leadingFinding);
    }

    /**
     * The same description for an account that already holds the colony's kind off the selection
     * that met the colony, so the kind stated can only be about the colony the line names.
     *
     * @param line   the colony's own line, as its account built it
     * @param colony the colony the line names, with the facts only its account holds
     * @param kind   what sort of place the colony is, as the account's own selection read it
     * @return the line, called out and remarked on where either is due and untouched where neither
     *         is
     */
    public CellTooltipEntryLine describeColony(
            CellTooltipEntryLine line,
            AccountedColony colony,
            ColonyKind kind) {

        return describeColonyWith(line, colony, kind, NO_LEADING_FINDING);
    }

    private CellTooltipEntryLine describeColonyWith(
            CellTooltipEntryLine line,
            AccountedColony colony,
            ColonyKind kind,
            String leadingFinding) {

        return ColonyQualifier.qualifyColony(
            remarkOnColony.apply(line, colony.marketId()),
            readQualifierFacts(colony, kind, leadingFinding));
    }

    // Everything the line may call out, gathered into the value its wording is chosen from: the
    // concealment answered here, the rest handed in.
    private ColonyQualifierFacts readQualifierFacts(
            AccountedColony colony,
            ColonyKind kind,
            String leadingFinding) {

        return new ColonyQualifierFacts(
            kind,
            leadingFinding,
            readConcealmentOf(colony),
            colony.isListedByEconomy());
    }

    // The three ways the colony may be out of plain view: two answered by this reading, and the
    // concealment flag only the account's own carrier holds.
    private ColonyConcealment readConcealmentOf(AccountedColony colony) {

        var colonyId = colony.marketId();

        return new ColonyConcealment(
            colonyFacts.isDiscoveredColony(colonyId),
            colony.isHiddenMarket(),
            colonyFacts.isOpenlyKnownColony(colonyId));
    }
}
