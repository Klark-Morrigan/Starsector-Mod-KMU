package kmu.maplayers.ownermap.holding;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKeyedMemo;

import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One rebuild's reading of a sector, as any owner-painted map layer needs it: which sector, how
 * factions fold into blocs, the {@link ColonyReadRules} its colony reads are taken under, and the
 * one walk of each system all of them are answered from.
 *
 * <p>Only what every such layer shares. The mechanic deciding <em>who</em> paints - weighing
 * markets, a relation between factions - is the layer's own and travels beside this, which is what
 * lets the holder seam take a pass at all: a seam naming one mechanic's pass could only be
 * implemented by layers painting by that mechanic. Anything answered by weighing markets therefore
 * belongs on that mechanic's pass, however generic its inputs look.
 *
 * <p>Opened where a rebuild begins and discarded with it, so the sector is read at one moment and
 * each system is walked once for the whole rebuild. The sector is the index's rather than a field
 * of its own, so a pass cannot name one sector while answering out of another.
 *
 * <p>A class rather than a record because it remembers as well as carries - the colony walk, each
 * colony's kind and each system's habitation are memoised snapshots - so two passes over one sector
 * are two readings and value equality would be wrong. Not safe for concurrent use.
 */
public final class HolderPass {

    // Each system's habitation, resolved on first ask. Memoised because the filter's holder resolve,
    // the inhabitation scan and each picker's stats fold all ask per system in separate walks.
    //
    // Keyed as the colony memo beneath it is: keyed differently, the two would disagree over a pair
    // of systems sharing an ID, and the second would draw the first's inhabitants over its cell.
    private final SystemKeyedMemo<SystemHabitation> habitationBySystem = new SystemKeyedMemo<>();

    private final ColonyKnowledge colonyKnowledge;
    private final ColonyReadRules colonyReadRules;
    private final HolderGrouping grouping;
    private final SectorPassIndex sectorIndex;

    /**
     * Opens a pass over an already-built reading of the sector.
     *
     * <p>Every argument is required: they are values the rebuild's opener already holds, so a null
     * is a fault at that one place. Standing the fog in for missing rules would turn that fault into
     * a map that quietly draws less.
     *
     * @param sectorIndex     the pass's one reading of the sector, shared by every read made
     *                        through it
     * @param colonyReadRules what the player may be shown of a colony, and what a decivilised
     *                        world counts as - every colony read through this pass is taken under
     *                        both
     * @param grouping        the grouping that folds factions into blocs before any mechanic
     *                        compares them; the identity grouping leaves every faction its own bloc
     */
    public HolderPass(
            SectorPassIndex sectorIndex,
            ColonyReadRules colonyReadRules,
            HolderGrouping grouping) {

        this.colonyReadRules = Objects.requireNonNull(colonyReadRules, "colonyReadRules");
        this.grouping = Objects.requireNonNull(grouping, "grouping");
        this.sectorIndex = Objects.requireNonNull(sectorIndex, "sectorIndex");

        // The visibility rule is paired with the sector's sighting register here, the one point at
        // which both are in hand.
        this.colonyKnowledge = ColonyKnowledge.over(
            sectorIndex.getSector(),
            colonyReadRules.colonyVisibility());
    }

    /**
     * A pass over one sector under explicit colony rules, opening the colony index its reads share.
     *
     * @param sector          the sector this pass reads; null yields a pass answering an empty
     *                        colony set for every system
     * @param colonyReadRules what the player may be shown of a colony, and what a decivilised
     *                        world counts as
     * @param grouping        the grouping this pass folds factions into blocs under
     * @return a pass over that sector carrying those rules
     */
    public static HolderPass over(
            SectorAPI sector,
            ColonyReadRules colonyReadRules,
            HolderGrouping grouping) {

        return new HolderPass(
            new SectorPassIndex(sector),
            colonyReadRules,
            grouping);
    }

    /**
     * A pass under the player's live colony rules ({@link ColonyReadRules#readFromLunaSettings})
     * and an explicit grouping.
     *
     * @param sector   the sector this pass reads
     * @param grouping the grouping this pass folds factions into blocs under
     * @return a pass carrying the live rules paired with the grouping
     */
    public static HolderPass readFromLunaSettings(SectorAPI sector, HolderGrouping grouping) {
        return over(sector, ColonyReadRules.readFromLunaSettings(), grouping);
    }

    /**
     * @return the grouping every per-bloc read through this pass is made under
     */
    public HolderGrouping grouping() {
        return grouping;
    }

    /**
     * What the player may be told about the colonies this pass walks: its rule, read against the
     * sector's own record of what has been seen.
     *
     * <p>Published so a reader needing a projection this pass does not name takes the pass's own
     * knowledge rather than pairing a rule with a register itself - a surface reading one pass's
     * rule against another's observations would withhold colonies nothing else on the map does.
     *
     * @return the knowledge every colony projection through this pass is taken under
     */
    public ColonyKnowledge colonyKnowledge() {
        return colonyKnowledge;
    }

    /**
     * @return the reading of the sector this pass answers out of, so a reader can open something
     *         of its own over the same walk
     */
    public SectorPassIndex sectorIndex() {
        return sectorIndex;
    }

    /**
     * @return the sector this pass reads, as its index names it; null when opened over none
     */
    public SectorAPI sector() {
        return sectorIndex.getSector();
    }

    /**
     * The systems this pass walks, in the sector's own order. Empty for a pass over no sector, so a
     * resolve states its walk without guarding the unreachable case first.
     *
     * @return the sector's star systems; empty when the pass was opened over no sector
     */
    public List<StarSystemAPI> readSystems() {

        var sector = sector();
        return sector == null ? List.of() : sector.getStarSystems();
    }

