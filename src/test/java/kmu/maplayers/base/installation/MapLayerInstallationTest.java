package kmu.maplayers.base.installation;

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

/**
 * Pins what an installation holds: a refresh board, a motion tracker and a hover holder of its own,
 * so that what went stale in one sector is not what any other sector rebuilds for, that a system's
 * drift is judged against where its own sector last saw it, and that a cursor read over one sector's
 * map is not reported over another's. Beside those sits what a layer hands it to hold - one per
 * kind per sector, released when the installation is.
 *
 * <p>Built here rather than resolved through {@link MapLayerInstallations}, since the claim is
 * about the holder itself and not about the index that hands one out.
 *
 * <p>One case runs on several threads, because one of these claims is only true under contention:
 * an installation is written from the campaign thread and read from the render thread, so a
 * make-if-absent has to be one step rather than a read and a write that read like one.
 */
class MapLayerInstallationTest {

    // The id both staged sectors give their system. Nothing forbids two sectors generating a
    // system under one id, and it is the case a shared holder gets wrong rather than merely
    // draws twice.
    private static final String SHARED_SYSTEM_ID = "a";

    // How many asks meet inside one resolution. Several threads rather than two, so the window a
    // read-then-write make-if-absent leaves open is entered from more than one side at once.
    private static final int CONTENDING_ASKS = 8;

    // Long enough that a loaded build machine is not what fails the case, short enough that a
    // resolution that deadlocked reports it rather than hanging the suite.
    private static final int ASK_TIMEOUT_SECONDS = 10;

    // How long one make is held open, so every asker is inside the resolution while it runs. Paid
    // once per case, since only a make waits.
    private static final int MAKE_PAUSE_MILLIS = 50;

    private final MapLayerInstallation installation = new MapLayerInstallation(null);
    private final MapLayerInstallation otherInstallation = new MapLayerInstallation(null);

    @Nested
    class ResolveHoverState {

        @Test
        void yieldsTheOneHolderThisInstallationKeeps() {
            // The render pass publishes into it and the highlight and the box read it out, each
            // resolving separately - so two resolutions handing back two holders would leave both
            // readers reporting a hover nothing ever published.
            assertThat(installation.resolveHoverState())
                .isSameAs(installation.resolveHoverState());
        }

        @Test
        void yieldsAHolderOfItsOwnSoOneSectorsHoverIsNotAnothers() {
            // A hover names its system by bare id, so a shared holder would have a cursor read over
            // one sector's map light the cell of whatever holds that id on the other's - and name
            // that system in the other's box.
            installation
                .resolveHoverState()
                .publishHover(new MapHover(SHARED_SYSTEM_ID, List.of(SHARED_SYSTEM_ID)));

            assertThat(otherInstallation.resolveHoverState().getHover())
                .isSameAs(MapHover.NONE);
            assertThat(installation.resolveHoverState().getHover().hoveredSystemId())
                .isEqualTo(SHARED_SYSTEM_ID);
        }
    }

    @Nested
    class ResolveMachinery {

        @Test
        void yieldsTheOneOfThatKindThisInstallationKeeps() {
            // The surface resolves what draws every frame and the hover box resolves it again in
            // the pass after, so two resolutions handing back two would have the box describing
            // draw lists the map never painted - and would double every cache behind them.
            assertThat(resolveCountingMachineryIn(installation))
                .isSameAs(resolveCountingMachineryIn(installation));
        }

        @Test
        void yieldsOneOfItsOwnSoOneSectorsDrawingIsNotAnothers() {
            // The whole of why a renderer stopped being the layer's: the caches behind it reconcile
            // by system id, so two sectors through one would keep each other's cells rather than
            // overwrite them.
            assertThat(resolveCountingMachineryIn(installation))
                .isNotSameAs(resolveCountingMachineryIn(otherInstallation));
        }

