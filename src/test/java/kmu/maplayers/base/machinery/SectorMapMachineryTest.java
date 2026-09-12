package kmu.maplayers.base.machinery;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;

import kmlib.profiling.ProfileOrigin;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MovableSystemSectorFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static kmu.maplayers.base.refresh.MovableSystemSectorFake.FORCED_ONTO_MAP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what machinery holds: a refresh board, a motion tracker and a hover holder of its own,
 * so that what went stale in one sector is not what any other sector rebuilds for, that a system's
 * drift is judged against where its own sector last saw it, and that a cursor read over one sector's
 * map is not reported over another's. Beside those sits what a layer hands it to hold - one per
 * kind per sector, released when the machinery is - and the profiling origin its rows are
 * grouped under, so a capture says which sector each beat was measured in.
 *
 * <p>Built here rather than resolved through {@link SectorMapMachineryIndex}, since the claim is
 * about the holder itself and not about the index that hands one out.
 *
 * <p>One case runs on several threads, because one of these claims is only true under contention:
 * machinery is written from the campaign thread and read from the render thread, so a
 * make-if-absent has to be one step rather than a read and a write that read like one.
 */
class SectorMapMachineryTest {

    // The id both staged sectors give their system. Nothing forbids two sectors generating a
    // system under one id, and it is the case a shared holder gets wrong rather than merely
    // draws twice.
    private static final String SHARED_SYSTEM_ID = "a";

    // The key that system carries in either sector: the id alone, a staged system stating no
    // centre and no anchor. Written out rather than read off the system, an expectation taken
    // from the code under test being no expectation at all.
    private static final SystemKey SHARED_SYSTEM_KEY = new SystemKey("a", "", "");

    // Two games one session could load in turn, and the character playing both - the seed is what
    // tells them apart, so it is what differs.
    private static final String SEED = "MN-6220";
    private static final String OTHER_SEED = "PQ-1183";
    private static final String PLAYER_NAME = "Marat";

    // How many asks meet inside one resolution. Several threads rather than two, so the window a
    // read-then-write make-if-absent leaves open is entered from more than one side at once.
    private static final int CONTENDING_ASKS = 8;

    // Long enough that a loaded build machine is not what fails the case, short enough that a
    // resolution that deadlocked reports it rather than hanging the suite.
    private static final int ASK_TIMEOUT_SECONDS = 10;

    // How long one make is held open, so every asker is inside the resolution while it runs. Paid
    // once per case, since only a make waits.
    private static final int MAKE_PAUSE_MILLIS = 50;

    private final SectorMapMachinery machinery = new SectorMapMachinery(null);
    private final SectorMapMachinery otherMachinery = new SectorMapMachinery(null);

    @Nested
    class ResolveHoverState {

        @Test
        void yieldsTheOneHolderThisMachineryKeeps() {
            // The render pass publishes into it and the highlight and the box read it out, each
            // resolving separately - so two resolutions handing back two holders would leave both
            // readers reporting a hover nothing ever published.
            assertThat(machinery.resolveHoverState())
                .isSameAs(machinery.resolveHoverState());
        }

        @Test
        void yieldsAHolderOfItsOwnSoOneSectorsHoverIsNotAnothers() {
            // A hover names its system by bare id, so a shared holder would have a cursor read over
            // one sector's map light the cell of whatever holds that id on the other's - and name
            // that system in the other's box.
            machinery
                .resolveHoverState()
                .publishHover(new MapHover(SHARED_SYSTEM_ID, List.of(SHARED_SYSTEM_ID)));

            assertThat(otherMachinery.resolveHoverState().getHover())
                .isSameAs(MapHover.NONE);
            assertThat(machinery.resolveHoverState().getHover().hoveredSystemId())
                .isEqualTo(SHARED_SYSTEM_ID);
        }
    }

    @Nested
    class ResolveMachinery {

        @Test
        void yieldsTheOneOfThatKindThisMachineryKeeps() {
            // The surface resolves what draws every frame and the hover box resolves it again in
            // the pass after, so two resolutions handing back two would have the box describing
            // draw lists the map never painted - and would double every cache behind them.
            assertThat(resolveCountingMachineryIn(machinery))
                .isSameAs(resolveCountingMachineryIn(machinery));
        }

        @Test
        void yieldsOneOfItsOwnSoOneSectorsDrawingIsNotAnothers() {
            // The whole of why a renderer stopped being the layer's: the caches behind it reconcile
            // by system id, so two sectors through one would keep each other's cells rather than
            // overwrite them.
            assertThat(resolveCountingMachineryIn(machinery))
                .isNotSameAs(resolveCountingMachineryIn(otherMachinery));
        }

