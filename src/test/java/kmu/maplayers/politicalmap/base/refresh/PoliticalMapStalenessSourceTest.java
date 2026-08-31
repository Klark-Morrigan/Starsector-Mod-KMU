package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.starsector.nexerelin.NexerelinAlliances;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Pins the political map's staleness routing: it reads the snapshot in one walk and sends
 * each axis to its own refresh - a visibility or moving-set move rebuilds geometry, an
 * holder-map diff marks exactly the changed systems politics-stale (the same set the event
 * listeners feed), and an alliance-fingerprint move bumps the alliance revision - while the
 * first poll only establishes the baselines. Asserts on the real geometry and alliance
 * counters' deltas and the real stale set rather than mocking {@link MapLayerRefresh}
 * (whose logger a static mock would null during class init), stubbing the snapshot scan and
 * the alliance fingerprint across polls.
 *
 * <p>Also pins the one passenger that writes rather than reads: the observation each system's own
 * inhabitants make of the colonies a revelation gate holds back, which rides this sweep because
 * nothing in the engine announces one.
 */
final class PoliticalMapStalenessSourceTest {

    // A fixed alliance fingerprint the non-alliance tests hold steady across polls, so the
    // alliance axis stays quiet while they assert on geometry and holding.
    private static final int STEADY_ALLIANCE_FINGERPRINT = 7;

    // One owned system, returned identically on both polls by the runs that mean to leave the
    // visibility and holding axes quiet.
    private static final PoliticalMapSectorSnapshot STEADY_SNAPSHOT =
        new PoliticalMapSectorSnapshot(1, Map.of("a", "hegemony"));

    @Nested
    class MarkChangesSinceLastPoll {

        @Test
        void firstPollEstablishesBaselinesWithoutRefreshingOrMarking() {

            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(1, "a", "hegemony")),
                1);

