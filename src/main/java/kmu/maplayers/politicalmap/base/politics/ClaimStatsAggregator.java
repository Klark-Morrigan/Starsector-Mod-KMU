package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates the whole-sector {@link ClaimStats} the claims picker sorts and labels its options by,
 * from one walk of the sector's star systems.
 *
 * <p>The claims counterpart to {@link DominanceStatsAggregator}, and deliberately a separate walk
 * producing a separate output type: the two aggregators share the per-system market <em>read</em>
 * ({@link DominancePass#readBlocContributions}) and nothing else, so neither stats record can grow
 * a field for the other's benefit.
 *
 * <p>The two metrics come from two different sources on the same pass over the systems. Claims come
 * from the {@link ClaimReader} port - not the {@code Misc} static behind it - so the aggregation
 * runs against a known set of claimants with no running game, exactly as {@link SectorClaims}'s
 * resolve does. Market sizes come from the economy, and are summed over every system a bloc holds a
 * colony in rather than over the systems it claims, since a claimant usually does not hold what it
 * claims (see {@link ClaimStats}).
 *
 * <p>Both are folded to blocs through the pass's grouping, so an alliance grouping totals its
 * members' claims and colonies into the alliance's one entry - the same fold the dominance
 * aggregation applies.
 */
public final class ClaimStatsAggregator {

    private ClaimStatsAggregator() {
    }

    /**
     * The whole-sector claim stats for every bloc that claims a system or holds a colony, under a
     * pass.
     *
     * <p>No gate is applied here: a bloc that claims nothing is still returned with its market size,
     * because deciding what belongs in a picker is the view's call rather than the fold's. What is
     * <em>not</em> returned is a bloc the walk never surfaced - one that neither claims nor holds
     * anything - so a caller listing every entry lists everyone who paints or holds something rather
     * than every faction in the sector.
     *
     * @param sector      the sector whose systems are walked and whose economy is read; null yields
     *                    an empty map
     * @param pass        the rule, dev reveal, and grouping this read resolves under, sampled once by
     *                    the caller so the whole read resolves under one set of knobs
     * @param claimReader the claim source, read once per system
     * @return each claiming or colony-holding bloc's stats, keyed by bloc id in star-system walk
     *         order; empty when the sector holds neither
     */
    public static Map<String, ClaimStats> aggregateClaimStats(
            SectorAPI sector,
            DominancePass pass,
            ClaimReader claimReader) {

        var statsByBlocId = new LinkedHashMap<String, ClaimStats>();
        if (sector == null) {
            return statsByBlocId;
        }

        // Claims are read from the port and so stand on their own, but market sizes need an economy
        // this sector may not have yet (mid-load). Sampled once rather than per system: a walk cannot
        // gain an economy halfway through, and the primary metric is still worth counting without one.
        var hasEconomy = sector.getEconomy() != null;

        for (var system : sector.getStarSystems()) {
            accumulateSystemClaim(statsByBlocId, system, pass, claimReader);
            if (hasEconomy) {
                accumulateSystemMarketSizes(statsByBlocId, sector, system, pass);
            }
        }
        return statsByBlocId;
    }

    // Folds one system's claimant into the running per-bloc stats. A system has exactly one claimant,
    // so this adds at most one claim; an unclaimed system contributes nothing. The claimant faction is
    // folded to its bloc first, so an alliance grouping counts its members' claims as the alliance's.
    private static void accumulateSystemClaim(
            Map<String, ClaimStats> statsByBlocId,
            StarSystemAPI system,
            DominancePass pass,
            ClaimReader claimReader) {

        var claimantId = claimReader.readClaimingFactionId(system);
        if (claimantId == null) {
            return;
        }
        var blocId = pass.grouping().resolveBlocId(claimantId);
        statsByBlocId.put(
            blocId,
            statsByBlocId.getOrDefault(blocId, ClaimStats.EMPTY).addClaim());
    }

    // Folds one system's colonies into the running per-bloc stats: the same grouped contribution
    // read the dominance aggregation walks, with only the raw market-size half kept - the dominance
    // weight the read also carries means nothing to a layer painted by the claim mechanic.
    private static void accumulateSystemMarketSizes(
            Map<String, ClaimStats> statsByBlocId,
            SectorAPI sector,
            StarSystemAPI system,
            DominancePass pass) {

        for (var entry : pass.readBlocContributions(sector, system).entrySet()) {
            var blocId = entry.getKey();
            statsByBlocId.put(
                blocId,
                statsByBlocId.getOrDefault(blocId, ClaimStats.EMPTY)
                    .addMarketSize(entry.getValue().marketSize()));
        }
    }
}
