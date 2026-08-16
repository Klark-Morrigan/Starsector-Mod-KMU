package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemColonies;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;

import java.util.Comparator;
import java.util.Objects;

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
 * paint by that mechanic.
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
 * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count (the "show all
 *                                         factions" dev reveal); false applies the normal
 *                                         known-to-player filter
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
            PoliticalMapDevToggles.readFromLunaSettings().isShowingAllFactions(),
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
     * The colonies in one system, off this pass's single walk of it - what a reader needing the
     * colonies themselves rather than a mechanic's verdict on them takes, so it shares the walk
     * with every other read instead of adding one.
     *
     * @param system the system to read; null yields an empty set
     * @return the system's colony set
     */
    public SystemColonies readColoniesIn(StarSystemAPI system) {
        return colonies.readColoniesIn(system);
    }

    /**
     * The market-proximity tie-break for one system, consulted only when blocs tie on every
     * level of whatever mechanic ranked them, so a dead heat falls to whoever holds the colony
     * nearest the system centre. Lazy - it reads no geometry unless a tie forces it - so every
     * pass shares one on-demand tie-break rather than each resolver building its own.
     *
     * @param system the system the tie-break ranks blocs within
     * @return the comparator that orders tied bloc ids for this system
     */
    public Comparator<String> tieBreakFor(StarSystemAPI system) {
        return MarketProximityTieBreak.forSystem(
            system,
            readColoniesIn(system),
            shouldIncludeUndiscoveredMarkets,
            grouping);
    }
}
