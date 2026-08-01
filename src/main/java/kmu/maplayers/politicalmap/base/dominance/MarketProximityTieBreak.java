package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.Markets;
import kmlib.starsector.systems.StarSystems;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Breaks an exact dominance tie by which bloc holds the market nearest the system's centre,
 * so a tie resolves the same on every view instead of by the arbitrary ordering of bloc ids.
 *
 * <p>A tie once fell to the lowest id, which is a faction id on the faction view but an
 * alliance's own id on the alliances view - so the same two tied markets could flip holder
 * between views purely because an alliance id sorts differently than the member faction's. A
 * physical measure - proximity to the system centre - is the same on both views, so a system
 * a tie decides paints the same holder however factions are grouped.
 *
 * <p>Distance is measured from the star nearest the system centre (the star itself in a
 * single-star system) along each market's orbit, summing the circular-orbit radii up the
 * body's orbit-focus chain to that star. Reading the orbit rather than the body's live
 * position makes the result identical every frame: a planet is as far out as its orbit,
 * wherever it currently sits on it. A bloc's distance is its nearest market's; the closer
 * bloc wins the tie, and two blocs whose nearest markets orbit at the same depth fall back to
 * the lowest colour-faction id - which is grouping-invariant, so even that rare case stays
 * consistent across views.
 *
 * <p>The comparator is lazy: it reads no geometry until first asked to compare, which
 * {@link SystemDominance} does only when two blocs tie on all three weight levels. A system
 * with a clear winner - nearly every system - pays nothing. Starsector economy and orbit
 * types stay in this adapter, so {@link SystemDominance} remains a pure rule over footprints.
 */
public final class MarketProximityTieBreak {

    // The distance a bloc with no counted market takes - it never wins a proximity tie, leaving
    // the colour-faction backstop to settle the pair. Matches the infinity StarSystems hands
    // back for a market whose primary entity cannot be placed.
    private static final double UNPLACEABLE_DISTANCE = Double.POSITIVE_INFINITY;

    private MarketProximityTieBreak() {
    }

    /**
     * A lazy tie-break comparator over bloc ids for one system, ordering the bloc whose
     * nearest market orbits closest to the system's central star first, and falling back to
     * the lowest colour-faction id when two nearest markets orbit at the same depth. Reads no
     * geometry until first compared, so a system that never ties costs nothing.
     *
     * @param sector                       the sector whose economy and orbits are read
     * @param system                       the system the tie is decided within
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count, matching the
     *                                     dominance pass's market filter so the tie weighs the
     *                                     same markets it did
     * @param grouping                     the active grouping, so a market's bloc and the
     *                                     colour-faction backstop resolve as the dominance pass
     *                                     grouped them
     * @return a comparator ordering the closer-to-centre bloc first
     */
    public static Comparator<String> forSystem(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets,
            HolderGrouping grouping) {

        return new Comparator<>() {
            // Built on the first compare - i.e. the first tie in this system - then reused for
            // any further ties, so the geometry is read once and only when a tie needs it.
            private Map<String, Double> minDistanceByBlocId;

            @Override
            public int compare(String leftBlocId, String rightBlocId) {
                if (minDistanceByBlocId == null) {
                    minDistanceByBlocId = computeMinDistanceByBlocId(
                        sector,
                        system,
                        shouldIncludeUndiscoveredMarkets,
                        grouping);
                }
                // Primitive doubles so the comparison below is by value; a boxed Double would
                // compare by reference and never reach the colour-faction backstop on a tie.
                double leftDistance =
                    minDistanceByBlocId.getOrDefault(leftBlocId, UNPLACEABLE_DISTANCE);
                double rightDistance =
                    minDistanceByBlocId.getOrDefault(rightBlocId, UNPLACEABLE_DISTANCE);

                if (leftDistance != rightDistance) {
                    return Double.compare(leftDistance, rightDistance);
                }
                return grouping
                    .resolveColourFactionId(leftBlocId)
                    .compareTo(grouping.resolveColourFactionId(rightBlocId));
            }
        };
    }

    // Each present bloc's nearest-market distance from the system's central star, built once
    // when the tie-break is first consulted. Walks the same counted colonies the dominance pass
    // did, folds each into its bloc, and keeps the smallest orbit-chain distance to the
    // centremost star, so the comparator reads a ready lookup. The star search and the orbit-chain
    // distance are StarSystems' job; this only maps the result onto blocs.
    private static Map<String, Double> computeMinDistanceByBlocId(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets,
            HolderGrouping grouping) {

        var centremostStar = StarSystems.getCentremostStar(system);
        var minDistanceByBlocId = new LinkedHashMap<String, Double>();

        for (var market : sector.getEconomy().getMarkets(system)) {
            if (!Markets.isCountedAsColony(market, shouldIncludeUndiscoveredMarkets)) {
                continue;
            }
            var blocId = grouping.resolveBlocId(market.getFaction().getId());
            var distance =
                StarSystems.getOrbitalDistanceTo(market.getPrimaryEntity(), centremostStar);
                
            minDistanceByBlocId.merge(blocId, distance, Math::min);
        }
        return minDistanceByBlocId;
    }
}
