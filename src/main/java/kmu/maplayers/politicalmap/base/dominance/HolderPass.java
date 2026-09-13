package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKeyedMemo;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One rebuild's reading of a sector, as any owner-painted map layer needs it: which sector, how
 * factions fold into blocs, what the player may be shown of a colony, and the one walk of each
 * system all three are answered from.
 *
 * <p>What every layer that paints somebody's territory has in common, and no more than that.
 * The mechanic deciding <em>who</em> paints - dominance weights, a claim, a diplomatic relation -
 * is the layer's own, and each carries its rule beside this rather than inside it
 * ({@link DominancePass} is this plus the weighting rule). That is what lets the holder seam take
 * a pass at all: a seam naming a whole mechanic's pass could only be implemented by layers that
 * paint by that mechanic. Anything answered by weighing markets - which colonies count toward a
 * weight, how a dead heat between them is settled - therefore belongs on that pass and not here,
 * however generic the inputs it is computed from look.
 *
 * <p>Opened where a rebuild begins and handed down, so the sector is read at one moment and
 * every system is walked once for the whole rebuild rather than once per surface that asks about
 * it. A pass is built for one rebuild and discarded with it; one kept past that would go on
 * answering off a sector that has since moved on.
 *
 * <p>The sector is the index's rather than a field of its own, so a pass cannot be built naming
 * one sector while answering out of another.
 *
 * <p>A class rather than a record because it remembers as well as carries: the colony walk is
 * memoised inside the index it holds, each colony's kind inside the knowledge it opened, and
 * habitation here beside them. All three are a snapshot of one moment, which is why a pass is
 * discarded with the rebuild that opened it - and why value equality would be wrong for it, two
 * passes over one sector being two separate readings however alike the knobs they were built
 * from. Not safe for concurrent use, a rebuild being one thread's work.
 */
public final class HolderPass {

    // Each system's habitation, resolved on first ask and remembered for the rest of the pass.
    //
    // Memoised because several readers ask for it per system and they run in separate walks of the
    // sector: the filter's holder resolve asks every system for its blocs, the inhabitation scan
    // asks every system for its emptiness, and each picker's stats fold asks every system for the
    // blocs living in it and what they live on. The colony walk beneath is already shared, so what
    // repeated was the projection and the folds over it - cheap each, and paid for the whole sector
    // over again per reader.
    //
    // Remembered on the same terms as the colony memo beneath it, whose answers the fold here
    // reads: the two have to agree about what one system is, and keyed differently they would
    // disagree exactly over a pair sharing an ID - one holding two entries where the other holds
    // one, so the second system draws the first's inhabitants over its cell, on the one layer
    // whose whole output is who lives where.
    private final SystemKeyedMemo<SystemHabitation> habitationBySystem = new SystemKeyedMemo<>();

    private final ColonyKnowledge colonyKnowledge;
    private final HolderGrouping grouping;
    private final SectorPassIndex sectorIndex;

    /**
     * Opens a pass over an already-built reading of the sector.
     *
     * <p>The rule is required on the same terms the grouping and the walk are: a pass is opened
     * where a rebuild begins, from a value the opener already holds, so a null is a fault at that
     * one place rather than a caller with no rule to state. Standing the fog in for it would turn
     * that fault into a map that quietly draws less, which nothing on screen would report.
     *
     * @param grouping         the grouping that folds factions into blocs before any mechanic
     *                         compares them; the identity grouping resolves the plain faction view
     * @param colonyVisibility the rule every read through this pass shows colonies under - the dev
     *                         reveal, and the gates holding back what a bare fog would leak
     * @param sectorIndex      the pass's one reading of the sector, shared by every read made
     *                         through it
     */
    public HolderPass(
            HolderGrouping grouping,
            ColonyVisibility colonyVisibility,
            SectorPassIndex sectorIndex) {

        Objects.requireNonNull(colonyVisibility, "colonyVisibility");

        this.grouping = Objects.requireNonNull(grouping, "grouping");
        this.sectorIndex = Objects.requireNonNull(sectorIndex, "sectorIndex");

        // The rule is paired with the sector's sighting register here, which is the one point at
        // which both are in hand: the index names the sector, and a projection asked of a colony
        // set later would have nowhere to read what has been observed from.
        this.colonyKnowledge = ColonyKnowledge.over(sectorIndex.getSector(), colonyVisibility);
    }

