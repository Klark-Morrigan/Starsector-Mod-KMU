package kmu.maplayers.politicalmap.base.politics;

import kmlib.testfixtures.starsector.systems.claims.ClaimReaderFake;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildEconomylessSectorWithSystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHolderPassOver;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.findSystemIn;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Integration coverage for {@link ClaimStatsAggregator}'s whole-sector totals end to end: claims
 * read through a {@link ClaimReaderFake} and colony sizes read off the real colony walk behind a
 * stubbed sector, folded together on one pass. Exercises them together because the value is the
 * wiring - two differently scoped metrics off two different sources landing in one per-bloc entry -
 * which mocking either source would hide.
 *
 * <p>The last cases ask what makes an entry at all, which is the sector's habitation rather than its
 * economy: a bloc living somewhere is offered to be spotlighted there whether or not the economy
 * lists what it lives on, and a bloc whose only holding is a hulk nobody lives on is offered
 * nothing.
 */
final class ClaimStatsAggregatorIntegrationTest {

    @Nested
    class AggregateClaimStats {

        @Test
        void aggregateClaimStatsCountsOneClaimPerClaimedSystem() {
            // Claims accumulate across systems rather than overwriting: a faction claiming two
            // systems reads as two claims on one entry, which is the count the layer paints.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("system-a"),
                listSystemMarkets("system-b"));

            var claimReaderFake = new ClaimReaderFake();

            claimReaderFake.setClaim("system-a", "hegemony");
            claimReaderFake.setClaim("system-b", "hegemony");

            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(sectorMock), claimReaderFake))
                .containsExactly(entry("hegemony", new ClaimStats(2, 0)));
        }

        @Test
        void aggregateClaimStatsSumsColoniesFromSystemsTheBlocDoesNotClaim() {
            // Market size is whole-sector, not claim-scoped: a faction claiming one system and
            // holding its colonies in two others totals all three colonies. Claim-scoped size would
            // read zero here, since a claimant usually does not hold what it claims.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("claimed-system"),
                listSystemMarkets("home-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("outpost-system", buildVisibleMarket(hegemony, 3)));

            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed-system", "hegemony");

            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(sectorMock), claimReaderFake))
                .containsExactly(entry("hegemony", new ClaimStats(1, 8)));
        }

        @Test
        void aggregateClaimStatsOffersAClaimantHoldingNoColonyAnywhere() {
            // A faction that claims territory but holds no market in the sector reads as claims with
            // a zero market size - normal rather than a defect, since it still paints on this layer
            // and so is still worth spotlighting.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("claimed-system"));

            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed-system", "hegemony");

            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(sectorMock), claimReaderFake))
                .containsExactly(entry("hegemony", new ClaimStats(1, 0)));
        }

        @Test
        void aggregateClaimStatsIncludesAColonyHolderThatClaimsNothing() {
            // The fold applies no gate of its own: a faction holding colonies but claiming nothing
            // still gets an entry, at zero claims. Dropping it is the claims view's call, since it is
            // the view that knows a claimless bloc paints nothing on its layer.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("shared-system", buildVisibleMarket(tritachyon, 4)));

            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("shared-system", "hegemony");

            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(sectorMock), claimReaderFake))
                .containsExactly(
                    entry("hegemony", new ClaimStats(1, 0)),
                    entry("tritachyon", new ClaimStats(0, 4)));
        }

        @Test
        void aggregateClaimStatsFoldsAnAlliancesMembersIntoOneBloc() {
            // Both metrics fold through the pass's grouping, so an alliance's one entry carries a
            // member's claim beside another member's colonies - never the members surfacing apart.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("claimed-system"),
                listSystemMarkets("home-system", buildVisibleMarket(tritachyon, 3)));

            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed-system", "hegemony");

            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));

            assertThat(ClaimStatsAggregator.aggregateClaimStats(
                    HolderPass.over(sectorMock, BASE_FOG, grouping),
                    claimReaderFake))
                .containsExactly(entry("alliance-1", new ClaimStats(1, 3)));
        }

        @Test
        void aggregateClaimStatsIsEmptyForASectorWithNoClaimsAndNoColonies() {
            // An unclaimed, uncolonised system contributes nothing at all - no zero-valued entry -
            // so a bloc absent from the map is absent from the picker rather than listed at nothing.
            var sectorMock = buildSectorWithSystems(List.of(), listSystemMarkets("empty-system"));

            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(sectorMock), new ClaimReaderFake()))
                .isEmpty();
        }

        @Test
        void aggregateClaimStatsCountsClaimsWhenTheSectorHasNoEconomy() {
            // Claims come from the port rather than the economy, so a sector whose economy is not up
            // yet still yields the layer's primary metric; only market size is left at zero.
            var sectorMock = buildEconomylessSectorWithSystem("claimed-system");
            var claimReaderFake = new ClaimReaderFake();

            claimReaderFake.setClaim("claimed-system", "hegemony");

            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(sectorMock), claimReaderFake))
                .containsExactly(entry("hegemony", new ClaimStats(1, 0)));
        }

        @Test
        void aggregateClaimStatsIsEmptyForNullSector() {
            assertThat(ClaimStatsAggregator.aggregateClaimStats(buildHolderPassOver(null), new ClaimReaderFake()))
                .isEmpty();
        }

        @Test
        void aggregateClaimStatsListsABlocLivingOnAnOffEconomyColonyAlone() {
            // The row the picker had no way to offer: a faction whose one station the economy never
            // registered lives in the sector, so the map draws it a run and the box names it - and
            // until the fold read habitation it had no entry here to be listed by.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("unregistered-system"));

            placeMarketsOnSystemEntities(
                findSystemIn(sectorMock, "unregistered-system"),
                buildVisibleMarket(hegemony, 4));

            assertThat(ClaimStatsAggregator.aggregateClaimStats(
                    buildHolderPassOver(sectorMock),
                    new ClaimReaderFake()))
                .containsExactly(entry("hegemony", new ClaimStats(0, 4)));
        }

        @Test
        void aggregateClaimStatsSumsBothKindsOfColonyIntoOneSize() {
            // A bloc holding one of each reads one combined size rather than the listed half alone.
            // The metric answers how much colony the bloc lives on, so what the economy happens to
            // register is no part of the question.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var listedColony = buildVisibleMarket(hegemony, 5);
            var sectorMock = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("mixed-system", listedColony));

            placeMarketsOnSystemEntities(
                findSystemIn(sectorMock, "mixed-system"),
                listedColony,
                buildVisibleMarket(hegemony, 4));

            assertThat(ClaimStatsAggregator.aggregateClaimStats(
                    buildHolderPassOver(sectorMock),
                    new ClaimReaderFake()))
                .containsExactly(entry("hegemony", new ClaimStats(0, 9)));
        }

        @Test
        void aggregateClaimStatsLeavesOutABlocHoldingOnlyADerelict() {
            // The line habitation draws that the listing does not. A hulk's owner is named in a box
            // and lives nowhere, and a spotlight lights territory - so offering the row would offer
            // a pick that lights nothing anywhere, which is not what a greyed row means.
            var sectorMock = buildSectorWithSystems(
                List.of(),
                listSystemMarkets("derelict-system"));

            placeMarketsOnSystemEntities(
                findSystemIn(sectorMock, "derelict-system"),
                buildAbandonedStationMarket(4));

            assertThat(ClaimStatsAggregator.aggregateClaimStats(
                    buildHolderPassOver(sectorMock),
                    new ClaimReaderFake()))
                .isEmpty();
        }
    }
}
