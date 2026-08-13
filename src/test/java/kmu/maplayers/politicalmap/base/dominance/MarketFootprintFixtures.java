package kmu.maplayers.politicalmap.base.dominance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared hand-built footprints for the suites that pin a rule reading them: the dominance
 * comparison, the grouped ranking, and the filter's presence classification. One home for these
 * two builders so the suites state a system's contest the same way rather than each carrying its
 * own near-identical copy - and so a footprint gaining a component is one edit rather than three.
 *
 * <p>Every rule they feed compares weights alone, which is what shapes both builders: a footprint
 * is stated as the three weights and nothing else, and the map preserves insertion order so a
 * suite can list the winner second and still show the ordering came from the rule rather than
 * from the walk.
 */
public final class MarketFootprintFixtures {

    // How many markets each hand-built footprint stands for. Fixed, because no rule these
    // fixtures feed reads the count: one that varied between them would read as though it did.
    private static final int SINGLE_MARKET = 1;

    private MarketFootprintFixtures() {
    }

    /**
     * A footprint stating only the three weights the rules compare, standing for one held market,
     * so each fixture reads as the levels a rule walks and nothing else.
     *
     * @param totalWeight         the holder's combined weight, the top level of every rule
     * @param largestMarketWeight the holder's heaviest single market
     * @param planetWeight        how much of the combined weight sits on planets rather than
     *                            stations
     * @return the footprint those weights describe
     */
    public static MarketFootprint buildWeightedFootprint(
            int totalWeight,
            int largestMarketWeight,
            int planetWeight) {

        return new MarketFootprint(
            SINGLE_MARKET,
            totalWeight,
            largestMarketWeight,
            planetWeight);
    }

    /**
     * Builds the footprint map preserving insertion order, so a test can list the higher-scoring or
     * higher-id entry first and still expect the rule to order it correctly.
     *
     * @param idsAndFootprints alternating holder id and {@link MarketFootprint}, in the order the
     *                         rule should be handed them
     * @return those pairs as a map that walks in the order given
     */
    public static Map<String, MarketFootprint> listOrderedFootprints(Object... idsAndFootprints) {

        var footprints = new LinkedHashMap<String, MarketFootprint>();

        for (var i = 0; i < idsAndFootprints.length; i += 2) {
            footprints.put(
                (String) idsAndFootprints[i],
                (MarketFootprint) idsAndFootprints[i + 1]);
        }
        return footprints;
    }
}
