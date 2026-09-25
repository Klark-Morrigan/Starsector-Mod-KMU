package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;
import kmu.settings.KmuMapVisibilitySettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.SectorScenarioFixtures.CONCEALED_HOLDER_ID;
import static kmu.maplayers.SectorScenarioFixtures.buildUnvisitedSectorHoldingGatedPair;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.buildRulesUnder;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.NEUTRAL_BASE;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildConditionOnlyMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildEconomylessSectorWithSystem;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildSectorWithSystems;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.findSystemIn;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.listSystemMarkets;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.markSystemAsVisitedByPlayer;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;

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
 * <p>The index cases ask the same walk where, rather than how much. They are here beside the totals
 * and not in a suite of their own because the fact worth pinning is that the two came off one entry:
 * a bloc's named systems and its presence count are the same reading, and the case posing both
 * blocs at different counts is what would catch them parting.
 *
 * <p>The middle cases ask what makes an entry at all, which is the sector's habitation rather than
 * its economy: a bloc living somewhere is listed whether or not the economy lists what it lives on,
 * a bloc whose only holding is a derelict nobody lives on is not, and neither answer may move a
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
            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
                .containsExactly(
                    entry("hegemony", new DominanceStats(1, 1, 5000, 5)),
                    entry("tritachyon", new DominanceStats(0, 1, 3000, 3)));
        }

        @Test
        void aggregateDominanceStatsCountsNoDominationForANeutralMarketOutweighingAColony() {
            // Neutral is barred from the contest, so the picker's domination count goes to the
            // faction whose cell actually paints. Its presence and its totals stand: the bar is on
            // the candidacy, never on the arithmetic, so the placeholder is still offered and still
            // shown what it scored.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var neutral = buildFaction(Factions.NEUTRAL, NEUTRAL_BASE);
            var sector = buildSectorWith(
                "salvage-system",
                buildVisibleMarket(neutral, 6),
                buildVisibleMarket(hegemony, 3));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
                .contains(
                    entry(Factions.NEUTRAL, new DominanceStats(0, 1, 6000, 6)),
                    entry("hegemony", new DominanceStats(1, 1, 3000, 3)));
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
                    DominancePass.createOver(sector, STABILITY_WEIGHTED, UNDER_THE_FOG, grouping))
                .statsByBlocId())
                .containsExactly(entry("alliance-1", new DominanceStats(2, 2, 5000, 5)));
        }

        @Test
        void aggregateDominanceStatsIndexesEverySystemABlocLivesIn() {
            // The other half of a presence count: which systems it was taken in. A bloc spread over
            // three systems names all three, in the order the sector walk surfaced them, so a
            // surface lighting a picker row's systems lights every one the row counted.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("system-b", buildVisibleMarket(hegemony, 3)),
                listSystemMarkets("system-c", buildVisibleMarket(hegemony, 1)));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector))
                    .presenceIndex()
                    .readPresentSystemKeys("hegemony"))
                .containsExactlyElementsOf(buildCellKeys("system-a", "system-b", "system-c"));
        }

        @Test
        void aggregateDominanceStatsIndexesASystemABlocLivesInWithoutWinningIt() {
            // Presence and not domination, which is the whole point of a separate index: the
            // outweighed bloc lives in both systems and wins neither, and both are still its to be
            // lit. Indexing what a bloc dominates instead would light the fills it already paints.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets(
                    "system-a",
                    buildVisibleMarket(hegemony, 5),
                    buildVisibleMarket(tritachyon, 1)),
                listSystemMarkets(
                    "system-b",
                    buildVisibleMarket(hegemony, 4),
                    buildVisibleMarket(tritachyon, 2)));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector))
                    .presenceIndex()
                    .readPresentSystemKeys("tritachyon"))
                .containsExactlyElementsOf(buildCellKeys("system-a", "system-b"));
        }

        @Test
        void aggregateDominanceStatsIndexesAsManySystemsAsItCountsPresences() {
            // The invariant the index is built for, over a sector where the two blocs differ: three
            // presences against three named systems, two against two. Counting and naming come off
            // the one habitation entry, so a reading where they disagree is a reading where the
            // spotlight and the row beneath the pointer stopped meaning the same thing.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets(
                    "system-b",
                    buildVisibleMarket(hegemony, 4),
                    buildVisibleMarket(tritachyon, 6)),
                listSystemMarkets(
                    "system-c",
                    buildVisibleMarket(hegemony, 1),
                    buildVisibleMarket(tritachyon, 2)));

            var read = DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector));

            assertThat(read.statsByBlocId())
                .containsExactly(
                    entry("hegemony", new DominanceStats(1, 3, 10000, 10)),
                    entry("tritachyon", new DominanceStats(2, 2, 8000, 8)));

            assertThat(read.presenceIndex().readPresentSystemKeys("hegemony"))
                .containsExactlyElementsOf(buildCellKeys("system-a", "system-b", "system-c"));

            assertThat(read.presenceIndex().readPresentSystemKeys("tritachyon"))
                .containsExactlyElementsOf(buildCellKeys("system-b", "system-c"));
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
                .containsExactly(entry("hegemony", new DominanceStats(1, 1, 5000, 9)));
        }

        @Test
        void aggregateDominanceStatsLeavesOutABlocHoldingOnlyADerelict() {
            // The line habitation draws that the listing does not. A derelict's owner is named in a box
            // and lives nowhere, and a spotlight lights territory - so offering the row would offer
            // a pick that lights nothing anywhere, which is not what a greyed row means.
            var sector = buildSectorWithSystems(
                List.of(),
                listSystemMarkets("derelict-system"));

            placeMarketsOnSystemEntities(
                findSystemIn(sector, "derelict-system"),
                buildAbandonedStationMarket(4));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(sector)).statsByBlocId())
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
                .isEqualTo(DominanceStatsRead.EMPTY);
        }

        @Test
        void aggregateDominanceStatsIsEmptyForNullSector() {
            assertThat(DominanceStatsAggregator.aggregateDominanceStats(buildPassOver(null)))
                .isEqualTo(DominanceStatsRead.EMPTY);
        }

        @Test
        void aggregateDominanceStatsWithholdsAHolderWhoseOnlyColonyIsAnUnseenConcealedBase() {
            // A bloc the colony rule withholds has no entry here at all, so it contributes to none
            // of the four metrics the picker sorts its options by - there is nothing to carry them.
            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

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
            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

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
            try (var settingsMock = mockStatic(KmuMapVisibilitySettings.class)) {

                settingsMock
                    .when(KmuMapVisibilitySettings::shouldShowUnseenHiddenMarkets)
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

        var pass = DominancePass.createOver(
            HolderPass.over(
                sector,
                buildRulesUnder(MapVisibilityRules.readFromLunaSettings().colonyVisibility()),
                HolderGrouping.identity()),
            STABILITY_WEIGHTED);

        return List.copyOf(
            DominanceStatsAggregator.aggregateDominanceStats(pass).statsByBlocId().keySet());
    }
}