        @Test
        void makesOneOfAKindHoweverManyAsksArriveAtOnce()
                throws InterruptedException {
            // The map surfaces resolve what they are about to draw on the render thread while the
            // campaign thread installs, so several asks can reach a kind this machinery has none
            // of yet. Making one each would hand one sector two renderers, and release only the one
            // the map went on to keep - leaking the other's GL buffers with nothing left holding it.
            //
            // Contended rather than sequential because a make-if-absent written as a read followed
            // by a write passes every sequential case and fails this one.
            var creationCount = new AtomicInteger();
            var resolved = ConcurrentHashMap.<CountingMachineryFake>newKeySet();

            runContendedAsks(() -> resolved.add(machinery.resolveMachinery(
                CountingMachineryFake.class,
                () -> {
                    creationCount.incrementAndGet();
                    pauseInsideTheMake();
                    return new CountingMachineryFake();
                })));

            // Two statements of the one claim, because either alone can hold while the other
            // breaks: one make, and every asker holding what that make produced.
            assertThat(creationCount)
                .hasValue(1);
            assertThat(resolved)
                .hasSize(1);
        }
    }

    @Nested
    class ResolveMovingSystems {

        @Test
        void yieldsTheOneTrackerThisMachineryKeeps() {
            // The poll observes into it and the geometry cache reads the movers out of it, each
            // resolving separately - so two resolutions handing back two trackers would leave the
            // cache reading a set nothing ever wrote.
            assertThat(machinery.resolveMovingSystems())
                .isSameAs(machinery.resolveMovingSystems());
        }

        @Test
        void yieldsATrackerOfItsOwnSoOneSectorsDriftIsNotJudgedAgainstAnothers() {
            // An observation is keyed by system key, which one sector mints without regard to
            // another's. Sharing a tracker, the second sector's system would be measured against
            // the position the first sector's system of that key was last seen at - so a system
            // that never moved reads as drifting, and one that did reads as still.
            var sectorFake = new MovableSystemSectorFake(SHARED_SYSTEM_ID);
            var otherSectorFake = new MovableSystemSectorFake(SHARED_SYSTEM_ID);
            var movingSystems = machinery.resolveMovingSystems();
            var otherMovingSystems = otherMachinery.resolveMovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);

            // The other sector's system sits exactly where it started and never moves.
            otherSectorFake.observePositionsInto(otherMovingSystems, FORCED_ONTO_MAP);
            otherSectorFake.observePositionsInto(otherMovingSystems, FORCED_ONTO_MAP);

