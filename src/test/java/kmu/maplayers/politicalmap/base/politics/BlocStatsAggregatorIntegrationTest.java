package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Integration coverage for {@link BlocStatsAggregator}'s whole-sector picker totals end to end:
 * reading a stubbed economy through {@link KnownMarketFootprints} and the real
 * {@link SystemDominance}, then folding each bloc's four metrics across systems. Exercises them
 * together because the value is the wiring - one walk yielding dominations, presences, summed
 * weight, and summed raw size - which mocking either collaborator would hide.
 */
class BlocStatsAggregatorIntegrationTest {
    // The stability-only weighting rule these suites share; the picker's live entry reads the rule
    // from LunaLib settings only the running game provides.
    private static final DominanceRules STABILITY_WEIGHTED = buildStabilityWeightedRules();

    // The faction-view pass most tests aggregate under: the stability rule, the normal filter, and
    // the identity grouping. The alliance-grouping test builds its own pass.
    private static final DominancePass STABILITY_PASS =
            new DominancePass(STABILITY_WEIGHTED, false, HolderGrouping.identity());

    @Nested
    class AggregateBlocStats {

        @Test
        void aggregateBlocStatsAccumulatesABlocsMetricsAcrossSeveralSystems() {
            // A bloc's metrics accumulate across systems rather than overwriting: a faction present in
            // two systems it dominates counts two dominations, two presences, and sums both its
            // dominance weight and its raw colony size - one option, whole-sector totals.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony),
                    listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                    listSystemMarkets("system-b", buildVisibleMarket(hegemony, 3)));

            // At full stability each size point is worth DOMINANCE_WEIGHT_SCALE (1000), so score sums
            // to (5 + 3) * 1000 and market size to the raw 5 + 3.
            assertThat(BlocStatsAggregator.aggregateBlocStats(sector, STABILITY_PASS))
                    .containsExactly(entry("hegemony", new BlocStats(2, 2, 8000, 8)));
        }

        @Test
        void aggregateBlocStatsCountsDominationOnlyForTheSystemWinner() {
            // Two factions share a system: the heavier is its one dominant holder, so it alone takes a
            // domination count while both take a presence - the metric that tells "wins" from "holds".
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("owned-system",
                    buildVisibleMarket(hegemony, 5), buildVisibleMarket(tritachyon, 3));

            assertThat(BlocStatsAggregator.aggregateBlocStats(sector, STABILITY_PASS))
                    .containsExactly(
                            entry("hegemony", new BlocStats(1, 1, 5000, 5)),
                            entry("tritachyon", new BlocStats(0, 1, 3000, 3)));
        }

        @Test
        void aggregateBlocStatsFoldsAnAlliancesMembersIntoOneBloc() {
            // The alliance grouping folds allied members into one bloc, so the alliance's metrics are
            // its members' summed - one unit's domination, presence, score, and size, never its
            // members surfacing apart.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony, tritachyon),
                    listSystemMarkets("system-a", buildVisibleMarket(hegemony, 2)),
                    listSystemMarkets("system-b", buildVisibleMarket(tritachyon, 3)));
            var grouping = new HolderGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));

            assertThat(BlocStatsAggregator.aggregateBlocStats(
                    sector, new DominancePass(STABILITY_WEIGHTED, false, grouping)))
                    .containsExactly(entry("alliance-1", new BlocStats(2, 2, 5000, 5)));
        }

        @Test
        void aggregateBlocStatsOffersAPresentButWeightlessBloc() {
            // A size-0 colony marks presence and, unopposed, dominates its system while carrying no
            // weight - so the bloc appears with a domination and a presence but a zero score. Presence,
            // not weight, is the selectable gate: a bloc holding paintable territory is offered even
            // when spotlighting it highlights cells worth nothing.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony),
                    listSystemMarkets("weightless-system", buildVisibleMarket(hegemony, 0)));

            assertThat(BlocStatsAggregator.aggregateBlocStats(sector, STABILITY_PASS))
                    .containsExactly(entry("hegemony", new BlocStats(1, 1, 0, 0)));
        }

        @Test
        void aggregateBlocStatsSkipsConditionOnlyMarkets() {
            // A bare rock's condition-only market is no colony, so it never marks a bloc's presence -
            // matching the holding pass, so a bloc is offered exactly when it could paint.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony),
                    listSystemMarkets("bare-system", buildConditionOnlyMarket(hegemony, 6)));

            assertThat(BlocStatsAggregator.aggregateBlocStats(sector, STABILITY_PASS)).isEmpty();
        }

        @Test
        void aggregateBlocStatsIsEmptyForNullSector() {
            assertThat(BlocStatsAggregator.aggregateBlocStats(null, STABILITY_PASS)).isEmpty();
        }
    }

    // A bare rock's condition-only market (the placeholder every uninhabited planet carries): no
    // colony, so it confers no holding and marks no bloc presence. Local to this suite.
    private static MarketAPI buildConditionOnlyMarket(FactionAPI faction, int size) {
        return SectorPoliticsFixtures.buildMarket(faction, size, true, false, false, FULL_STABILITY);
    }
}