    /**
     * Whether this pass can read an economy at all - a sector to walk, with its economy up. Named
     * once because every weighing resolve meets the unreachable and the mid-load sector, and reports
     * nothing rather than an empty sector in both; stating it once stops one resolve testing half.
     *
     * @return true when both the sector and its economy are there to read
     */
    public boolean canReadEconomy() {

        var sector = sector();
        return sector != null && sector.getEconomy() != null;
    }

    /**
     * The colonies in one system, off this pass's single walk of it, for a reader needing the
     * colonies themselves rather than a mechanic's verdict on them.
     *
     * @param system the system to read; null yields an empty set
     * @return the system's colony set
     */
    public Colonies readColoniesIn(StarSystemAPI system) {
        return sectorIndex.readColoniesIn(system);
    }

    /**
     * The colonies in one system the player may be shown, under the rule the pass was opened with.
     *
     * <p>Named here rather than composed at each display reader, because the fill, the band inside
     * it and the box over it all have to withhold the same colonies.
     *
     * @param system the system to read; null yields an empty list
     * @return the system's colonies the rule admits, in the set's own order
     */
    public List<Colony> readKnownColoniesIn(StarSystemAPI system) {
        return colonyKnowledge.readKnownColonies(readColoniesIn(system));
    }

    /**
     * The colonies in one system that amount to people living there, under the visibility rule and
     * this pass's habitation rule.
     *
     * <p>Beside the known listing rather than in place of it: "who may be named here" and "is
     * anybody living here" are different questions, and a derelict somebody has seen answers the
     * first and settles nothing in the second.
     *
     * <p>The habitation rule lands here rather than at the fold below, because this list is also
     * read directly. It names the one decivilised kind rather than asking the kind itself, so a kind
     * not yet written inhabits its place until it says otherwise.
     *
     * @param system the system to read; null yields an empty list
     * @return the system's known colonies somebody lives on under this pass's habitation rule, in
     *         the set's own order
     */
    public List<Colony> readInhabitingColoniesIn(StarSystemAPI system) {

        var inhabitingColonies = colonyKnowledge.readInhabitingColonies(readColoniesIn(system));

        if (colonyReadRules.decivilisedColonyHabitation().isCountedAsPopulated()) {
            return inhabitingColonies;
        }
        return inhabitingColonies.stream()
            .filter(colony -> colonyKnowledge.readKindOf(colony) != ColonyKind.UNGOVERNED_COLONY)
            .toList();
    }

    /**
     * The factions the player may be told about in one system - the owners of the known listing,
     * each named once.
     *
     * <p>Answered here, off the one projection, rather than derived by each mechanic from what it
     * scored, so every box over one cell names the same factions. The listing rather than
     * habitation: a seen derelict has an owner to name and nobody living on it.
     *
     * @param system the system to read; null yields an empty set
     * @return the IDs of the factions holding a colony the rule admits, in the projection's own
     *         order
     */
    public Set<String> readKnownColonyFactionIds(StarSystemAPI system) {
        return collectFactionIdsOf(readKnownColoniesIn(system));
    }

    /**
     * What one system's habitation amounts to, off this pass's one walk of it; {@link
     * SystemHabitation} says why it is one value rather than a read apiece. Worked out on the first
     * ask and remembered for the rest of the pass.
     *
     * @param system the system to read; null yields an empty habitation
     * @return the system's habitation under this pass's rule and grouping
     */
    public SystemHabitation readHabitationIn(StarSystemAPI system) {

        if (system == null) {
            return resolveHabitationIn(null);
        }
        return habitationBySystem.readValueFor(system, this::resolveHabitationIn);
    }

    // One system's habitation worked out, for the memo above to remember: the habitation
    // projection over this pass's walk, with the blocs and their sizes folded from those very
    // colonies.
    private SystemHabitation resolveHabitationIn(StarSystemAPI system) {

        var inhabitingColonies = readInhabitingColoniesIn(system);

        return new SystemHabitation(
            inhabitingColonies,
            grouping.regroupByBloc(
                sumColonySizesByFaction(inhabitingColonies),
                0,
                Integer::sum));
    }

    // Each owner's summed raw colony size among the colonies handed in - the sizes the habitation
    // fold above regroups.
    //
    // Summed per owner before the grouping rather than per bloc after it, so the one fold that
    // decides which owners can be named a bloc for decides it for the sizes as well: a bloc's
    // presence and the size beside it are then the same colonies counted twice over, never two
    // selections that could differ by one.
    private static Map<String, Integer> sumColonySizesByFaction(List<Colony> colonies) {

        var sizeByFactionId = new LinkedHashMap<String, Integer>();
        for (var colony : colonies) {
            sizeByFactionId.merge(
                colony.readOwnerId(),
                colony.market().getSize(),
                Integer::sum);
        }
        return sizeByFactionId;
    }

    // The owners of one projection's colonies, each named once however many it holds there.
    //
    // Asked of the colony rather than of the faction hanging off its market, as the fold above is.
    // A market states its owner twice - the ID it stores, and the faction that ID resolves to -
    // and a pass reading one where something beside it reads the other would be two answers to a
    // question the sector has one of.
    private static Set<String> collectFactionIdsOf(List<Colony> colonies) {

        var factionIds = new LinkedHashSet<String>();
        for (var colony : colonies) {
            factionIds.add(colony.readOwnerId());
        }
        return factionIds;
    }
}
