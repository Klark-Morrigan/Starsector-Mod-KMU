package kmu.maplayers.ownermap.render;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.holding.OwnerMapInhabitation;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveFake;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.DISTANT_SYSTEM;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.FLIPPED_SYSTEM;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.NEIGHBOUR_SYSTEM;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.buildHolderOf;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.buildHoldersOf;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.buildSectorWithSystems;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.buildTwoAdjacentCells;
import static kmu.maplayers.ownermap.render.StaleOwnerMapFixtures.matchSystemArg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Pins what a drained batch reads back into the built map, and what it records that as having
 * disturbed. Three facts settle how a marked system's cell draws and each moves on its own, so
 * every case here states one of them moving and reads back both halves: what the occupancy now
 * says, and what the batch thinks it owes for saying it.
 *
 * <p>Both halves are silent when wrong, and nothing corrects them: a colony event marks a system
 * once, so a fact folded without the redraw it owes leaves the map disagreeing with the sector
 * until something unrelated rebuilds it, and a redraw recorded without the fold behind it spends a
 * frame arriving at the cell it already had.
 *
 * <p>A unit test, so the three reads are mocked at their static seams - the holder resolve, the
 * inhabitation read, and the spotlit presence read. Each is pinned by its own suite; what belongs
 * here is which of them is asked about what, and what is done with the answers.
 */
final class MarkedSystemRederiveTest {

    // The bloc a spotlight case picks. Neither of the two factions above, so a system recorded as
    // holding the pick's presence cannot be one the pick is holding.
    private static final String SPOTLIT_BLOC = "selected-bloc";

    @Nested
    class RederiveMarkedSystems {

        // The classes this arrangement stands in for, closed on the way out.
        private final StaticSeams seams = new StaticSeams();

        // The two adjacent cells every case poses over, built once so the neighbours a flip
        // disturbs are read off one arrangement.
        private final CellGeometryCache cellGeometry = buildTwoAdjacentCells();

        private MockedStatic<OwnerMapInhabitation> inhabitationMock;
        private MockedStatic<SpotlitBlocs> presenceMock;

        // The batch's own holder read, over a real pass across a stubbed sector rather than a
        // stand-in, so the systems a case marks are the ones it resolves - and so the presence read
        // it hands on is the one the seam below answers for. What each system resolves to is
        // stated per case: who holds one is the painting layer's, and no tier case may name it.
        private final Map<String, SystemOwner> holderBySystemId = new LinkedHashMap<>();
        private SystemHolderResolveFake holderResolve;

        @BeforeEach
        void openSeamsAndBuildThePass() {

            holderBySystemId.clear();
            holderResolve = SystemHolderResolveFake.createOver(
                HolderPass.readFromLunaSettings(
                    buildSectorWithSystems(FLIPPED_SYSTEM, NEIGHBOUR_SYSTEM, DISTANT_SYSTEM),
                    HolderGrouping.identity()),
                holderBySystemId);

            // Every fixture below marks systems its clusters already count as settled, so the
            // seam answers "still settled" and a case about inhabitation says so by re-stubbing it.
            // That keeps the holder cases free of a second fact moving underneath them.
            inhabitationMock = seams.openSeam(OwnerMapInhabitation.class);
            inhabitationMock
                .when(() -> OwnerMapInhabitation.isSystemInhabited(any(), any()))
                .thenReturn(true);

            // Off filter the presence read answers empty, which is what the seam's own default
            // gives; a spotlight case stubs where the pick lives.
            presenceMock = seams.openSeam(SpotlitBlocs.class);
            presenceMock
                .when(() -> SpotlitBlocs.findPresentSystemKeys(
                    any(HolderPass.class),
                    any(),
                    any()))
                .thenReturn(Set.of());
        }

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        @Test
        void rederiveMarkedSystemsRecordsTheNewHolderWhenASystemChangesHands() {

            var clusters = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            rederive(clusters, FLIPPED_SYSTEM);

            assertThat(clusters.getOccupancy().getHolderBySystemKey())
                .containsExactly(Map.entry(buildCellKey(FLIPPED_SYSTEM), buildHolderOf(TRITACHYON)));
        }

