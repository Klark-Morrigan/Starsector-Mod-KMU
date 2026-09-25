package kmu.maplayers.politicalmap.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.mods.nexerelin.NexerelinAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Pins the political map's staleness routing: it reads the snapshot in one walk and sends
 * each axis to its own refresh - a visibility or moving-set move rebuilds geometry, an
 * holder-map diff marks exactly the changed systems politics-stale under their keys (the same
 * set the event listeners feed), and an alliance-fingerprint move bumps the alliance revision - while the
 * first poll only establishes the baselines. Asserts on a real {@link MapLayerRefreshBoard}
 * rather than a mocked one (whose logger a static mock would null during class init), stubbing
 * the snapshot scan and the alliance fingerprint across polls. The board is the one its own
 * machinery holds, so a run reads only what it raised.
 *
 * <p>Also pins what this poll no longer does: the observation each system's own inhabitants make of
 * the colonies a revelation gate holds back rode it while this was the only sector-wide sweep
 * running, and is the substrate's now.
 */
final class PoliticalMapStalenessSourceTest {

    // A fixed alliance fingerprint the non-alliance tests hold steady across polls, so the
    // alliance axis stays quiet while they assert on geometry and holding.
    private static final int STEADY_ALLIANCE_FINGERPRINT = 7;

    // The one system the snapshots name, keyed as the scan keys a system it holds.
    private static final SystemKey SYSTEM_A = buildCellKey("a");

    // Two systems answering to one ID, told apart by their anchors - the pair a diff by ID would
    // have folded into one baseline.
    private static final SystemKey ANCHORED_TWIN = new SystemKey("a", null, "8b3");
    private static final SystemKey OTHER_TWIN = new SystemKey("a", null, "38d53");

    // One owned system, returned identically on both polls by the runs that mean to leave the
    // visibility and holding axes quiet.
    private static final PoliticalMapSectorSnapshot STEADY_SNAPSHOT =
        new PoliticalMapSectorSnapshot(1, Map.of(SYSTEM_A, "hegemony"));

    @Nested
    class MarkChangesSinceLastPoll {

        @Test
        void firstPollEstablishesBaselinesWithoutRefreshingOrMarking() {

            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(1, "a", "hegemony")),
                1);

            assertThat(outcome.geometryRevision())
                .isZero();
            assertThat(outcome.staleSystemKeys())
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

            assertThat(outcome.geometryRevision())
                .isEqualTo(1);
            assertThat(outcome.staleSystemKeys())
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

