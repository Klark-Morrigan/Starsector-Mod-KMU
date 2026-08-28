package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates the whole-sector {@link DominanceStats} the filter picker sorts and labels its options by,
 * from one grouped per-system dominance pass.
 *
 * <p>The picker half of the holder pipeline, kept apart from {@link SectorPolitics}'s per-system
 * holder resolution: where that produces one render holder per system, this walks the same sector to
 * total each bloc's four picker metrics - dominations, presences, summed dominance weight, and
 * summed raw colony size.
 *
 * <p>The two numbers about winning come off the weighed footprints, and the two about being there
 * come off the pass's habitation - which is the same reading of a system the cell classification and
 * the band both take, so a bloc the player can see living somewhere is offered to be spotlighted
 * there. Reading presence off the weights instead would drop every bloc whose colonies the economy
 * does not list, leaving the picker denying what the map beneath it is drawing.
 */
public final class DominanceStatsAggregator {

    private DominanceStatsAggregator() {
    }

    /**
     * The whole-sector stats for every bloc living somewhere the player can see, under a pass - the
     * filter picker's selectable set (a bloc present here has presence of at least one, which is the
     * {@code presence > 0} gate) paired with the four numbers the picker sorts and displays them by.
     *
     * <p>One walk yields all four metrics so the picker never re-reads the sector per number: each
     * system's weighed footprints settle its one dominant bloc, and every bloc living there then
     * takes a present-system entry carrying its weight and its colony size - the dominant one also a
     * domination count. The order follows the sector walk, which each view then maps into its own
     * picker options.
     *
     * @param pass the sector walk, weighting rule, colony rule, and grouping this read resolves
     *             under, sampled once by the caller so the whole read resolves under one set of
     *             knobs; a pass over no sector (or one whose sector has no economy) yields an
     *             empty map
     * @return each present bloc's stats, keyed by bloc id in walk order; empty when nobody lives
     *         anywhere the player can see
     */
    public static Map<String, DominanceStats> aggregateDominanceStats(DominancePass pass) {
        var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

        if (!pass.canReadEconomy()) {
            return statsByBlocId;
        }
        for (var system : pass.readSystems()) {
            accumulateSystemStats(statsByBlocId, system, pass);
        }
        return statsByBlocId;
    }

    // Folds one system into the running per-bloc stats: resolves the one dominant bloc from the
    // weighed footprints, then adds a present-system entry to every bloc living here - the dominant
    // one also taking a domination count. A bloc living in several systems accumulates rather than
    // overwrites, and under an alliance grouping the members fold into the alliance's one bloc.
    private static void accumulateSystemStats(
            Map<String, DominanceStats> statsByBlocId,
            StarSystemAPI system,
            DominancePass pass) {

        // The weighed read, and the only thing the winner is settled from. Ties resolve by market
        // proximity, the same as the render pass, so a picker's domination count matches the
        // territory that actually paints.
        var footprintByBlocId = pass.readBlocFootprints(system);

        // The one winner among the blocs the contest weighed; null when it weighed nothing here.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            pass.tieBreakFor(system));

        // Presence is habitation rather than the weights, so a bloc living here on a colony the
        // economy does not list is listed at the size it lives on and a score of nought, as the
        // band under the same cell has been drawing its run for it all along. Folded after the
        // winner is settled and never into the map above: a nought-weight footprint entering the
        // ranking would take a system nobody dominates, the first entry being the leader before
        // anything is compared.
        for (var entry : pass.readHabitationIn(system).colonySizeByBlocId().entrySet()) {
            var blocId = entry.getKey();
            var footprint = footprintByBlocId.getOrDefault(blocId, MarketFootprint.EMPTY);

            statsByBlocId.put(
                blocId,
                statsByBlocId.getOrDefault(blocId, DominanceStats.EMPTY)
                    .addSystem(
                        blocId.equals(dominantBlocId),
                        footprint.totalWeight(),
                        entry.getValue()));
        }
    }
}
