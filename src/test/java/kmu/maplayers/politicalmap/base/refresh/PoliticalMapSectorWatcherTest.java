package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapSectorWatcher}'s routing: it polls the snapshot in one
 * walk and sends each half to its own refresh - a visibility move rebuilds
 * geometry, an owner-map diff marks exactly the changed systems politics-stale
 * (the same set the event listeners feed) - while the first poll only establishes
 * the baselines. Asserts on the real geometry counter's delta and the real stale
 * set rather than mocking {@link PoliticalMapRefresh} (whose logger a static mock
 * would null during class init), stubbing only the snapshot scan across polls.
 */
final class PoliticalMapSectorWatcherTest {
    // Comfortably past the 4-5s poll interval, so each advance elapses it and
    // drives exactly one poll.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;

    @Nested
    class Advance {

        @Test
        void firstPollEstablishesBaselinesWithoutRefreshingOrMarking() {
            var outcome = pollThenReadRefreshOutcome(
                    snapshot(1, "a", "hegemony"), snapshot(1, "a", "hegemony"), 1);

            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void visibilityChangeRequestsGeometryRefreshOnly() {
            // Same ownership, moved visibility: a system joined or left the map, so
            // the cells rebuild but no owner reshapes.
            var outcome = pollThenReadRefreshOutcome(
                    snapshot(1, "a", "hegemony"), snapshot(9, "a", "hegemony"), 2);

            assertThat(outcome.geometryDelta()).isEqualTo(1);
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void ownerFlipMarksThatSystemStaleWithoutGeometryRefresh() {
            // The AI-captures-or-founds case: same membership, a drawn system changed
            // hands, so only that system is marked stale and no geometry rebuilds.
            var outcome = pollThenReadRefreshOutcome(
                    snapshot(1, "a", "hegemony"), snapshot(1, "a", "tritachyon"), 2);

            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void ownerGainedMarksTheSystemStale() {
            var outcome = pollThenReadRefreshOutcome(
                    snapshot(1, Map.of()), snapshot(1, "a", "hegemony"), 2);

            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void ownerLostMarksTheSystemStale() {
            // A colony decivilised or abandoned drops from the owner map, so its
            // system is marked stale to repaint neutral.
            var outcome = pollThenReadRefreshOutcome(
                    snapshot(1, "a", "hegemony"), snapshot(1, Map.of()), 2);

            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void bothAxesMovingRefreshGeometryAndMarkOwnerStale() {
            var outcome = pollThenReadRefreshOutcome(
                    snapshot(1, "a", "hegemony"), snapshot(9, "a", "tritachyon"), 2);

            assertThat(outcome.geometryDelta()).isEqualTo(1);
            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void movingSetChangeRequestsGeometryRefreshWithoutMarkingOwnerStale() {
            // Unmoved visibility and ownership, but a system started or stopped moving:
            // it joins or leaves the partition, so the geometry counter advances and no
            // owner is reshaped.
            var outcome = pollThenReadRefreshOutcomeWithMovingSetChange(
                    snapshot(1, "a", "hegemony"), snapshot(1, "a", "hegemony"),
                    true, 2);

            assertThat(outcome.geometryDelta()).isEqualTo(1);
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void steadyMovingSetRequestsNoGeometryRefresh() {
            // The moving set is unchanged (a system that keeps moving is already
            // excluded, or nothing moves at all), so nothing rebuilds.
            var outcome = pollThenReadRefreshOutcomeWithMovingSetChange(
                    snapshot(1, "a", "hegemony"), snapshot(1, "a", "hegemony"),
                    false, 2);

            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).isEmpty();
        }
    }

    private static PoliticalMapSectorSnapshot snapshot(int visibilityFingerprint,
            String systemId, String factionId) {
        return snapshot(visibilityFingerprint, Map.of(systemId, factionId));
    }

    private static PoliticalMapSectorSnapshot snapshot(int visibilityFingerprint,
            Map<String, String> ownerBySystemId) {
        return new PoliticalMapSectorSnapshot(visibilityFingerprint, ownerBySystemId);
    }

    // Advances the watcher pollCount times with the scan stubbed to return first
    // then second, and reports how far the geometry counter moved and which systems
    // the watcher marked politics-stale. Global is stubbed so the scan's sector read
    // is inert and the watcher's logger is a no-op mock; PoliticalMapRefresh is left
    // real so its counter and stale set record the watcher's requests. The stale set
    // is drained first to isolate this run from earlier tests' marks.
    private static RefreshOutcome pollThenReadRefreshOutcome(PoliticalMapSectorSnapshot first,
            PoliticalMapSectorSnapshot second, int pollCount) {
        try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                MockedStatic<PoliticalMapDevOverrides> overridesMock =
                        mockStatic(PoliticalMapDevOverrides.class);
                MockedStatic<PoliticalMapSectorSnapshot> snapshotMock =
                        mockStatic(PoliticalMapSectorSnapshot.class)) {
            globalMock.when(Global::getSector).thenReturn(null);
            globalMock.when(() -> Global.getLogger(any(Class.class)))
                    .thenReturn(mock(Logger.class));
            // The watcher reads the reveal toggles itself now; stub them to the no-reveal
            // view so the scan and motion walks resolve the normal drawn set.
            overridesMock.when(PoliticalMapDevOverrides::readFromLunaSettings)
                    .thenReturn(PoliticalMapDevOverrides.NONE);
            snapshotMock.when(() -> PoliticalMapSectorSnapshot.scan(
                            nullable(SectorAPI.class), any(PoliticalMapDevOverrides.class)))
                    .thenReturn(first, second);

            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var geometryBefore = PoliticalMapRefresh.getGeometryRevision();
            var watcher = new PoliticalMapSectorWatcher();
            for (var poll = 0; poll < pollCount; poll++) {
                watcher.advance(ADVANCE_PAST_POLL_INTERVAL);
            }
            return new RefreshOutcome(
                    PoliticalMapRefresh.getGeometryRevision() - geometryBefore,
                    PoliticalMapRefresh.drainStalePoliticsSystemIds());
        }
    }

    // Like pollThenReadRefreshOutcome but stubs the motion tracker to report the moving
    // set changing on the last poll (no change on the baseline first poll), so the
    // move-driven geometry refresh can be isolated from the visibility axis. The shared
    // tracker is stubbed to a mock instance rather than driven with real positions,
    // keeping the assertion on the watcher's routing, not the motion-detection math.
    private static RefreshOutcome pollThenReadRefreshOutcomeWithMovingSetChange(
            PoliticalMapSectorSnapshot first, PoliticalMapSectorSnapshot second,
            boolean movingSetChangedOnSecondPoll, int pollCount) {
        try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                MockedStatic<PoliticalMapDevOverrides> overridesMock =
                        mockStatic(PoliticalMapDevOverrides.class);
                MockedStatic<PoliticalMapSectorSnapshot> snapshotMock =
                        mockStatic(PoliticalMapSectorSnapshot.class);
                MockedStatic<MovingSystems> movingStaticMock =
                        mockStatic(MovingSystems.class)) {
            globalMock.when(Global::getSector).thenReturn(null);
            globalMock.when(() -> Global.getLogger(any(Class.class)))
                    .thenReturn(mock(Logger.class));
            overridesMock.when(PoliticalMapDevOverrides::readFromLunaSettings)
                    .thenReturn(PoliticalMapDevOverrides.NONE);
            snapshotMock.when(() -> PoliticalMapSectorSnapshot.scan(
                            nullable(SectorAPI.class), any(PoliticalMapDevOverrides.class)))
                    .thenReturn(first, second);
            var movingSystemsMock = mock(MovingSystems.class);
            // nullable(SectorAPI.class), not any(): it matches the null sector the
            // stubbed Global.getSector() hands the watcher and pins the sector overload
            // (the tracker also has a Map-typed updateMovingSystems, so a bare any() is
            // ambiguous).
            when(movingSystemsMock.updateMovingSystems(nullable(SectorAPI.class),
                            any(PoliticalMapDevOverrides.class)))
                    .thenReturn(false, movingSetChangedOnSecondPoll);
            movingStaticMock.when(MovingSystems::getInstance).thenReturn(movingSystemsMock);

            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var geometryBefore = PoliticalMapRefresh.getGeometryRevision();
            var watcher = new PoliticalMapSectorWatcher();
            for (var poll = 0; poll < pollCount; poll++) {
                watcher.advance(ADVANCE_PAST_POLL_INTERVAL);
            }
            return new RefreshOutcome(
                    PoliticalMapRefresh.getGeometryRevision() - geometryBefore,
                    PoliticalMapRefresh.drainStalePoliticsSystemIds());
        }
    }

    private record RefreshOutcome(int geometryDelta, Set<String> staleSystemIds) {
    }
}
