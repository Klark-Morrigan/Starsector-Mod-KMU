package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates the whole-sector totals the filter picker sorts and labels its options by, and the
 * systems behind them, from one grouped per-system dominance pass.
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

    // One walk's worth of running state, held as fields rather than threaded through each step.
    // The four metrics and the system index accumulate over every system in the sector, so passing
    // them along made each step's parameter list longer than the step itself and put the pass - the
    // one thing every step reads - last among them.
    private final BlocPresenceIndexBuilder inhabitedSystems = new BlocPresenceIndexBuilder();
    private final DominancePass pass;
    private final Map<String, DominanceStats> statsByBlocId = new LinkedHashMap<>();

    // Single-use and private, so the entry point below stays the only way in: a caller can neither
    // hold a half-filled aggregation nor run a second sector through one that is already full.
    private DominanceStatsAggregator(DominancePass pass) {
        this.pass = pass;
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
     *             empty read
     * @return each present bloc's stats and the systems it lives in, keyed by bloc id in walk
     *         order; empty when nobody lives anywhere the player can see
     */
    public static DominanceStatsRead aggregateDominanceStats(DominancePass pass) {
        if (!pass.canReadEconomy()) {
            return DominanceStatsRead.EMPTY;
        }
        return new DominanceStatsAggregator(pass).aggregateWholeSector();
    }

    // Walks every system the pass offers, folding each into the running state, and seals the result.
    private DominanceStatsRead aggregateWholeSector() {
        for (var system : pass.readSystems()) {
            accumulateSystemStats(system);
        }
        return new DominanceStatsRead(statsByBlocId, inhabitedSystems.buildIndex());
    }

    // Folds one system into the running per-bloc stats and presence sets: resolves the one dominant
    // bloc from the weighed footprints, then adds a present-system entry to every bloc living here -
    // the dominant one also taking a domination count. A bloc living in several systems accumulates
    // rather than overwrites, and under an alliance grouping the members fold into the alliance's
    // one bloc.
    private void accumulateSystemStats(StarSystemAPI system) {

        // The weighed read, and the only thing the winner is settled from. Ties resolve by market
        // proximity, the same as the render pass, so a picker's domination count matches the
        // territory that actually paints.
        var footprintByBlocId = pass.readBlocFootprints(system);

        // The one winner among the blocs the contest weighed and admits as candidates; null when it
        // weighed nothing here.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            pass.resolveRankingRulesFor(system));

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

            inhabitedSystems.recordPresence(blocId, SystemKey.readKeyOf(system));
        }
    }
}
