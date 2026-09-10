package kmu.maplayers.base.refresh;

import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins the substrate's share of the engine's script list: it never retires, it does not sweep
 * while the game is paused, and every frame it is advanced reaches the poll. Why it is a class of
 * its own rather than a second {@link MapLayerSectorWatcher} shows where a layer is taken back -
 * {@code PoliticalMapInstallerTest} - and the loop itself is {@link StalenessPollLoop}'s to pin.
 */
final class MapSubstrateSectorWatcherTest {

    // Comfortably past the poll interval, so one advance elapses it and the source is reached.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;

    @Nested
    class IsDone {

        @Test
        void neverFinishesSoTheSweepRunsForTheLifeOfTheSave() {
            // The engine drops a script that reports done, and nothing reinstalls one before the
            // next load - so this answering true would stop the shared record accruing for the
            // rest of the save, and the gap would only show cycles later.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var watcher = new MapSubstrateSectorWatcher(mock(MapLayerStalenessSource.class));

                assertThat(watcher.isDone())
                    .isFalse();
            }
        }
    }

    @Nested
    class RunWhilePaused {

        @Test
        void doesNotSweepWhileTheGameIsPaused() {
            // No colony arrives among witnesses while the game is paused, so sweeping then
            // would only spend a sector walk on the campaign thread to record what is already
            // recorded.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var watcher = new MapSubstrateSectorWatcher(mock(MapLayerStalenessSource.class));

                assertThat(watcher.runWhilePaused())
                    .isFalse();
            }
        }
    }

    @Nested
    class Advance {

        @Test
        void handsTheEnginesFrameToTheSubstratesPoll() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new MapSubstrateSectorWatcher(stalenessSourceMock)
                    .advance(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }
    }
}