        @Test
        void rederiveMarkedSystemsDisturbsBothSidesAndTheRingOfAFlip() {
            // Both outlines moved and every neighbour's shared edge changed class, so the batch
            // owes the ring a re-shape and both sides a cluster group rebuild. Recorded together
            // because half of that is a border drawn down one side only.
            var clusters = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            var disturbance = rederive(clusters, FLIPPED_SYSTEM);

            assertThat(disturbance.getCellKeysToRedraw())
                .containsExactly(buildCellKey(FLIPPED_SYSTEM), buildCellKey(NEIGHBOUR_SYSTEM));
            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY, TRITACHYON);
        }

        @Test
        void rederiveMarkedSystemsDropsTheHolderOfASystemThatLostItsLastColony() {
            // Decivilised or bombed out: the entry goes rather than being left pointing at the
            // faction that lost it, since every reader takes an absent entry for "nobody holds
            // this".
            var clusters = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, null);

            rederive(clusters, FLIPPED_SYSTEM);

            assertThat(clusters.getOccupancy().getHolderBySystemKey())
                .isEmpty();
        }

        @Test
        void rederiveMarkedSystemsDisturbsNothingWhenNoFactMoved() {
            // The common resize: a colony grew, its faction still wins, nothing about what stands
            // there changed. Every fill and border is identical, so a disturbance recorded here
            // would spend a frame redrawing the map it already had.
            var clusters = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            var disturbance = rederive(clusters, FLIPPED_SYSTEM);

            assertThat(disturbance.getCellKeysToRedraw())
                .isEmpty();
            assertThat(disturbance.hasFlips())
                .isFalse();
        }

        @Test
        void rederiveMarkedSystemsAddsASystemTakingItsFirstColonyToTheInhabitedSet() {
            // A haven appearing where nothing stood, on a layer whose holding cannot account for
            // whoever built it: no holder moves, so this set is the only record of the change and
            // the cell over it the only surface that can report one.
            var clusters = OwnerMapClusterFixtures.createClustersSettledIn(
                Map.of(),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, true);

            var disturbance = rederive(clusters, FLIPPED_SYSTEM);

            assertThat(clusters.getOccupancy().getInhabitedSystemKeys())
                .containsExactly(buildCellKey(FLIPPED_SYSTEM));
            assertThat(disturbance.getCellKeysToRedraw())
                .containsExactly(buildCellKey(FLIPPED_SYSTEM));
        }

        @Test
        void rederiveMarkedSystemsRemovesASystemLosingItsLastColonyFromTheInhabitedSet() {

            var clusters = OwnerMapClusterFixtures.createClustersSettledIn(
                Map.of(),
                Set.of(FLIPPED_SYSTEM));

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, false);

            var disturbance = rederive(clusters, FLIPPED_SYSTEM);

            assertThat(clusters.getOccupancy().getInhabitedSystemKeys())
                .isEmpty();
            assertThat(disturbance.getCellKeysToRedraw())
                .containsExactly(buildCellKey(FLIPPED_SYSTEM));
        }

        @Test
        void rederiveMarkedSystemsRebuildsNoClusterGroupWhenOnlyInhabitationMoved() {
            // Inhabitation moves no seam - a cell's shape is settled by which of its edges are
            // same-owner seams - so the neighbour keeps the shape it has and no bloc's outline is
            // retraced. This is what makes the redraw cheap enough to run on a colony event.
            var clusters = OwnerMapClusterFixtures.createClustersSettledIn(
                Map.of(),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, true);

            var disturbance = rederive(clusters, FLIPPED_SYSTEM);

            assertThat(disturbance.getAffectedFactionIds())
                .isEmpty();
            assertThat(disturbance.getCellKeysToRedraw())
                .doesNotContain(buildCellKey(NEIGHBOUR_SYSTEM));
        }

        @Test
        void rederiveMarkedSystemsRecordsThePickArrivingInAnUnheldSystem() {
            // The third fact, which goes stale exactly as the other two do: a cell restyled against
            // the standing presence set would sink the pick's brand new colony under the recede
            // meant for everything the pick is not.
            var clusters = OwnerMapClusterFixtures.createClustersSpotlighting(
                SPOTLIT_BLOC,
                Map.of(),
                Set.of(FLIPPED_SYSTEM),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertPickLivesIn(FLIPPED_SYSTEM);

            var disturbance = rederive(clusters, FLIPPED_SYSTEM);

            assertThat(clusters.getOccupancy().getSpotlitPresenceSystemKeys())
                .containsExactly(buildCellKey(FLIPPED_SYSTEM));
            assertThat(disturbance.getCellKeysToRedraw())
                .containsExactly(buildCellKey(FLIPPED_SYSTEM));
        }

        @Test
        void rederiveMarkedSystemsDropsThePickPresenceOfASystemItNowHolds() {
            // Presence is what spares a cell nobody holds, so a system the batch has just given to
            // the pick leaves the set rather than being carried in it under a holder that draws it
            // anyway - which is the set a full rebuild would have resolved, asked only of the
            // systems its holding left out.
            var clusters = OwnerMapClusterFixtures.createClustersSpotlighting(
                SPOTLIT_BLOC,
                Map.of(),
                Set.of(FLIPPED_SYSTEM),
                Set.of(FLIPPED_SYSTEM));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(SPOTLIT_BLOC));

            rederive(clusters, FLIPPED_SYSTEM);

            assertThat(clusters.getOccupancy().getSpotlitPresenceSystemKeys())
                .isEmpty();

            // Asked of nothing at all, since the one marked system is now held: the read walks the
            // sector to find its candidates, so handing it a system it cannot change anything for
            // is a walk paid for an answer that is discarded.
            presenceMock.verify(() -> SpotlitBlocs.findPresentSystemKeys(
                any(HolderPass.class),
                eq(SPOTLIT_BLOC),
                eq(Set.of())));
        }

        // Runs the re-derive over the two-cell geometry every case shares.
        private StaleOwnerMapDisturbance rederive(
                OwnerMapClusters clusters,
                String... markedSystemIds) {

            return MarkedSystemRederive.rederiveMarkedSystems(
                clusters,
                cellGeometry,
                holderResolve,
                Set.copyOf(buildCellKeys(markedSystemIds)));
        }

        // A clusters holding the given systems and counting each of them settled, which is the
        // only arrangement production builds: a bloc holds a system by having a colony in it.
        private static OwnerMapClusters buildSettledIn(
                Map<String, String> factionIdBySystemId) {
            return OwnerMapClusterFixtures.createClustersOwnedBy(
                buildHoldersOf(factionIdBySystemId));
        }

        // What the holder resolve answers for one system this batch. Every case stubs the
        // systems it marks; an unstubbed one comes back null, which reads as a system that lost its
        // holder rather than as a missing stub.
        private void assertResolvesTo(String systemId, SystemOwner holder) {
            holderBySystemId.put(systemId, holder);
        }

        // What the inhabitation read answers for one system, against the seam's own "still
        // settled" default.
        private void assertSettledIs(String systemId, boolean isInhabited) {
            inhabitationMock
                .when(() -> OwnerMapInhabitation.isSystemInhabited(
                    any(),
                    matchSystemArg(systemId)))
                .thenReturn(isInhabited);
        }

        // Where the presence read finds the pick living. Its rule is its own suite's; what a case
        // here states is the answer the batch folds.
        private void assertPickLivesIn(String systemId) {
            presenceMock
                .when(() -> SpotlitBlocs.findPresentSystemKeys(
                    any(HolderPass.class),
                    eq(SPOTLIT_BLOC),
                    any()))
                .thenReturn(Set.of(buildCellKey(systemId)));
        }
    }
}
