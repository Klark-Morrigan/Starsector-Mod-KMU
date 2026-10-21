package kmu.maplayers.politicalmap.dominance.weighting;

import kmlib.starsector.entities.EntityNameplate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the fold that turns breakdowns already read into the footprints the dominance rule compares,
 * on hand-built breakdowns. It is what lets a box that ranks a system and then explains each score
 * weigh every market once: the totals have to come out exactly as a fresh weighing would have folded
 * them, or the ranking and the account would disagree.
 *
 * <p>The walk that reads the breakdowns off the economy is
 * {@link KnownMarketFootprintsIntegrationTests}'s.
 */
final class KnownMarketFootprintsTests {

    // Where a colony sits and whether it is concealed. Named so the pair of booleans a breakdown
    // opens with can be read rather than counted off against the record's own order.
    private static final boolean VISIBLE_COLONY = false;
    private static final boolean PLANET_COLONY = false;
    private static final boolean STATION_COLONY = true;

    @Nested
    class FoldFootprintsByFaction {

        @Test
        void sumsEachFactionsMarketsIntoItsFootprint() {

            var footprints = KnownMarketFootprints.foldFootprintsByFaction(Map.of(
                "hegemony", List.of(
                    buildBreakdown("jangala", PLANET_COLONY, 2.0),
                    buildBreakdown("orbital", STATION_COLONY, 1.0))));

            // Two markets worth 2000 and 1000 on the grid, the planet's weight counted apart since
            // the rule prefers a planet at an otherwise exact tie.
            assertThat(footprints.get("hegemony"))
                .isEqualTo(new MarketFootprint(2, 3000, 2000, 2000));
        }

        @Test
        void keepsEveryFactionApart() {

            var footprints = KnownMarketFootprints.foldFootprintsByFaction(Map.of(
                "hegemony", List.of(buildBreakdown("jangala", PLANET_COLONY, 2.0)),
                "tritachyon", List.of(buildBreakdown("eventide", PLANET_COLONY, 3.0))));

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(2000);
            assertThat(footprints.get("tritachyon").totalWeight())
                .isEqualTo(3000);
        }

        @Test
        void keepsTheOrderTheBreakdownsWereKeyedIn() {
            // The order a ranking meets factions in, which a tie-break by first-seen would read.
            var breakdownsByFactionId = new LinkedHashMap<String, List<MarketWeightBreakdown>>();

            breakdownsByFactionId.put("tritachyon", List.of(buildBreakdown("eventide", PLANET_COLONY, 3.0)));
            breakdownsByFactionId.put("hegemony", List.of(buildBreakdown("jangala", PLANET_COLONY, 2.0)));

            assertThat(KnownMarketFootprints.foldFootprintsByFaction(breakdownsByFactionId).keySet())
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void foldsNothingIntoNothing() {

            assertThat(KnownMarketFootprints.foldFootprintsByFaction(Map.of()))
                .isEmpty();
        }
    }

    // A plain colony - no station factor, no patrols - worth the given size points.
    private static MarketWeightBreakdown buildBreakdown(
            String marketId,
            boolean isStationMarket,
            double contribution) {

        return new MarketWeightBreakdown(
            marketId,
            EntityNameplate.createUnmarkedNameplate(marketId),
            VISIBLE_COLONY,
            isStationMarket,
            10.0,
            new BaseSizeFactor(4, 4.0, contribution, 0.0),
            Optional.empty(),
            Optional.empty());
    }
}
