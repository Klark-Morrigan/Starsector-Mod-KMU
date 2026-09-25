package kmu.maplayers.base.refresh;

import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins a layer's share of the engine's script list: it never retires, it does not run while the
 * game is paused, and every frame it is advanced reaches the poll. The throttle, the fault guard
 * and what a fault costs are {@link StalenessPollLoop}'s to pin.
 *
 * <p>Asked of a layer's subclass rather than of the base that answers for it, since a watcher exists
 * to be a class the engine can install and clear on its own: the three answers are only worth
 * anything reached through the identity that carries them.
 */
final class MapLayerSectorWatcherTest {

    // Comfortably past the poll interval, so one advance elapses it and the source is reached.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;

    @Nested
    class IsDone {

        @Test
        void neverFinishesSoTheEngineKeepsPollingForTheLifeOfTheSave() {
            // The engine drops a script that reports done, and nothing reinstalls one before
            // the next load - so this answering true would silently stop live refresh for the
            // rest of the save, with no crash to point at it.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var watcherFake = new LayerSectorWatcherFake(mock(MapLayerStalenessSource.class));

                assertThat(watcherFake.isDone())
                    .isFalse();
            }
        }
    }

    @Nested
    class RunWhilePaused {

        @Test
        void doesNotPollWhileTheGameIsPaused() {
            // Nothing the poll watches for can happen while the game is paused, so polling
            // then would only spend a sector walk on the campaign thread to find no change.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var watcherFake = new LayerSectorWatcherFake(mock(MapLayerStalenessSource.class));

                assertThat(watcherFake.runWhilePaused())
                    .isFalse();
            }
        }
    }

    @Nested
    class Advance {

        @Test
        void handsTheEnginesFrameToTheLayersPoll() {
            // The whole of what this class does with a frame. A watcher that kept the amount
            // to itself would never elapse an interval, and the layer would refresh only on
            // reload with nothing on screen to say so.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new LayerSectorWatcherFake(stalenessSourceMock)
                    .advance(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }
    }

    // A layer's own watcher, as every layer declares one: the class is abstract so that no two
    // layers can share a script identity, which leaves a subclass the only thing there is to ask.
    private static final class LayerSectorWatcherFake extends MapLayerSectorWatcher {

        private LayerSectorWatcherFake(MapLayerStalenessSource stalenessSource) {
            super(stalenessSource);
        }
    }
}
