package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.DISTANT_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.FLIPPED_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.NEIGHBOUR_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildHolderOf;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildHoldersOf;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildTwoAdjacentCells;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.matchSystemArg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

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
 * <p>A unit test, so the three reads are mocked at their static seams - the dominance resolve, the
 * inhabitation read, and the spotlit presence read. Each is pinned by its own suite; what belongs
 * here is which of them is asked about what, and what is done with the answers.
 */
final class MarkedSystemRederiveTest {

    // The bloc a spotlight case picks. Neither of the two factions above, so a system recorded as
    // holding the pick's presence cannot be one the pick is holding.
    private static final String SPOTLIT_BLOC = "selected-bloc";

    @Nested
    class RederiveMarkedSystems {

        // Closed in reverse on the way out, so a seam opened over another is never left standing
        // when the inner one is already gone.
        private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

        // The two adjacent cells every case poses over, built once so the neighbours a flip
        // disturbs are read off one arrangement.
        private final CellGeometryCache cellGeometry = buildTwoAdjacentCells();

        private MockedStatic<SectorPolitics> politicsMock;
        private MockedStatic<PoliticalMapInhabitation> inhabitationMock;
        private MockedStatic<FilteredPolitics> presenceMock;

        // The batch's own reading of the sector. A real pass over a stubbed sector rather than a
        // stand-in, so the systems a case marks are the ones it resolves - and so the presence read
        // it hands on is the one the seam below answers for.
        private DominancePass pass;

        @BeforeEach
        void openSeamsAndBuildThePass() {

            pass = SectorPoliticsFixtures.buildPassOver(buildSectorWithSystems(
                FLIPPED_SYSTEM,
                NEIGHBOUR_SYSTEM,
                DISTANT_SYSTEM));

            politicsMock = openSeam(SectorPolitics.class);

            // Every fixture below marks systems its territories already count as settled, so the
            // seam answers "still settled" and a case about inhabitation says so by re-stubbing it.
            // That keeps the holder cases free of a second fact moving underneath them.
            inhabitationMock = openSeam(PoliticalMapInhabitation.class);
            inhabitationMock
                .when(() -> PoliticalMapInhabitation.isSystemInhabited(any(), any()))
                .thenReturn(true);

            // Off filter the presence read answers empty, which is what the seam's own default
            // gives; a spotlight case stubs where the pick lives.
            presenceMock = openSeam(FilteredPolitics.class);
            presenceMock
                .when(() -> FilteredPolitics.findPresentSystemIds(
                    any(DominancePass.class),
                    any(),
                    any()))
                .thenReturn(Set.of());
        }

        @AfterEach
        void closeSeams() {
            for (var index = openStaticSeams.size() - 1; index >= 0; index--) {
                openStaticSeams.get(index).close();
            }
            openStaticSeams.clear();
        }

        @Test
        void rederiveMarkedSystemsRecordsTheNewHolderWhenASystemChangesHands() {

            var territories = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            rederive(territories, FLIPPED_SYSTEM);

            assertThat(territories.getHolderBySystemId())
                .containsExactly(Map.entry(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON)));
        }