        @Test
        void makesOneOfAKindHoweverManyAsksArriveAtOnce()
                throws InterruptedException {
            // The map surfaces resolve what they are about to draw on the render thread while the
            // campaign thread installs, so several asks can reach a kind this installation has none
            // of yet. Making one each would hand one sector two renderers, and release only the one
            // the map went on to keep - leaking the other's GL buffers with nothing left holding it.
            //
            // Contended rather than sequential because a make-if-absent written as a read followed
            // by a write passes every sequential case and fails this one.
            var creationCount = new AtomicInteger();
            var resolved = ConcurrentHashMap.<CountingMachineryFake>newKeySet();

            runContendedAsks(() -> resolved.add(installation.resolveMachinery(
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
        void yieldsTheOneTrackerThisInstallationKeeps() {
            // The poll observes into it and the geometry cache reads the movers out of it, each
            // resolving separately - so two resolutions handing back two trackers would leave the
            // cache reading a set nothing ever wrote.
            assertThat(installation.resolveMovingSystems())
                .isSameAs(installation.resolveMovingSystems());
        }

        @Test
        void yieldsATrackerOfItsOwnSoOneSectorsDriftIsNotJudgedAgainstAnothers() {
            // An observation is keyed by system id. Sharing a tracker, the second sector's system
            // would be measured against the position the first sector's system of that id was last
            // seen at - so a system that never moved reads as drifting, and one that did reads as
            // still.
            var sectorFake = new MovableSystemSectorFake(SHARED_SYSTEM_ID);
            var otherSectorFake = new MovableSystemSectorFake(SHARED_SYSTEM_ID);
            var movingSystems = installation.resolveMovingSystems();
            var otherMovingSystems = otherInstallation.resolveMovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);

            // The other sector's system sits exactly where it started and never moves.
            otherSectorFake.observePositionsInto(otherMovingSystems, FORCED_ONTO_MAP);
            otherSectorFake.observePositionsInto(otherMovingSystems, FORCED_ONTO_MAP);

            assertThat(movingSystems.getMovingSystemIds())
                .containsExactly(SHARED_SYSTEM_ID);
            assertThat(otherMovingSystems.getMovingSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class ResolveRefreshBoard {

        @Test
        void yieldsTheOneBoardThisInstallationKeeps() {
            // A producer and a consumer resolve the board separately, so two resolutions handing
            // back two boards would have every raise land where nothing reads it.
            assertThat(installation.resolveRefreshBoard())
                .isSameAs(installation.resolveRefreshBoard());
        }

        @Test
        void yieldsABoardOfItsOwnSoOneSectorsStaleSystemsAreNotAnothers() {
            // The stale set is bare system ids, and nothing forbids two sectors from generating a
            // system under the same one - so a shared board is where two sectors corrupt each
            // other silently rather than merely draw each other's picture.
            installation.resolveRefreshBoard().markSystemGroupingStale("sys");

            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
            assertThat(installation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void yieldsABoardOfItsOwnSoOneSectorsRevisionIsNotAnothers() {
            // Shared counters would leave neither sector able to be stale on its own: either
            // sector's change would rebuild both.
            installation.resolveRefreshBoard().requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(otherInstallation
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isZero();

            assertThat(installation
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
            var machineryFake = resolveCountingMachineryIn(installation);

            installation.disposeMachinery();

            assertThat(machineryFake.getDisposeCount())
                .isEqualTo(1);
        }

        @Test
        void releasesOnlyItsOwn() {
            // Removing one sector's layers leaves the other sector drawing, so its renderer - and
            // every GL resource behind it - has to survive the release beside it.
            var machineryFake = resolveCountingMachineryIn(installation);
            var otherMachineryFake = resolveCountingMachineryIn(otherInstallation);

            installation.disposeMachinery();

            assertThat(machineryFake.getDisposeCount())
                .isEqualTo(1);
            assertThat(otherMachineryFake.getDisposeCount())
                .isZero();
        }

        @Test
        void isSafeWithNothingHandedToIt() {
            // A sector installed on with the map never opened, and the detached installation every
            // sector-less caller shares: both reach disposal holding nothing.
            installation.disposeMachinery();

            assertThat(installation.isDisposed())
                .isTrue();
        }
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
    // installation it is asking rather than as the pair of arguments it is asking with.
    private static CountingMachineryFake resolveCountingMachineryIn(
            MapLayerInstallation installation) {

        return installation.resolveMachinery(
            CountingMachineryFake.class,
            CountingMachineryFake::new);
    }

    // A layer's machinery, stated as the plainest thing that can be held and released: what an
    // installation owes one is a lifetime, and a real renderer would drag a cache and a live screen
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
