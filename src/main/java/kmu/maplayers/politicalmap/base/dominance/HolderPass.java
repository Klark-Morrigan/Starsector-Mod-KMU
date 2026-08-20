package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.Colony;
import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.base.visibility.MapVisibilityRules;

import java.util.LinkedHashSet;
import java.util.List;
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
 * @param grouping         the grouping that folds factions into blocs before any mechanic
 *                         compares them; the identity grouping resolves the plain faction view
 * @param colonyVisibility the rule every read through this pass shows colonies under - the dev
 *                         reveal, and the gates holding back what a bare fog would leak
 * @param colonies         the pass's one walk of each system, shared by every read made through
 *                         it
 */
public record HolderPass(
    HolderGrouping grouping,
    ColonyVisibility colonyVisibility,
    SystemColoniesIndex colonies) {

    // The rule is required on the same terms the grouping and the walk are: a pass is opened
    // where a rebuild begins, from a value the opener already holds, so a null is a fault at that
    // one place rather than a caller with no rule to state. Standing the fog in for it would turn
    // that fault into a map that quietly draws less, which nothing on screen would report.
    public HolderPass {
        Objects.requireNonNull(grouping, "grouping");
        Objects.requireNonNull(colonyVisibility, "colonyVisibility");
        Objects.requireNonNull(colonies, "colonies");
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
            new SystemColoniesIndex(sector));
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
     * The sector this pass reads, as the index it walks names it.
     *
     * @return the sector; null when the pass was opened over none
     */
    public SectorAPI sector() {
        return colonies.getSector();
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
        return colonies.readColoniesIn(system);
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
        return readColoniesIn(system).readKnownColonies(colonyVisibility);
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
        return readColoniesIn(system).readInhabitingColonies(colonyVisibility);
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
     * @return the ids of the factions holding a colony the rule admits, in the projection's own
     *         order
     */
    public Set<String> readKnownColonyFactionIds(StarSystemAPI system) {
        return collectFactionIdsOf(readKnownColoniesIn(system));
    }

    /**
     * What one system's habitation amounts to - the colonies somebody lives on, and the blocs
     * folded from those very colonies - as the one value the surfaces answering about habitation
     * share.
     *
     * <p>Offered as a value rather than as two reads because the cell classification asks its
     * emptiness while the filter's spotlight asks its bloc set, and a cell painted as empty space
     * under a fill the spotlight kept over it is the one pairing those two must not be able to
     * make. Folding the blocs here, from the same colonies the emptiness is asked of, is what makes
     * presence a partition of that set rather than a second read two call sites happen to have
     * chosen alike.
     *
     * <p>Habitation rather than the wider listing, and the difference is the derelict: a hulk
     * somebody has seen is named in a box and settles nothing, so no bloc is living in a system
     * holding one alone and none is spared the recede there.
     *
     * @param system the system to read; null yields an empty habitation
     * @return the system's habitation under this pass's rule and grouping
     */
    public SystemHabitation readHabitationIn(StarSystemAPI system) {

        var inhabitingColonies = readInhabitingColoniesIn(system);

        return new SystemHabitation(
            inhabitingColonies,
            grouping.collectBlocIds(collectFactionIdsOf(inhabitingColonies)));
    }

    // The owners of one projection's colonies, each named once however many it holds there. Shared
    // by the two folds above so a listing's owners and a habitation's blocs are gathered by one
    // rule - which colonies were handed in is the only thing that separates them.
    private static Set<String> collectFactionIdsOf(List<Colony> colonies) {

        var factionIds = new LinkedHashSet<String>();
        for (var colony : colonies) {
            factionIds.add(colony.market().getFaction().getId());
        }
        return factionIds;
    }
}
