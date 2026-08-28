package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.SectorScenarioFixtures.CONCEALED_HOLDER_ID;
import static kmu.maplayers.SectorScenarioFixtures.buildUnvisitedSectorHoldingGatedPair;
import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildConditionOnlyMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildEconomylessSectorWithSystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.findSystemIn;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.markSystemAsVisitedByPlayer;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mockStatic;

/**
 * Integration coverage for {@link DominanceStatsAggregator}'s whole-sector picker totals end to end:
 * reading a stubbed economy through {@link KnownMarketFootprints} and the real
 * {@link SystemDominance}, then folding each bloc's four metrics across systems. Exercises them
 * together because the value is the wiring - one walk yielding dominations, presences, summed
 * weight, and summed raw size - which mocking either collaborator would hide.
 *
 * <p>The middle cases ask what makes an entry at all, which is the sector's habitation rather than
 * its economy: a bloc living somewhere is listed whether or not the economy lists what it lives on,
 * a bloc whose only holding is a hulk nobody lives on is not, and neither answer may move a
 * domination count - the contest still settles a system from the colonies it weighed alone.
 *
 * <p>The last cases below ask the other half of the same question: not what a bloc's totals come to
 * once it is here, but whether it is offered at all. The picker's selectable set is this fold's key
 * set, five hops down from the view, so a holder the colony rule withholds is a holder the picker
 * cannot list - and until the spoiler gates became toggles, nothing could observe that. They drive
 * the live settings read rather than a rule of their own, the wiring from a toggle to this fold
 * being what they are about.
 */
class DominanceStatsAggregatorIntegrationTest {

    // The stability-only weighting rule these suites share; the picker's live entry reads the rule
    // from LunaLib settings only the running game provides.
    private static final DominanceRules STABILITY_WEIGHTED
        = buildStabilityWeightedRules();

    // The sector the gate cases below pose, whose one system nobody has visited.
    private static final String UNVISITED_SYSTEM = "unvisited-system";

    @Nested
    class AggregateDominanceStats {

        @Test
        void aggregateDominanceStatsAccumulatesABlocsMetricsAcrossSeveralSystems() {
            // A bloc's metrics accumulate across systems rather than overwriting: a faction present in
            // two systems it dominates counts two dominations, two presences, and sums both its
            // dominance weight and its raw colony size - one option, whole-sector totals.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("system-b", buildVisibleMarket(hegemony, 3)));

            // At full stability each size point is worth DOMINANCE_WEIGHT_SCALE (1000), so score sums
            // to (5 + 3) * 1000 and market size to the raw 5 + 3.
            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(entry("hegemony", new DominanceStats(2, 2, 8000, 8)));
        }

        @Test
        void aggregateDominanceStatsCountsDominationOnlyForTheSystemWinner() {
            // Two factions share a system: the heavier is its one dominant holder, so it alone takes a
            // domination count while both take a presence - the metric that tells "wins" from "holds".
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(
                    entry("hegemony", new DominanceStats(1, 1, 5000, 5)),
                    entry("tritachyon", new DominanceStats(0, 1, 3000, 3)));
        }