            assertThat(outcome.geometryRevision())
                .isZero();
            assertThat(outcome.staleSystemKeys())
                .containsExactly(SYSTEM_A);
        }

        @Test
        void ownerGainedMarksTheSystemStale() {

            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, Map.of()),
                    takeSnapshot(1, "a", "hegemony")),
                2);

            assertThat(outcome.staleSystemKeys())
                .containsExactly(SYSTEM_A);
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

            assertThat(outcome.staleSystemKeys())
                .containsExactly(SYSTEM_A);
        }

        @Test
        void ownerFlipInOneOfTwoSystemsSharingAnIdMarksThatSystemAlone() {
            // The collision the key exists for, met at the diff: the sector holds two systems
            // answering to one ID, and only the second changes hands. Diffed by key, the mark
            // names the twin that flipped and nothing else - diffed by ID, the pair was one
            // baseline holding whichever the walk reached last, and a flip in the other could
            // never be noticed at all.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, Map.of(ANCHORED_TWIN, "hegemony", OTHER_TWIN, "hegemony")),
                    takeSnapshot(1, Map.of(ANCHORED_TWIN, "hegemony", OTHER_TWIN, "tritachyon"))),
                2);

            assertThat(outcome.staleSystemKeys())
                .containsExactly(OTHER_TWIN);
        }

        @Test
        void bothAxesMovingRefreshGeometryAndMarkHolderStale() {

            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForSnapshotChange(
                    takeSnapshot(1, "a", "hegemony"),
                    takeSnapshot(9, "a", "tritachyon")),
                2);

            assertThat(outcome.geometryRevision())
                .isEqualTo(1);
            assertThat(outcome.staleSystemKeys())
                .containsExactly(SYSTEM_A);
        }

        @Test
        void movingSetChangeRequestsGeometryRefreshWithoutMarkingHolderStale() {
            // Unmoved visibility and holding, but a system started or stopped moving:
            // it joins or leaves the partition, so the geometry counter advances and no
            // holder is reshaped.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForMovingSetChange(true),
                2);

            assertThat(outcome.geometryRevision())
                .isEqualTo(1);
            assertThat(outcome.staleSystemKeys())
                .isEmpty();
        }

        @Test
        void steadyMovingSetRequestsNoGeometryRefresh() {
            // The moving set is unchanged (a system that keeps moving is already
            // excluded, or nothing moves at all), so nothing rebuilds.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForMovingSetChange(false),
                2);

            assertThat(outcome.geometryRevision())
                .isZero();
            assertThat(outcome.staleSystemKeys())
                .isEmpty();
        }

        @Test
        void allianceFingerprintChangeRequestsAllianceRefreshOnly() {
            // An alliance formed, dissolved, or changed members: the fingerprint moved, so
            // the alliance revision bumps while the geometry and holding axes stay put.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForAllianceChange(11, 22),
                2);

            assertThat(outcome.allianceRevision())
                .isEqualTo(1);
            assertThat(outcome.geometryRevision())
                .isZero();
            assertThat(outcome.staleSystemKeys())
                .isEmpty();
        }

        @Test
        void steadyAllianceFingerprintRequestsNoAllianceRefresh() {
            // The alliance set is unchanged (or Nex is absent, where the fingerprint is a
            // fixed value), so the alliance revision never advances.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForAllianceChange(11, 11),
                2);

            assertThat(outcome.allianceRevision())
                .isZero();
        }

        @Test
        void firstPollSeedsTheAllianceBaselineWithoutBumping() {
            // The first poll only records the fingerprint; there is no prior to diff against,
            // so it never bumps the alliance revision.
            var outcome = pollThenReadRefreshOutcome(
                PollInputs.buildForAllianceChange(11, 22),
                1);

            assertThat(outcome.allianceRevision())
                .isZero();
        }

        @Test
        void writesNoObservationsTheSubstratesSweepOwns() {
            // The sweep that records what each system's own inhabitants can see used to ride this
            // poll. It is the substrate's now, the register being shared by every map family - so
            // a layer left writing it too would double the write, and would go on deciding when
            // the record accrued from behind a preference about which map is on screen.
            try (var sightingsMock = mockStatic(SectorColonySightings.class)) {

                pollThenReadRefreshOutcome(
                    PollInputs.buildForSnapshotChange(STEADY_SNAPSHOT, STEADY_SNAPSHOT),
                    2);

                // The write alone, not every reach: this poll still opens the register to read
                // it, the visibility rule its pass carries being answered against what has been
                // observed. What moved is who writes it.
                sightingsMock.verify(
                    () -> SectorColonySightings.recordSightingsByInhabitants(
                        any(),
                        any(),
                        any(),
                        any()),
                    never());
            }
        }

        @Test
        void marksOnTheBoardOfTheMachineryItWasBuiltWith() {
            // The other half of the same claim, over the board rather than the sector. A holder
            // flip in one sector must reach only that sector's cache: marked on a board two
            // sectors read, it would re-shape a cell in the other under a key nothing forbids both
            // from minting - and would do it invisibly, the re-shape being correct in every
            // respect but which map it happened on.
            var holderFlip = PollInputs.buildForSnapshotChange(
                takeSnapshot(1, "a", "hegemony"),
                takeSnapshot(1, "a", "tritachyon"));

            var otherMachinery = buildMachineryPolledUnder(holderFlip);

            var outcome = pollThenReadRefreshOutcome(
                holderFlip,
                2,
                buildMachineryPolledUnder(holderFlip));

            assertThat(outcome.staleSystemKeys())
                .containsExactly(SYSTEM_A);
            assertThat(otherMachinery.resolveRefreshBoard().drainStaleGroupingSystemKeys())
                .isEmpty();
        }

        @Test
        void pollsTheSectorItsMachineryWasMadeForRatherThanTheRunningOne() {
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
                StubbedGlobalLogger.answerLoggersOn(globalMock);

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
                            .sectorIndex()
                            .getSector());
                        return STEADY_SNAPSHOT;
                    });

                new PoliticalMapStalenessSource(new SectorMapMachinery(installedSector))
                    .markChangesSinceLastPoll();
            }
            assertThat(polledSector)
                .hasValue(installedSector);
        }
    }

    // One empty star system and nothing else, so a poll opened over this sector reaches the pass
    // it is meant to be read through. Nothing is staged in it: the snapshot scan and the motion
    // walk are both stubbed away, and what a poll decides off them is stated by each case.
    private static SectorAPI buildOneSystemSector() {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(mock(StarSystemAPI.class)));

        return sectorMock;
    }

    // A snapshot naming one held system by ID, keyed as the scan keys a system stating no centre
    // and no anchor.
    private static PoliticalMapSectorSnapshot takeSnapshot(
            int visibilityFingerprint,
            String systemId,
            String factionId) {

        return takeSnapshot(visibilityFingerprint, Map.of(buildCellKey(systemId), factionId));
    }

    private static PoliticalMapSectorSnapshot takeSnapshot(
            int visibilityFingerprint,
            Map<SystemKey, String> ownerBySystemKey) {

        return new PoliticalMapSectorSnapshot(visibilityFingerprint, ownerBySystemKey);
    }

    // The machinery one run polls through: a bare one-system sector - enough for the poll's
    // passengers to be handed something, with the two that read it stubbed away - a board of its
    // own, and a motion tracker reporting the run's stated moving-set answer.
    //
    // The tracker is always stubbed rather than driven with real positions, so every run reports
    // its moving set explicitly and none of them depends on the motion-detection math. The
    // machinery is the one collaborator this suite hands the source, and all three of those come
    // off it, the running game answering the source nothing.
    private static SectorMapMachinery buildMachineryPolledUnder(PollInputs inputs) {

        // Both collaborators are finished before either is handed over, so Mockito never sees one
        // stubbing opened inside another - which is what building the sector inside the sector
        // stub's own thenReturn would be.
        var sector = buildOneSystemSector();
        var movingSystemsMock = mock(MovingSystems.class);

        when(movingSystemsMock.updateMovingSystems(any(MapVisibilityPass.class)))
            .thenReturn(false, inputs.hasMovingSetChangedOnSecondPoll());

        var machineryMock = mock(SectorMapMachinery.class);

        when(machineryMock.resolveMovingSystems())
            .thenReturn(movingSystemsMock);
        when(machineryMock.resolveSector())
            .thenReturn(sector);
        when(machineryMock.resolveRefreshBoard())
            .thenReturn(new MapLayerRefreshBoard());

        return machineryMock;
    }

    // Polls a run's own machinery pollCount times against its stubbed reads, and reports what its
    // board was left holding. Global is stubbed to a no-op logger and nothing else.
    private static RefreshOutcome pollThenReadRefreshOutcome(PollInputs inputs, int pollCount) {
        return pollThenReadRefreshOutcome(
            inputs,
            pollCount,
            buildMachineryPolledUnder(inputs));
    }

    // The same run against a stated machinery, for the case that needs a second one beside it to
    // stay untouched.
    private static RefreshOutcome pollThenReadRefreshOutcome(
            PollInputs inputs,
            int pollCount,
            SectorMapMachinery machinery) {

        try (var globalMock = mockStatic(Global.class);
                var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                var snapshotMock = mockStatic(PoliticalMapSectorSnapshot.class);
                var alliancesMock = mockStatic(NexerelinAlliances.class)) {

            // Only the logger. The sector lookup is left unstubbed on purpose: a poll reads its
            // machinery's sector, so a run that only passed because the running game answered
            // with one would fail here rather than read as a case about routing.
            StubbedGlobalLogger.answerLoggersOn(globalMock);

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

            return runPollsAndReadOutcome(machinery, pollCount);
        }
    }

    // Polls the source pollCount times and reports what its own machinery's board was left
    // holding. Split from the stubbing so the try-with-resources block above reads as configuration
    // alone. The counters are read straight rather than as deltas: a board belongs to the
    // machinery the source was built against and is made fresh with it, so a run cannot see
    // another's marks and has nothing to isolate itself from.
    private static RefreshOutcome runPollsAndReadOutcome(
            SectorMapMachinery machinery,
            int pollCount) {

        var board = machinery.resolveRefreshBoard();
        var stalenessSource = new PoliticalMapStalenessSource(machinery);

        for (var poll = 0; poll < pollCount; poll++) {
            stalenessSource.markChangesSinceLastPoll();
        }
        return new RefreshOutcome(
            board.getRevision(MapLayerCommonRefreshSignal.GEOMETRY),
            board.getRevision(PoliticalMapRefreshSignal.ALLIANCES),
            board.drainStaleGroupingSystemKeys());
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
        int geometryRevision,
        int allianceRevision,
        Set<SystemKey> staleSystemKeys) {
    }
}
