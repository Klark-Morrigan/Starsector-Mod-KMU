package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.FactionMarketContribution;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates the whole-sector {@link BlocStats} the filter picker sorts and labels its options by,
 * from one grouped per-system dominance pass.
 *
 * <p>The picker half of the holder pipeline, kept apart from {@link SectorPolitics}'s per-system
 * holder resolution: where that produces one render holder per system, this walks the same economy to
 * total each bloc's four picker metrics - dominations, presences, summed dominance weight, and
 * summed raw colony size. Both read the same footprints, dominance inputs, and grouping through
 * {@link DominancePass}, so the picker's "which blocs are selectable" gate stays honest against the
 * territory the render pass actually paints rather than drifting to a second definition of presence.
 */
public final class BlocStatsAggregator {

    private BlocStatsAggregator() {
    }

    /**
     * The whole-sector stats for every bloc holding a visible market somewhere, under a pass - the
     * filter picker's selectable set (a bloc present here has presence of at least one, which is the
     * {@code presence > 0} gate) paired with the four numbers the picker sorts and displays them by.
     *
     * <p>One walk yields all four metrics so the picker never re-reads the economy per number: each
     * system's per-faction contributions are regrouped into per-bloc footprints and market sizes, the
     * one dominant bloc is resolved, and every present bloc takes a present-system entry (the dominant
     * one also a domination count). The order follows the economy walk, which each view then maps into
     * its own picker options.
     *
     * @param sector the sector whose economy is read; null (or a null economy) yields an empty map
     * @param pass   the rule, dev reveal, and grouping this read resolves under, sampled once by the
     *               caller so the whole read resolves under one set of knobs
     * @return each present bloc's stats, keyed by bloc id in economy-walk order; empty when no bloc
     *         holds a visible market
     */
    public static Map<String, BlocStats> aggregateBlocStats(SectorAPI sector, DominancePass pass) {
        var statsByBlocId = new LinkedHashMap<String, BlocStats>();
        if (sector == null || sector.getEconomy() == null) {
            return statsByBlocId;
        }
        for (var system : sector.getStarSystems()) {
            accumulateSystemStats(statsByBlocId, sector, system, pass);
        }
        return statsByBlocId;
    }

    // Folds one system into the running per-bloc stats: regroups the system's per-faction
    // contributions into per-bloc footprints and raw market sizes, resolves the one dominant bloc,
    // then adds a present-system entry to every bloc holding a market here - the dominant one also
    // taking a domination count. A bloc holding markets in several systems accumulates rather than
    // overwrites, and under an alliance grouping the members fold into the alliance's one bloc.
    private static void accumulateSystemStats(
            Map<String, BlocStats> statsByBlocId,
            SectorAPI sector,
            StarSystemAPI system,
            DominancePass pass) {

        // One regroup folds the footprint and the raw market size together (a bloc holding markets in
        // several systems, or an alliance's members, accumulates rather than overwrites), then the
        // dominance rule reads the footprint half of each bloc's folded contribution.
        var contributionByBlocId = pass.grouping().regroupByBloc(
                KnownMarketFootprints.readContributionsByFaction(
                        sector, system, pass.rules(), pass.shouldIncludeUndiscoveredMarkets()),
                FactionMarketContribution.EMPTY,
                FactionMarketContribution::merge);

        // The one winner among the system's present blocs; null only when no bloc is present here,
        // in which case the loop below has nothing to fold and the system contributes no stats.
        // Ties resolve by market proximity, the same as the render pass, so a picker's domination
        // count matches the territory that actually paints.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
                extractFootprints(contributionByBlocId),
                pass.tieBreakFor(sector, system));
        for (var entry : contributionByBlocId.entrySet()) {
            var blocId = entry.getKey();
            var stats = statsByBlocId.getOrDefault(blocId, BlocStats.EMPTY);
            statsByBlocId.put(
                    blocId,
                    stats.addSystem(
                            blocId.equals(dominantBlocId),
                            entry.getValue().footprint().totalWeight(),
                            entry.getValue().marketSize()));
        }
    }

    // The footprint half of each bloc's folded contribution, so the dominance rule - which ranks
    // footprints alone - reads them without the raw market size the stats pass also carries.
    private static Map<String, MarketFootprint> extractFootprints(
            Map<String, FactionMarketContribution> contributionByBlocId) {
        var footprintByBlocId = new LinkedHashMap<String, MarketFootprint>();
        for (var entry : contributionByBlocId.entrySet()) {
            footprintByBlocId.put(entry.getKey(), entry.getValue().footprint());
        }
        return footprintByBlocId;
    }
}
