package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;
import kmu.starsector.nexerelin.NexerelinAlliances;

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
 * Pins the political map's staleness routing: it reads the snapshot in one walk and sends
 * each axis to its own refresh - a visibility or moving-set move rebuilds geometry, an
 * owner-map diff marks exactly the changed systems politics-stale (the same set the event
 * listeners feed), and an alliance-fingerprint move bumps the alliance revision - while the
 * first poll only establishes the baselines. Asserts on the real geometry and alliance
 * counters' deltas and the real stale set rather than mocking {@link MapLayerRefresh}
 * (whose logger a static mock would null during class init), stubbing the snapshot scan and
 * the alliance fingerprint across polls.
 */
final class PoliticalMapStalenessSourceTest {
    // A fixed alliance fingerprint the non-alliance tests hold steady across polls, so the
    // alliance axis stays quiet while they assert on geometry and ownership.
    private static final int STEADY_ALLIANCE_FINGERPRINT = 7;
    // One owned system, returned identically on both polls by the runs that mean to leave the
    // visibility and ownership axes quiet.
    private static final PoliticalMapSectorSnapshot STEADY_SNAPSHOT =
            new PoliticalMapSectorSnapshot(1, Map.of("a", "hegemony"));

    @Nested
    class MarkChangesSinceLastPoll {

        @Test
        void firstPollEstablishesBaselinesWithoutRefreshingOrMarking() {
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForSnapshotChange(
                    snapshot(1, "a", "hegemony"), snapshot(1, "a", "hegemony")), 1);

            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void visibilityChangeRequestsGeometryRefreshOnly() {
            // Same ownership, moved visibility: a system joined or left the map, so
            // the cells rebuild but no owner reshapes.
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForSnapshotChange(
                    snapshot(1, "a", "hegemony"), snapshot(9, "a", "hegemony")), 2);

            assertThat(outcome.geometryDelta()).isEqualTo(1);
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void ownerFlipMarksThatSystemStaleWithoutGeometryRefresh() {
            // The AI-captures-or-founds case: same membership, a drawn system changed
            // hands, so only that system is marked stale and no geometry rebuilds.
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForSnapshotChange(
                    snapshot(1, "a", "hegemony"), snapshot(1, "a", "tritachyon")), 2);

            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void ownerGainedMarksTheSystemStale() {
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForSnapshotChange(
                    snapshot(1, Map.of()), snapshot(1, "a", "hegemony")), 2);

            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void ownerLostMarksTheSystemStale() {
            // A colony decivilised or abandoned drops from the owner map, so its
            // system is marked stale to repaint neutral.
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForSnapshotChange(
                    snapshot(1, "a", "hegemony"), snapshot(1, Map.of())), 2);

            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void bothAxesMovingRefreshGeometryAndMarkOwnerStale() {
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForSnapshotChange(
                    snapshot(1, "a", "hegemony"), snapshot(9, "a", "tritachyon")), 2);

            assertThat(outcome.geometryDelta()).isEqualTo(1);
            assertThat(outcome.staleSystemIds()).containsExactly("a");
        }

        @Test
        void movingSetChangeRequestsGeometryRefreshWithoutMarkingOwnerStale() {
            // Unmoved visibility and ownership, but a system started or stopped moving:
            // it joins or leaves the partition, so the geometry counter advances and no
            // owner is reshaped.
            var outcome = pollThenReadRefreshOutcome(
                    PollInputs.buildForMovingSetChange(true), 2);

            assertThat(outcome.geometryDelta()).isEqualTo(1);
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void steadyMovingSetRequestsNoGeometryRefresh() {
            // The moving set is unchanged (a system that keeps moving is already
            // excluded, or nothing moves at all), so nothing rebuilds.
            var outcome = pollThenReadRefreshOutcome(
                    PollInputs.buildForMovingSetChange(false), 2);

            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void allianceFingerprintChangeRequestsAllianceRefreshOnly() {
            // An alliance formed, dissolved, or changed members: the fingerprint moved, so
            // the alliance revision bumps while the geometry and ownership axes stay put.
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForAllianceChange(11, 22), 2);

            assertThat(outcome.allianceDelta()).isEqualTo(1);
            assertThat(outcome.geometryDelta()).isZero();
            assertThat(outcome.staleSystemIds()).isEmpty();
        }

        @Test
        void steadyAllianceFingerprintRequestsNoAllianceRefresh() {
            // The alliance set is unchanged (or Nex is absent, where the fingerprint is a
            // fixed value), so the alliance revision never advances.
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForAllianceChange(11, 11), 2);

            assertThat(outcome.allianceDelta()).isZero();
        }