    /**
     * A pass over one sector under an explicit colony rule, opening the colony index its
     * reads share.
     *
     * @param sector           the sector this pass reads; null yields a pass answering an empty
     *                         colony set for every system, matching how the reads treat an
     *                         unreachable sector
     * @param colonyVisibility the rule this pass shows colonies under
     * @param grouping         the grouping this pass folds factions into blocs under
     * @return a pass over that sector carrying those knobs
     */
    public static HolderPass over(
            SectorAPI sector,
            ColonyVisibility colonyVisibility,
            HolderGrouping grouping) {

        return new HolderPass(
            grouping,
            colonyVisibility,
            new SectorPassIndex(sector));
    }

    /**
     * A pass reading the player's live visibility settings under an explicit grouping: the rule
     * is sampled once here so the whole rebuild resolves under the settings in force when it
     * began, even if the player flips a toggle mid-walk.
     *
     * <p>The whole rule is taken rather than the reveal alone, so a surface reading through this
     * pass cannot be handed one gate and not the other.
     *
     * @param sector   the sector this pass reads
     * @param grouping the grouping this pass folds factions into blocs under
     * @return a pass carrying the live rule paired with the grouping
     */
    public static HolderPass readFromLunaSettings(SectorAPI sector, HolderGrouping grouping) {
        return over(
            sector,
            MapVisibilityRules.readFromLunaSettings().colonyVisibility(),
            grouping);
    }

    /**
     * The grouping this pass folds factions into blocs under.
     *
     * @return the grouping every per-bloc read through this pass is made under
     */
    public HolderGrouping grouping() {
        return grouping;
    }

    /**
     * What the player may be told about the colonies this pass walks: its rule, read against the
     * sector's own record of what has been seen and where.
     *
     * <p>Published so a reader needing a projection of its own - one this pass does not name -
     * takes the pass's own knowledge rather than pairing a rule with a register for itself. The
     * pair assembled at a call site is a pair that can be assembled wrongly, and a surface reading
     * one pass's rule against another's observations would withhold colonies nothing else on the
     * map is withholding.
     *
     * @return the knowledge every colony projection through this pass is taken under
     */
    public ColonyKnowledge colonyKnowledge() {
        return colonyKnowledge;
    }

    /**
     * The reading of the sector this pass answers out of - its systems, and the colonies in each.
     *
     * @return the index, so a reader needing to open something of its own over the same walk can
     */
    public SectorPassIndex sectorIndex() {
        return sectorIndex;
    }

    /**
     * Opens a claim reader over this pass - its walk of each system, under its colony rule.
     *
     * <p>Named here so the pair is never assembled at a call site. A reader given the walk but
     * some other rule would score the sector this pass read while reporting a different sector's
     * worth of it as known, and nothing on screen would say which half was wrong.
     *
     * @param claimReaderSource the source to open through
     * @return a reader answering off this pass, to be discarded with it
     */
    public ClaimReader openClaimReaderThrough(ClaimReaderSource claimReaderSource) {
        return claimReaderSource.openReaderOver(colonyKnowledge, sectorIndex);
    }

    /**
     * The sector this pass reads, as the index it walks names it.
     *
     * @return the sector; null when the pass was opened over none
     */
    public SectorAPI sector() {
        return sectorIndex.getSector();
    }

    /**
     * The systems this pass walks, in the sector's own order.
     *
     * <p>Empty for a pass over no sector, which is what lets a resolve state its walk without
     * guarding the unreachable case first: there is nothing to iterate, so the loop body decides
     * nothing and the guard it would have needed cannot be forgotten at one call site and kept at
     * another.
     *
     * @return the sector's star systems; empty when the pass was opened over no sector
     */
    public List<StarSystemAPI> readSystems() {

        var sector = sector();
        return sector == null ? List.of() : sector.getStarSystems();
    }

