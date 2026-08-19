package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.Colony;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.base.visibility.MapVisibilityOverrides;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One rebuild's reading of a sector, as any owner-painted map layer needs it: which sector, how
 * factions fold into blocs, how far the fog is lifted, and the one walk of each system all three
 * are answered from.
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
 * @param grouping                         the grouping that folds factions into blocs before any
 *                                         mechanic compares them; the identity grouping resolves
 *                                         the plain faction view
 * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count (the "show
 *                                         undiscovered markets" dev reveal); false applies the
 *                                         normal known-to-player filter
 * @param colonies                         the pass's one walk of each system, shared by every
 *                                         read made through it
 */
public record HolderPass(
    HolderGrouping grouping,
    boolean shouldIncludeUndiscoveredMarkets,
    SystemColoniesIndex colonies) {

    public HolderPass {
        Objects.requireNonNull(grouping, "grouping");
        Objects.requireNonNull(colonies, "colonies");
    }

    /**
     * A pass over one sector under explicit knobs, opening the colony index its reads share.
     *
     * @param sector                           the sector this pass reads; null yields a pass
     *                                         answering an empty colony set for every system,
     *                                         matching how the reads treat an unreachable sector
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count
     * @param grouping                         the grouping this pass folds factions into blocs
     *                                         under
     * @return a pass over that sector carrying those knobs
     */
    public static HolderPass over(
            SectorAPI sector,
            boolean shouldIncludeUndiscoveredMarkets,
            HolderGrouping grouping) {

        return new HolderPass(
            grouping,
            shouldIncludeUndiscoveredMarkets,
            new SystemColoniesIndex(sector));
    }

    /**
     * A pass reading the player's live dev reveal under an explicit grouping: the reveal is
     * sampled once here so the whole rebuild resolves under the setting in force when it began,
     * even if the player flips the toggle mid-walk.
     *
     * @param sector   the sector this pass reads
     * @param grouping the grouping this pass folds factions into blocs under
     * @return a pass carrying the live reveal paired with the grouping
     */
    public static HolderPass readFromLunaSettings(SectorAPI sector, HolderGrouping grouping) {
        return over(
            sector,
            MapVisibilityOverrides.readFromLunaSettings().shouldIncludeUndiscoveredMarkets(),
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
     * one walk of it, taken under the reveal the pass was opened with.
     *
     * <p>Named here rather than composed at each display reader, because the fill painting a cell,
     * the band counting inside it and the box over it all have to withhold the same colonies. Two
     * of them applying the fog separately is two chances for a band to count out a colony the fill
     * declines to draw.
     *
     * @param system the system to read; null yields an empty list
     * @return the system's colonies the reveal admits, in the set's own order
     */
    public List<Colony> readKnownColoniesIn(StarSystemAPI system) {
        return readColoniesIn(system).readKnownColonies(shouldIncludeUndiscoveredMarkets);
    }

    /**
     * The factions present in one system - the owners of the colonies above, each named once
     * however many it holds there.
     *
     * <p>Who is in a system is a question about the system rather than about the mechanic reading
     * it, so it is answered here, off the one projection, rather than derived by each mechanic from
     * whatever it happened to score. That is what lets a band counting a cell and a box listing it
     * report one set of factions: both are projections of this, so neither can hold an owner the
     * other lacks.
     *
     * @param system the system to read; null yields an empty set
     * @return the ids of the factions holding a colony the reveal admits, in the projection's own
     *         order
     */
    public Set<String> readKnownColonyFactionIds(StarSystemAPI system) {

        var factionIds = new LinkedHashSet<String>();
        for (var colony : readKnownColoniesIn(system)) {
            factionIds.add(colony.market().getFaction().getId());
        }
        return factionIds;
    }

    /**
     * The blocs present in one system - the owners above folded under this pass's grouping, so two
     * allies in one system arrive as the one bloc they paint as.
     *
     * <p>What a bloc-keyed surface asks when it needs presence rather than a verdict, and it is
     * read here rather than off whatever the mechanic scored. A mechanic's own numbers are a
     * narrower set than presence - a dominance weight is economy-fed throughout, so a bloc holding
     * nothing the economy lists raises no footprint - and a surface reading presence off them calls
     * such a bloc absent while the band inside the same cell counts its colonies.
     *
     * @param system the system to read; null yields an empty set
     * @return the ids of the blocs holding a colony the reveal admits, in the projection's own
     *         order
     */
    public Set<String> readKnownColonyBlocIds(StarSystemAPI system) {

        var blocIds = new LinkedHashSet<String>();
        for (var factionId : readKnownColonyFactionIds(system)) {
            var blocId = grouping.resolveBlocId(factionId);

            // Left out on the same rule every per-bloc fold on the map applies: a nameless key
            // would travel on as a bloc, and a surface asked to draw or name one has nothing to
            // draw or name it by.
            if (blocId != null) {
                blocIds.add(blocId);
            }
        }
        return blocIds;
    }
}