        @Test
        void rederiveMarkedSystemsDisturbsBothSidesAndTheRingOfAFlip() {
            // Both outlines moved and every neighbour's shared edge changed class, so the batch
            // owes the ring a re-shape and both sides a territory rebuild. Recorded together
            // because half of that is a border drawn down one side only.
            var territories = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            var disturbance = rederive(territories, FLIPPED_SYSTEM);

            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM, NEIGHBOUR_SYSTEM);
            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY, TRITACHYON);
        }

        @Test
        void rederiveMarkedSystemsDropsTheHolderOfASystemThatLostItsLastColony() {
            // Decivilised or bombed out: the entry goes rather than being left pointing at the
            // faction that lost it, since every reader takes an absent entry for "nobody holds
            // this".
            var territories = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, null);

            rederive(territories, FLIPPED_SYSTEM);

            assertThat(territories.getHolderBySystemId())
                .isEmpty();
        }

        @Test
        void rederiveMarkedSystemsDisturbsNothingWhenNoFactMoved() {
            // The common resize: a colony grew, its faction still wins, nothing about what stands
            // there changed. Every fill and border is identical, so a disturbance recorded here
            // would spend a frame redrawing the map it already had.
            var territories = buildSettledIn(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            var disturbance = rederive(territories, FLIPPED_SYSTEM);

            assertThat(disturbance.getCellIdsToRedraw())
                .isEmpty();
            assertThat(disturbance.hasFlips())
                .isFalse();
        }

        @Test
        void rederiveMarkedSystemsAddsASystemTakingItsFirstColonyToTheInhabitedSet() {
            // A haven appearing where nothing stood, on a layer whose holding cannot account for
            // whoever built it: no holder moves, so this set is the only record of the change and
            // the cell over it the only surface that can report one.
            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSettledIn(
                Map.of(),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, true);

            var disturbance = rederive(territories, FLIPPED_SYSTEM);

            assertThat(territories.getInhabitedSystemIds())
                .containsExactly(FLIPPED_SYSTEM);
            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM);
        }

        @Test
        void rederiveMarkedSystemsRemovesASystemLosingItsLastColonyFromTheInhabitedSet() {

            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSettledIn(
                Map.of(),
                Set.of(FLIPPED_SYSTEM));

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, false);

            var disturbance = rederive(territories, FLIPPED_SYSTEM);

            assertThat(territories.getInhabitedSystemIds())
                .isEmpty();
            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM);
        }

        @Test
        void rederiveMarkedSystemsRebuildsNoTerritoryWhenOnlyInhabitationMoved() {
            // Inhabitation moves no seam - a cell's shape is settled by which of its edges are
            // same-owner seams - so the neighbour keeps the shape it has and no bloc's outline is
            // retraced. This is what makes the redraw cheap enough to run on a colony event.
            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSettledIn(
                Map.of(),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, true);

            var disturbance = rederive(territories, FLIPPED_SYSTEM);

            assertThat(disturbance.getAffectedFactionIds())
                .isEmpty();
            assertThat(disturbance.getCellIdsToRedraw())
                .doesNotContain(NEIGHBOUR_SYSTEM);
        }

        @Test
        void rederiveMarkedSystemsRecordsThePickArrivingInAnUnheldSystem() {
            // The third fact, which goes stale exactly as the other two do: a cell restyled against
            // the standing presence set would sink the pick's brand new colony under the recede
            // meant for everything the pick is not.
            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSpotlighting(
                SPOTLIT_BLOC,
                Map.of(),
                Set.of(FLIPPED_SYSTEM),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertPickLivesIn(FLIPPED_SYSTEM);

            var disturbance = rederive(territories, FLIPPED_SYSTEM);

            assertThat(territories.getSpotlitPresenceSystemIds())
                .containsExactly(FLIPPED_SYSTEM);
            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM);
        }

        @Test
        void rederiveMarkedSystemsDropsThePickPresenceOfASystemItNowHolds() {
            // Presence is what spares a cell nobody holds, so a system the batch has just given to
            // the pick leaves the set rather than being carried in it under a holder that draws it
            // anyway - which is the set a full rebuild would have resolved, asked only of the
            // systems its holding left out.
            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSpotlighting(
                SPOTLIT_BLOC,
                Map.of(),
                Set.of(FLIPPED_SYSTEM),
                Set.of(FLIPPED_SYSTEM));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(SPOTLIT_BLOC));

            rederive(territories, FLIPPED_SYSTEM);

            assertThat(territories.getSpotlitPresenceSystemIds())
                .isEmpty();

            // Asked of nothing at all, since the one marked system is now held: the read walks the
            // sector to find its candidates, so handing it a system it cannot change anything for
            // is a walk paid for an answer that is discarded.
            presenceMock.verify(() -> FilteredPolitics.findPresentSystemIds(
                any(DominancePass.class),
                eq(SPOTLIT_BLOC),
                eq(Set.of())));
        }

        // Opens a static seam and registers it for closing, so a case names what it needs rather
        // than repeating the open-and-remember pair for each.
        private <T> MockedStatic<T> openSeam(Class<T> seamType) {
            MockedStatic<T> staticMock = mockStatic(seamType);
            openStaticSeams.add(staticMock);
            return staticMock;
        }

        // Runs the re-derive over the two-cell geometry every case shares.
        private StalePoliticsDisturbance rederive(
                PoliticalMapTerritories territories,
                String... markedSystemIds) {

            return MarkedSystemRederive.rederiveMarkedSystems(
                territories,
                cellGeometry,
                pass,
                Set.of(markedSystemIds));
        }

        // A territories holding the given systems and counting each of them settled, which is the
        // only arrangement production builds: a bloc holds a system by having a colony in it.
        private static PoliticalMapTerritories buildSettledIn(
                Map<String, String> factionIdBySystemId) {
            return PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(
                buildHoldersOf(factionIdBySystemId));
        }

        // What the dominance resolve answers for one system this batch. Every case stubs the
        // systems it marks; an unstubbed one comes back null, which reads as a system that lost its
        // holder rather than as a missing stub.
        private void assertResolvesTo(String systemId, DominantHolder holder) {
            politicsMock
                .when(() -> SectorPolitics.resolveDominantHolder(
                    matchSystemArg(systemId),
                    any(DominancePass.class)))
                .thenReturn(holder);
        }

        // What the inhabitation read answers for one system, against the seam's own "still
        // settled" default.
        private void assertSettledIs(String systemId, boolean isInhabited) {
            inhabitationMock
                .when(() -> PoliticalMapInhabitation.isSystemInhabited(
                    any(),
                    matchSystemArg(systemId)))
                .thenReturn(isInhabited);
        }

        // Where the presence read finds the pick living. Its rule is its own suite's; what a case
        // here states is the answer the batch folds.
        private void assertPickLivesIn(String systemId) {
            presenceMock
                .when(() -> FilteredPolitics.findPresentSystemIds(
                    any(DominancePass.class),
                    eq(SPOTLIT_BLOC),
                    any()))
                .thenReturn(Set.of(systemId));
        }
    }
}