    /**
     * Whether this pass can read an economy at all - a sector to walk, with its economy up.
     *
     * <p>The one named answer to a state every resolve below meets: the sector is unreachable, or
     * it is mid-load and its economy has not been built yet. A resolve that reads market weights
     * reports nothing at all in that case rather than reporting a sector as empty, and stating the
     * condition once is what stops one of them testing half of it.
     *
     * @return true when both the sector and its economy are there to read
     */
    public boolean canReadEconomy() {

        var sector = sector();
        return sector != null && sector.getEconomy() != null;
    }

    /**
     * The colonies in one system, off this pass's single walk of it - what a reader needing the
     * colonies themselves rather than a mechanic's verdict on them takes, so it shares the walk
     * with every other read instead of adding one.
     *
     * @param system the system to read; null yields an empty set
     * @return the system's colony set
     */
    public Colonies readColoniesIn(StarSystemAPI system) {
        return sectorIndex.readColoniesIn(system);
    }

    /**
     * The colonies in one system the player may be shown - the known projection over this pass's
     * one walk of it, taken under the rule the pass was opened with.
     *
     * <p>Named here rather than composed at each display reader, because the fill painting a cell,
     * the band counting inside it and the box over it all have to withhold the same colonies. Two
     * of them applying the rule separately is two chances for a band to count out a colony the
     * fill declines to draw.
     *
     * @param system the system to read; null yields an empty list
     * @return the system's colonies the rule admits, in the set's own order
     */
    public List<Colony> readKnownColoniesIn(StarSystemAPI system) {
        return colonyKnowledge.readKnownColonies(readColoniesIn(system));
    }

    /**
     * The colonies in one system that amount to people living there - the habitation projection
     * over this pass's one walk of it, under the same rule the known listing takes.
     *
     * <p>Beside the listing rather than in place of it, because a reader answering "who may be
     * named here" and a reader answering "is anybody living here" are asking different questions
     * of the one system: a derelict somebody has seen belongs in the first answer and settles
     * nothing in the second. Offering both off the pass means neither reader has to know which
     * shapes of colony the difference turns on.
     *
     * @param system the system to read; null yields an empty list
     * @return the system's known colonies somebody lives on, in the set's own order
     */
    public List<Colony> readInhabitingColoniesIn(StarSystemAPI system) {
        return colonyKnowledge.readInhabitingColonies(readColoniesIn(system));
    }

    /**
     * The factions the player may be told about in one system - the owners of the known listing
     * above, each named once however many colonies it holds there.
     *
     * <p>Who a box may name is a question about the system rather than about the mechanic reading
     * it, so it is answered here, off the one projection, rather than derived by each mechanic from
     * whatever it happened to score. That is what lets every box over one cell name the same
     * factions: all of them are projections of this, so none can hold an owner another lacks.
     *
     * <p>The listing rather than habitation, which is what {@link #readHabitationIn} answers. A
     * derelict somebody has seen has an owner to name and nobody living on it, so it belongs in
     * this set and in no answer about who lives in the system.
     *
     * @param system the system to read; null yields an empty set
     * @return the IDs of the factions holding a colony the rule admits, in the projection's own
     *         order
     */
    public Set<String> readKnownColonyFactionIds(StarSystemAPI system) {
        return collectFactionIdsOf(readKnownColoniesIn(system));
    }

    /**
     * What one system's habitation amounts to, off this pass's one walk of it - the value every
     * surface answering about habitation shares, and {@link SystemHabitation} says why it is one
     * value rather than a read apiece.
     *
     * <p>Habitation rather than the wider listing {@link #readKnownColonyFactionIds} answers, and
     * the difference is the derelict: a hulk somebody has seen is named in a box and settles
     * nothing, so no bloc is living in a system holding one alone and none is spared the recede
     * there.
     *
     * <p>Worked out on the first ask and remembered for the rest of the pass, since its readers
     * ask it in separate walks of the sector.
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