            assertThat(movingSystems.getMovingSystemKeys())
                .containsExactly(SHARED_SYSTEM_KEY);
            assertThat(otherMovingSystems.getMovingSystemKeys())
                .isEmpty();
        }
    }

    @Nested
    class ResolveProfilingOrigin {

        @Test
        void composesTheLabelFromWhatTheSectorIsRecognisedBy() {
            // The pair a save browser shows, since a reader who cannot take a slow row back to a
            // save cannot go and reproduce it.
            var machineryOnSector = new SectorMapMachinery(mockSectorSeeded(SEED));

            assertThat(machineryOnSector.resolveProfilingOrigin().getLabel())
                .isEqualTo("MN-6220 - Marat");
        }

        @Test
        void yieldsAnOriginOfItsOwnSoOneSectorsRowsAreNotAnothers() {
            // Two sectors through one origin would put both sets of beats in one group of rows,
            // which is the state a capture exists to tell apart.
            var machineryOnSector = new SectorMapMachinery(mockSectorSeeded(SEED));
            var machineryOnOtherSector =
                new SectorMapMachinery(mockSectorSeeded(OTHER_SEED));

            assertThat(machineryOnSector.resolveProfilingOrigin())
                .isNotSameAs(machineryOnOtherSector.resolveProfilingOrigin());
        }

        @Test
        void leavesTheDetachedMachinerySpansUnattributed() {
            // Nobody's sector, so there is nothing to describe and nothing a reader could match a
            // row back to - which is exactly what the reserved origin says.
            assertThat(machinery.resolveProfilingOrigin())
                .isSameAs(ProfileOrigin.UNSCOPED);
        }
    }

    @Nested
    class ResolveRefreshBoard {

        @Test
        void yieldsTheOneBoardThisMachineryKeeps() {
            // A producer and a consumer resolve the board separately, so two resolutions handing
            // back two boards would have every raise land where nothing reads it.
            assertThat(machinery.resolveRefreshBoard())
                .isSameAs(machinery.resolveRefreshBoard());
        }

        @Test
        void yieldsABoardOfItsOwnSoOneSectorsStaleSystemsAreNotAnothers() {
            // The stale set is bare system ids, and nothing forbids two sectors from generating a
            // system under the same one - so a shared board is where two sectors corrupt each
            // other silently rather than merely draw each other's picture.
            machinery.resolveRefreshBoard().markSystemGroupingStale("sys");

            assertThat(otherMachinery.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
            assertThat(machinery.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void yieldsABoardOfItsOwnSoOneSectorsRevisionIsNotAnothers() {
            // Shared counters would leave neither sector able to be stale on its own: either
            // sector's change would rebuild both.
            machinery.resolveRefreshBoard().requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(otherMachinery
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isZero();

            assertThat(machinery
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isEqualTo(1);
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void releasesWhatALayerHandedItToHold() {
            // What the release contract is for: the political map's draw lists own a GL buffer per
            // cached name, so a sector removed mid-session leaks every one it built unless disposal
            // reaches through to them.
            var machineryFake = resolveCountingMachineryIn(machinery);

            machinery.disposeMachinery();

            assertThat(machineryFake.getDisposeCount())
                .isEqualTo(1);
        }

        @Test
        void releasesOnlyItsOwn() {
            // Removing one sector's layers leaves the other sector drawing, so its renderer - and
            // every GL resource behind it - has to survive the release beside it.
            var machineryFake = resolveCountingMachineryIn(machinery);
            var otherMachineryFake = resolveCountingMachineryIn(otherMachinery);

            machinery.disposeMachinery();

            assertThat(machineryFake.getDisposeCount())
                .isEqualTo(1);
            assertThat(otherMachineryFake.getDisposeCount())
                .isZero();
        }

        @Test
        void isSafeWithNothingHandedToIt() {
            // A sector installed on with the map never opened, and the detached machinery every
            // sector-less caller shares: both reach disposal holding nothing.
            machinery.disposeMachinery();

            assertThat(machinery.isDisposed())
                .isTrue();
        }
    }

    // A sector carrying the two facts a save browser shows about it, which is all the origin's
    // label is composed from.
    private static SectorAPI mockSectorSeeded(String seed) {

        var sectorMock = mock(SectorAPI.class);
        var playerMock = mock(PersonAPI.class);

        when(playerMock.getNameString()).thenReturn(PLAYER_NAME);
        when(sectorMock.getSeedString()).thenReturn(seed);
        when(sectorMock.getPlayerPerson()).thenReturn(playerMock);
        return sectorMock;
    }

    // Holds the make open for a moment, which is what a real one does: a renderer builds a cover
    // reader and an empty cache before it can be handed back. Without it the first asker usually
    // finishes making before the rest are even scheduled, and a make-if-absent written as a read
    // then a write slips through this case about half the time - so the pause is what gives the
    // window the width it has in play rather than the width a stand-in happens to leave.
    private static void pauseInsideTheMake() {

        try {
            Thread.sleep(MAKE_PAUSE_MILLIS);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }

    // Runs one ask on several threads released together, so they meet inside the resolution rather
    // than queue behind each other, and returns once every one of them is done. Failing the wait
    // rather than reading the counters early, since a case that asserted over asks still running
    // could report one make where the second had simply not happened yet.
    private static void runContendedAsks(Runnable ask)
            throws InterruptedException {

        var executor = Executors.newFixedThreadPool(CONTENDING_ASKS);
        var startSignal = new CountDownLatch(1);
        var finishedAsks = new CountDownLatch(CONTENDING_ASKS);

        try {
            for (var askIndex = 0; askIndex < CONTENDING_ASKS; askIndex++) {
                executor.execute(() -> {
                    try {
                        startSignal.await();
                        ask.run();
                    } catch (InterruptedException interruption) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishedAsks.countDown();
                    }
                });
            }
            startSignal.countDown();

            assertThat(finishedAsks.await(ASK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();

        } finally {
            executor.shutdownNow();
        }
    }

    // The one kind of machinery these cases stage, asked for the way a layer asks: by its class,
    // with the way to make one. Named rather than repeated at each call so a case reads as which
    // machinery it is asking rather than as the pair of arguments it is asking with.
    private static CountingMachineryFake resolveCountingMachineryIn(
            SectorMapMachinery machinery) {

        return machinery.resolveMachinery(
            CountingMachineryFake.class,
            CountingMachineryFake::new);
    }

    // A layer's machinery, stated as the plainest thing that can be held and released: what an
    // machinery owes one is a lifetime, and a real renderer would drag a cache and a live screen
    // read in to say the same thing.
    private static final class CountingMachineryFake implements InstalledMachinery {

        private int disposeCount;

        @Override
        public void disposeMachinery() {
            disposeCount++;
        }

        int getDisposeCount() {
            return disposeCount;
        }
    }
}