        @Test
        void firstPollSeedsTheAllianceBaselineWithoutBumping() {
            // The first poll only records the fingerprint; there is no prior to diff against,
            // so it never bumps the alliance revision.
            var outcome = pollThenReadRefreshOutcome(PollInputs.buildForAllianceChange(11, 22), 1);

            assertThat(outcome.allianceDelta()).isZero();
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

    // Polls the source pollCount times against the run's stubbed reads and reports how far
    // each counter moved and which systems were marked politics-stale. Global is stubbed so
    // the scan's sector read is inert and the source's logger is a no-op mock;
    // MapLayerRefresh is left real so its counters and stale set record the requests. The
    // stale set is drained first to isolate this run from earlier tests' marks.
    //
    // The shared moving tracker is always stubbed to a mock instance rather than driven with
    // real positions, so every run reports its moving set explicitly and none of them depends
    // on the motion-detection math.
    private static RefreshOutcome pollThenReadRefreshOutcome(PollInputs inputs, int pollCount) {
        try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                MockedStatic<PoliticalMapDevToggles> overridesMock =
                        mockStatic(PoliticalMapDevToggles.class);
                MockedStatic<PoliticalMapSectorSnapshot> snapshotMock =
                        mockStatic(PoliticalMapSectorSnapshot.class);
                MockedStatic<NexerelinAlliances> alliancesMock =
                        mockStatic(NexerelinAlliances.class);
                MockedStatic<MovingSystems> movingStaticMock =
                        mockStatic(MovingSystems.class)) {
            globalMock.when(Global::getSector).thenReturn(null);
            globalMock.when(() -> Global.getLogger(any(Class.class)))
                    .thenReturn(mock(Logger.class));
            // The source reads the reveal toggles itself; stub them to the no-reveal view
            // so the scan and motion walks resolve the normal drawn set.
            overridesMock.when(PoliticalMapDevToggles::readFromLunaSettings)
                    .thenReturn(PoliticalMapDevToggles.NONE);
            snapshotMock.when(() -> PoliticalMapSectorSnapshot.scan(
                            nullable(SectorAPI.class), any(PoliticalMapDevToggles.class)))
                    .thenReturn(inputs.firstSnapshot(), inputs.secondSnapshot());
            alliancesMock.when(NexerelinAlliances::computeAllianceFingerprint)
                    .thenReturn(inputs.firstAllianceFingerprint(),
                            inputs.secondAllianceFingerprint());
            var movingSystemsMock = mock(MovingSystems.class);
            // nullable(SectorAPI.class), not any(): it matches the null sector the
            // stubbed Global.getSector() hands the source and pins the sector overload
            // (the tracker also has a Map-typed updateMovingSystems, so a bare any() is
            // ambiguous).
            when(movingSystemsMock.updateMovingSystems(nullable(SectorAPI.class),
                            any(MapVisibilityOverrides.class)))
                    .thenReturn(false, inputs.hasMovingSetChangedOnSecondPoll());
            movingStaticMock.when(MovingSystems::getInstance).thenReturn(movingSystemsMock);

            return runPollsAndReadOutcome(pollCount);
        }
    }

    // Drains any prior stale marks, records the geometry and alliance counters, polls the
    // source pollCount times, and reports how far each counter moved and which systems were
    // marked politics-stale. Split from the stubbing so the try-with-resources block above
    // reads as configuration alone; the counters are read as deltas so a run is isolated from
    // earlier tests' bumps.
    private static RefreshOutcome runPollsAndReadOutcome(int pollCount) {
        MapLayerRefresh.drainStaleGroupingSystemIds();
        var geometryBefore = MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY);
        var allianceBefore = MapLayerRefresh.getRevision(PoliticalMapRefreshSignal.ALLIANCES);
        var stalenessSource = new PoliticalMapStalenessSource();
        for (var poll = 0; poll < pollCount; poll++) {
            stalenessSource.markChangesSinceLastPoll();
        }
        return new RefreshOutcome(
                MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY) - geometryBefore,
                MapLayerRefresh.getRevision(PoliticalMapRefreshSignal.ALLIANCES) - allianceBefore,
                MapLayerRefresh.drainStaleGroupingSystemIds());
    }

    // One run's stubbed reads: what the two snapshot scans return, what the two alliance
    // fingerprints read, and whether the moving set moved on the second poll. A record rather
    // than a widening parameter list on the helper, so every value keeps a name instead of
    // riding in as a positional int or boolean, and each factory says which single axis its
    // run varies while holding the rest steady.
    private record PollInputs(PoliticalMapSectorSnapshot firstSnapshot,
            PoliticalMapSectorSnapshot secondSnapshot,
            int firstAllianceFingerprint,
            int secondAllianceFingerprint,
            boolean hasMovingSetChangedOnSecondPoll) {

        // Two snapshots that may differ, every other axis quiet - what the geometry and
        // ownership assertions need.
        private static PollInputs buildForSnapshotChange(PoliticalMapSectorSnapshot first,
                PoliticalMapSectorSnapshot second) {
            return new PollInputs(first, second, STEADY_ALLIANCE_FINGERPRINT,
                    STEADY_ALLIANCE_FINGERPRINT, false);
        }

        // A steady sector where only the moving set moves, so the move-driven geometry
        // refresh is isolated from the visibility axis that shares its counter.
        private static PollInputs buildForMovingSetChange(
                boolean hasMovingSetChangedOnSecondPoll) {
            return new PollInputs(STEADY_SNAPSHOT, STEADY_SNAPSHOT,
                    STEADY_ALLIANCE_FINGERPRINT, STEADY_ALLIANCE_FINGERPRINT,
                    hasMovingSetChangedOnSecondPoll);
        }

        // A steady sector where only the alliance fingerprint moves, so the geometry counter
        // and the stale set must stay put while the alliance revision is what changes.
        private static PollInputs buildForAllianceChange(int firstFingerprint,
                int secondFingerprint) {
            return new PollInputs(STEADY_SNAPSHOT, STEADY_SNAPSHOT, firstFingerprint,
                    secondFingerprint, false);
        }
    }

    private record RefreshOutcome(int geometryDelta, int allianceDelta, Set<String> staleSystemIds) {
    }
}