            assertThat(outcome.geometryDelta())
                .isZero();
            assertThat(outcome.staleSystemIds())
                .isEmpty();
        }

        @Test
        void visibilityChangeRequestsGeometryRefreshOnly() {
            // Same holding, moved visibility: a system joined or left the map, so
            // the cells rebuild but no holder reshapes.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(9, "a", "hegemony")),
                2);

            assertThat(outcome.geometryDelta())
                .isEqualTo(1);
            assertThat(outcome.staleSystemIds())
                .isEmpty();
        }

        @Test
        void ownerFlipMarksThatSystemStaleWithoutGeometryRefresh() {
            // The AI-captures-or-founds case: same membership, a drawn system changed
            // hands, so only that system is marked stale and no geometry rebuilds.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(1, "a", "tritachyon")),
                2);

            assertThat(outcome.geometryDelta())
                .isZero();
            assertThat(outcome.staleSystemIds())
                .containsExactly("a");
        }

        @Test
        void ownerGainedMarksTheSystemStale() {

            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, Map.of()),
                    takeSnapshot(1, "a", "hegemony")),
                2);

            assertThat(outcome.staleSystemIds())
                .containsExactly("a");
        }

        @Test
        void ownerLostMarksTheSystemStale() {
            // A colony decivilised or abandoned drops from the holder map, so its
            // system is marked stale to repaint neutral.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(1, Map.of())),
                2);

            assertThat(outcome.staleSystemIds())
                .containsExactly("a");
        }

        @Test
        void bothAxesMovingRefreshGeometryAndMarkHolderStale() {

            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(9, "a", "tritachyon")),
                2);

            assertThat(outcome.geometryDelta())
                .isEqualTo(1);
            assertThat(outcome.staleSystemIds())
                .containsExactly("a");
        }

        @Test
        void movingSetChangeRequestsGeometryRefreshWithoutMarkingHolderStale() {
            // Unmoved visibility and holding, but a system started or stopped moving:
            // it joins or leaves the partition, so the geometry counter advances and no
            // holder is reshaped.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForMovingSetChange(true),
                2);

            assertThat(outcome.geometryDelta())
                .isEqualTo(1);
            assertThat(outcome.staleSystemIds())
                .isEmpty();
        }

        @Test
        void steadyMovingSetRequestsNoGeometryRefresh() {
            // The moving set is unchanged (a system that keeps moving is already
            // excluded, or nothing moves at all), so nothing rebuilds.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForMovingSetChange(false),
                2);

            assertThat(outcome.geometryDelta())
                .isZero();
            assertThat(outcome.staleSystemIds())
                .isEmpty();
        }

        @Test
        void allianceFingerprintChangeRequestsAllianceRefreshOnly() {
            // An alliance formed, dissolved, or changed members: the fingerprint moved, so
            // the alliance revision bumps while the geometry and holding axes stay put.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForAllianceChange(11, 22),
                2);

            assertThat(outcome.allianceDelta())
                .isEqualTo(1);
            assertThat(outcome.geometryDelta())
                .isZero();
            assertThat(outcome.staleSystemIds())
                .isEmpty();
        }

        @Test
        void steadyAllianceFingerprintRequestsNoAllianceRefresh() {
            // The alliance set is unchanged (or Nex is absent, where the fingerprint is a
            // fixed value), so the alliance revision never advances.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForAllianceChange(11, 11),
                2);

            assertThat(outcome.allianceDelta())
                .isZero();
        }

        @Test
        void firstPollSeedsTheAllianceBaselineWithoutBumping() {
            // The first poll only records the fingerprint; there is no prior to diff against,
            // so it never bumps the alliance revision.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForAllianceChange(11, 22),
                1);

            assertThat(outcome.allianceDelta())
                .isZero();
        }

        @Test
        void everyPollWritesWhatEachSystemsInhabitantsCanSee() {
            // The one passenger that writes rather than reads. Nothing announces a derelict
            // arriving among witnesses, so the observation rides this sweep - on every poll,
            // the first included, having no baseline to seed and nothing to diff against.
            try (var sightingsMock = mockStatic(SectorColonySightings.class)) {

                pollThenReadRefreshOutcome(
                    PollInputs.buildForSnapshotChange(STEADY_SNAPSHOT, STEADY_SNAPSHOT),
                    2);

                // Once per system per poll, the sweep being the poll's own now: the register is
                // handed a place and the set already selected for it rather than walking the
                // sector for either.
                sightingsMock.verify(
                    () -> SectorColonySightings.recordSightingsByInhabitants(
                        any(SectorAPI.class),
                        any(StarSystemAPI.class),
                        any(Colonies.class),
                        any(ColonyKnowledge.class)),
                    times(2));
            }
        }

        @Test
        void pollsTheSectorItsInstallationWasMadeForRatherThanTheRunningOne() {
            // Vanilla drives this poll's cadence and names no sector, so it used to ask the running
            // game which one it was polling - right only while the sector it was installed on and
            // the sector loaded are the same. Posed with the two apart: a poll that read the loaded
            // one would diff its holders against another sector's baselines and stage its drift
            // into another sector's tracker, neither of which anything on screen would report.
            var installedSector = buildOneSystemSector();
            var polledSector = new AtomicReference<SectorAPI>();

            try (var globalMock = mockStatic(Global.class);
                    var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                    var snapshotMock = mockStatic(PoliticalMapSectorSnapshot.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(mock(SectorAPI.class));
                globalMock
                    .when(() -> Global.getLogger(any(Class.class)))
                    .thenReturn(mock(Logger.class));

                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(MapVisibilityRules.BASE);

                // Read off the pass rather than off any one passenger: the pass is the single
                // reading every passenger below is handed, so what it was opened over is what the
                // whole poll walked.
                snapshotMock
                    .when(() -> PoliticalMapSectorSnapshot.scan(any(MapVisibilityPass.class)))
                    .thenAnswer(scan -> {
                        polledSector.set(scan
                            .<MapVisibilityPass>getArgument(0)
                            .colonies()
                            .getSector());
                        return STEADY_SNAPSHOT;
                    });

                new PoliticalMapStalenessSource(new MapLayerInstallation(installedSector))
                    .markChangesSinceLastPoll();
            }
            assertThat(polledSector)
                .hasValue(installedSector);
        }
    }

    // One empty star system and nothing else. The snapshot scan and the motion walk are both
    // stubbed away, so the only passenger that reads this is the observation write - which
    // needs a system to be handed, having stopped sweeping for one. Nothing is staged in it:
    // what each poll writes is the register's own suite's business, not this one's.
    private static SectorAPI buildOneSystemSector() {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(mock(StarSystemAPI.class)));

        return sectorMock;
    }

    private static PoliticalMapSectorSnapshot takeSnapshot(
            int visibilityFingerprint,
            String systemId,
            String factionId) {

        return takeSnapshot(visibilityFingerprint, Map.of(systemId, factionId));
    }

    private static PoliticalMapSectorSnapshot takeSnapshot(
            int visibilityFingerprint,
            Map<String, String> ownerBySystemId) {

        return new PoliticalMapSectorSnapshot(visibilityFingerprint, ownerBySystemId);
    }

    // Polls the source pollCount times against the run's stubbed reads and reports how far
    // each counter moved and which systems were marked politics-stale. Global is stubbed to a
    // bare one-system sector - enough for the poll's passengers to be handed something, with
    // the two that read it stubbed away - and to a no-op logger; MapLayerRefresh is left real
    // so its counters and stale set record the requests. The stale set is drained first to
    // isolate this run from earlier tests' marks.
    //
    // The motion tracker is always stubbed to a mock rather than driven with real positions, so
    // every run reports its moving set explicitly and none of them depends on the motion-detection
    // math. It is reached through the installation the source is built against, which is the one
    // collaborator this suite hands the source - and which is also where the sector it polls comes
    // from, the running game answering the source nothing.
    private static RefreshOutcome pollThenReadRefreshOutcome(PollInputs inputs, int pollCount) {

        // Wired before the static stubbing opens, so Mockito sees no stubbing nested inside
        // another.
        var sector = buildOneSystemSector();

        try (var globalMock = mockStatic(Global.class);
                var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                var snapshotMock = mockStatic(PoliticalMapSectorSnapshot.class);
                var alliancesMock = mockStatic(NexerelinAlliances.class)) {

            // Only the logger. The sector lookup is left unstubbed on purpose: a poll reads its
            // installation's sector, so a run that only passed because the running game answered
            // with one would fail here rather than read as a case about routing.
            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(mock(Logger.class));

            // The source reads the reveal toggles itself; stub them to the no-reveal view
            // so the scan and motion walks resolve the normal drawn set.
            visibilityRulesMock
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(MapVisibilityRules.BASE);

            snapshotMock
                .when(() -> PoliticalMapSectorSnapshot.scan(any(MapVisibilityPass.class)))
                .thenReturn(inputs.firstSnapshot(), inputs.secondSnapshot());

            alliancesMock
                .when(NexerelinAlliances::computeAllianceFingerprint)
                .thenReturn(
                    inputs.firstAllianceFingerprint(),
                    inputs.secondAllianceFingerprint());

            var movingSystemsMock = mock(MovingSystems.class);

            when(movingSystemsMock.updateMovingSystems(any(MapVisibilityPass.class)))
                .thenReturn(false, inputs.hasMovingSetChangedOnSecondPoll());

            var installationMock = mock(MapLayerInstallation.class);

            when(installationMock.resolveMovingSystems())
                .thenReturn(movingSystemsMock);
            when(installationMock.resolveSector())
                .thenReturn(sector);

            return runPollsAndReadOutcome(installationMock, pollCount);
        }
    }

    // Drains any prior stale marks, records the geometry and alliance counters, polls the
    // source pollCount times, and reports how far each counter moved and which systems were
    // marked politics-stale. Split from the stubbing so the try-with-resources block above
    // reads as configuration alone; the counters are read as deltas so a run is isolated from
    // earlier tests' bumps.
    private static RefreshOutcome runPollsAndReadOutcome(
            MapLayerInstallation installation,
            int pollCount) {

        MapLayerRefresh.drainStaleGroupingSystemIds();

        var geometryBefore = MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY);
        var allianceBefore = MapLayerRefresh.getRevision(PoliticalMapRefreshSignal.ALLIANCES);
        var stalenessSource = new PoliticalMapStalenessSource(installation);

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
    private record PollInputs(
            PoliticalMapSectorSnapshot firstSnapshot,
            PoliticalMapSectorSnapshot secondSnapshot,
            int firstAllianceFingerprint,
            int secondAllianceFingerprint,
            boolean hasMovingSetChangedOnSecondPoll) {

        // Two snapshots that may differ, every other axis quiet - what the geometry and
        // holding assertions need.
        private static PollInputs buildForSnapshotChange(
                PoliticalMapSectorSnapshot first,
                PoliticalMapSectorSnapshot second) {

            return new PollInputs(
                first,
                second,
                STEADY_ALLIANCE_FINGERPRINT,
                STEADY_ALLIANCE_FINGERPRINT,
                false);
        }

        // A steady sector where only the moving set moves, so the move-driven geometry
        // refresh is isolated from the visibility axis that shares its counter.
        private static PollInputs buildForMovingSetChange(
                boolean hasMovingSetChangedOnSecondPoll) {

            return new PollInputs(
                STEADY_SNAPSHOT,
                STEADY_SNAPSHOT,
                STEADY_ALLIANCE_FINGERPRINT,
                STEADY_ALLIANCE_FINGERPRINT,
                hasMovingSetChangedOnSecondPoll);
        }

        // A steady sector where only the alliance fingerprint moves, so the geometry counter
        // and the stale set must stay put while the alliance revision is what changes.
        private static PollInputs buildForAllianceChange(
                int firstFingerprint,
                int secondFingerprint) {

            return new PollInputs(
                STEADY_SNAPSHOT,
                STEADY_SNAPSHOT,
                firstFingerprint,
                secondFingerprint,
                false);
        }
    }

    private record RefreshOutcome(
        int geometryDelta,
        int allianceDelta,
        Set<String> staleSystemIds) {
    }
}