        @Test
        void aggregateDominanceStatsFoldsAnAlliancesMembersIntoOneBloc() {
            // The alliance grouping folds allied members into one bloc, so the alliance's metrics are
            // its members' summed - one unit's domination, presence, score, and size, never its
            // members surfacing apart.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 2)),
                listSystemMarkets("system-b", buildVisibleMarket(tritachyon, 3)));

            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(
                    DominancePass.over(sector, STABILITY_WEIGHTED, BASE_FOG, grouping)))
                .containsExactly(entry("alliance-1", new DominanceStats(2, 2, 5000, 5)));
        }

        @Test
        void aggregateDominanceStatsOffersAPresentButWeightlessBloc() {
            // A size-0 colony marks presence and, unopposed, dominates its system while carrying no
            // weight - so the bloc appears with a domination and a presence but a zero score. Presence,
            // not weight, is the selectable gate: a bloc holding paintable territory is offered even
            // when spotlighting it highlights cells worth nothing.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("weightless-system", buildVisibleMarket(hegemony, 0)));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(entry("hegemony", new DominanceStats(1, 1, 0, 0)));
        }

        @Test
        void aggregateDominanceStatsSkipsConditionOnlyMarkets() {
            // A bare rock's condition-only market is no colony, so it never marks a bloc's presence -
            // matching the holding pass, so a bloc is offered exactly when it could paint.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("bare-system", buildConditionOnlyMarket(hegemony, 6)));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .isEmpty();
        }

        @Test
        void aggregateDominanceStatsListsABlocLivingOnAnOffEconomyColonyAlone() {
            // The row the picker had no way to offer: every term of a dominance weight is
            // economy-fed, so a faction whose one station the economy never registered weighs
            // nothing - and reading presence off the weights left it out of the list while the band
            // beneath its cell drew its run.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("unregistered-system"));

            placeMarketsOnSystemEntities(
                findSystemIn(sector, "unregistered-system"),
                buildVisibleMarket(hegemony, 4));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(entry("hegemony", new DominanceStats(0, 1, 0, 4)));
        }

        @Test
        void aggregateDominanceStatsLeavesTheDominationWithTheBlocTheContestWeighed() {
            // The unweighed bloc joins the listing and takes no part in the contest: the system is
            // still won by the one colony the economy lists, at the weight it always had.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("shared-system", buildVisibleMarket(hegemony, 5)));

            placeMarketsOnSystemEntities(
                findSystemIn(sector, "shared-system"),
                buildVisibleMarket(tritachyon, 4));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(
                    entry("hegemony", new DominanceStats(1, 1, 5000, 5)),
                    entry("tritachyon", new DominanceStats(0, 1, 0, 4)));
        }

        @Test
        void aggregateDominanceStatsCountsNoDominationWhereTheContestWeighedNobody() {
            // The trap the fold is ordered around. Both blocs live here on colonies the economy does
            // not list, so nothing was weighed and the map paints no fill - and folding them into
            // the ranking would hand one of them a domination anyway, the first entry being the
            // leader before anything is compared and the tie-break settling which by proximity.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("unregistered-system"));

            placeMarketsOnSystemEntities(
                findSystemIn(sector, "unregistered-system"),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 4));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(
                    entry("hegemony", new DominanceStats(0, 1, 0, 5)),
                    entry("tritachyon", new DominanceStats(0, 1, 0, 4)));
        }

        @Test
        void aggregateDominanceStatsSumsBothKindsOfColonyIntoOneSize() {
            // A bloc holding one of each reads one combined size while its score counts the listed
            // half alone: the two halves of these stats are scoped differently on purpose, one
            // answering what the contest made of the bloc and the other how much of the sector it
            // lives in.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var listedColony = buildVisibleMarket(hegemony, 5);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("mixed-system", listedColony));

            placeMarketsOnSystemEntities(
                findSystemIn(sector, "mixed-system"),
                listedColony,
                buildVisibleMarket(hegemony, 4));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .containsExactly(entry("hegemony", new DominanceStats(1, 1, 5000, 9)));
        }

        @Test
        void aggregateDominanceStatsLeavesOutABlocHoldingOnlyADerelict() {
            // The line habitation draws that the listing does not. A hulk's owner is named in a box
            // and lives nowhere, and a spotlight lights territory - so offering the row would offer
            // a pick that lights nothing anywhere, which is not what a greyed row means.
            var sector = buildSectorWithSystems(
                List.of(),
                listSystemMarkets("derelict-system"));

            placeMarketsOnSystemEntities(
                findSystemIn(sector, "derelict-system"),
                buildAbandonedStationMarket(4));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .isEmpty();
        }

        @Test
        void aggregateDominanceStatsIsEmptyForASectorWithNoEconomy() {
            // Every metric here is read from the economy, so a sector whose economy is not up yet
            // yields nothing rather than walking systems it cannot price. The claims aggregation is
            // deliberately not symmetric with this: its primary metric comes from the claim port, so
            // it goes on counting claims without an economy and leaves only market size at zero.
            var sector = buildEconomylessSectorWithSystem("system-a");

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)))
                .isEmpty();
        }

        @Test
        void aggregateDominanceStatsIsEmptyForNullSector() {
            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(null)))
                .isEmpty();
        }

        @Test
        void aggregateDominanceStatsWithholdsAHolderWhoseOnlyColonyIsAnUnseenConcealedBase() {
            // A bloc the colony rule withholds has no entry here at all, so it contributes to none
            // of the four metrics the picker sorts its options by - there is nothing to carry them.
            try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                var sector = buildUnvisitedSectorHoldingGatedPair(UNVISITED_SYSTEM);

                assertThat(readOfferedHolderIds(sector))
                    .doesNotContain(CONCEALED_HOLDER_ID);
            }
        }

        @Test
        void aggregateDominanceStatsOffersThatHolderOnceThePlayerHasBeenInItsSystem() {
            // The gate is still in force; what changed is that somebody has seen the base. A rule
            // reaching the fold means the picker gains the option on the same day the map gains
            // the colony.
            try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                var sector = buildUnvisitedSectorHoldingGatedPair(UNVISITED_SYSTEM);
                markSystemAsVisitedByPlayer(sector, buildOnlySystem(sector));

                assertThat(readOfferedHolderIds(sector))
                    .contains(CONCEALED_HOLDER_ID);
            }
        }

        @Test
        void aggregateDominanceStatsOffersThatHolderOnceTheConcealmentGateIsTurnedOff() {
            // The other way into the same entry: the player has been nowhere near the base and has
            // asked to be shown concealed colonies anyway.
            try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                settingsMock
                    .when(KmuMapLayerSettings::shouldShowUnseenHiddenMarkets)
                    .thenReturn(true);

                var sector = buildUnvisitedSectorHoldingGatedPair(UNVISITED_SYSTEM);

                assertThat(readOfferedHolderIds(sector))
                    .contains(CONCEALED_HOLDER_ID);
            }
        }
    }

    // The blocs the picker would offer over a sector, under the player's live colony rule rather
    // than the fog the cases above pose - which is what makes a gate observable here at all.
    private static List<String> readOfferedHolderIds(SectorAPI sector) {

        var pass = DominancePass.over(
            HolderPass.over(
                sector,
                MapVisibilityRules.readFromLunaSettings().colonyVisibility(),
                HolderGrouping.identity()),
            STABILITY_WEIGHTED);

        return List.copyOf(DominanceStatsAggregator.aggregateDominanceStats(pass).keySet());
    }
}
