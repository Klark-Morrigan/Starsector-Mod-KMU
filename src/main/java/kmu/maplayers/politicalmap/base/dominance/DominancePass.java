package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * The three settings-derived inputs that parameterise one dominance pass over the economy,
 * carried as one unit so a pass reads the same rule, reveal, and grouping for every system it
 * walks.
 *
 * <p>The dominance pipeline resolves ownership under three knobs that always travel together:
 * the weighting rule that scores each market, the dev reveal that decides whether undiscovered
 * colonies count, and the grouping that folds factions into blocs. Threading them as three loose
 * parameters bred a ladder of overloads and a seven-argument inner resolve; bundling them here
 * lets a caller read the player's settings once ({@link #readFromLunaSettings}) and hand the whole
 * pass down, so no system in the walk can drift onto a different rule mid-pass.
 *
 * <p>Beyond holding the knobs, the pass performs the two per-system reads they drive - the market
 * footprints regrouped under its grouping ({@link #readBlocFootprints}) and the tie-break that
 * settles a dead heat ({@link #tieBreakFor}) - so the ownership and stats resolvers share one
 * definition of each rather than re-deriving it from the loose knobs at every call site.
 *
 * @param rules                            the weighting rule scoring each market's dominance worth
 * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward dominance (the
 *                                         "show all factions" dev reveal); false applies the normal
 *                                         known-to-player filter
 * @param grouping                         the grouping that folds factions into blocs before
 *                                         dominance is compared; the identity grouping resolves the
 *                                         plain faction view
 */
public record DominancePass(
        DominanceRules rules,
        boolean shouldIncludeUndiscoveredMarkets,
        OwnershipGrouping grouping) {

    public DominancePass {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(grouping, "grouping");
    }

    /**
     * A pass reading the player's live LunaLib settings under an explicit grouping: the weighting
     * rule and the show-all-factions reveal are sampled once here so the whole pass resolves under
     * the settings in force when it began, even if the player changes a toggle mid-walk.
     *
     * @param grouping the grouping this pass folds factions into blocs under
     * @return a pass carrying the live rule and reveal paired with the grouping
     */
    public static DominancePass readFromLunaSettings(OwnershipGrouping grouping) {
        return new DominancePass(
                DominanceRules.readFromLunaSettings(),
                PoliticalMapDevToggles.readFromLunaSettings().isShowingAllFactions(),
                grouping);
    }

    /**
     * A pass reading the player's live settings under the faction (identity) grouping - every
     * faction its own bloc - for the plain faction view.
     *
     * @return a pass carrying the live rule and reveal under the identity grouping
     */
    public static DominancePass readFromLunaSettings() {
        return readFromLunaSettings(OwnershipGrouping.identity());
    }

    /**
     * This system's per-faction footprints under the pass's rule and reveal, before any grouping:
     * each faction's known markets folded into its own footprint. The unowned read the per-bloc
     * read below builds on, and the one a two-tier standings breakdown needs whole so it can rank a
     * bloc's members individually - both taking the read from the pass rather than re-deriving it
     * from the loose knobs.
     *
     * @param sector the sector whose economy is read; assumed non-null with a non-null economy,
     *               which the callers guard before delegating
     * @param system the system whose markets are folded
     * @return each present faction's footprint in the system; empty when the system holds no known
     *         owned market
     */
    public Map<String, MarketFootprint> readFootprintsByFaction(
            SectorAPI sector, StarSystemAPI system) {
        return KnownMarketFootprints.readByFaction(
                sector, system, rules, shouldIncludeUndiscoveredMarkets);
    }

    /**
     * This system's per-bloc footprints under the pass's rule, reveal, and grouping: the per-faction
     * footprints regrouped into per-bloc footprints (a no-op fold under identity, a member-summing
     * merge under an alliance grouping). The one read shared by the ownership resolve and the
     * filter's presence resolve, so both rank the same footprints.
     *
     * @param sector the sector whose economy is read; assumed non-null with a non-null economy,
     *               which the callers guard before delegating
     * @param system the system whose markets are folded
     * @return each present bloc's footprint in the system; empty when no bloc holds a folded market
     */
    public Map<String, MarketFootprint> readBlocFootprints(SectorAPI sector, StarSystemAPI system) {
        return grouping.regroupByBloc(
                readFootprintsByFaction(sector, system),
                MarketFootprint.EMPTY,
                MarketFootprint::merge);
    }

    /**
     * The market-proximity tie-break for this system under the pass's reveal and grouping,
     * consulted only when blocs tie on every weight level so it resolves a dead heat by who holds
     * the market nearest the system centre. Lazy - it reads no geometry unless a tie forces it - so
     * every pass shares one on-demand tie-break rather than each resolver building its own.
     *
     * @param sector the sector whose economy and geometry the tie-break reads
     * @param system the system the tie-break ranks blocs within
     * @return the comparator that orders tied bloc ids for this system
     */
    public Comparator<String> tieBreakFor(SectorAPI sector, StarSystemAPI system) {
        return MarketProximityTieBreak.forSystem(
                sector, system, shouldIncludeUndiscoveredMarkets, grouping);
    }
}
