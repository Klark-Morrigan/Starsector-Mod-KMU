package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pins the watcher's loop, which is all it owns now that what counts as a change is the
 * layer's answer: it asks its source once per elapsed interval, never in between, and a
 * source that faults neither escapes into the engine's per-frame path nor stops the next
 * poll. What the source then marks stale is its own test's subject.
 */
final class MapLayerSectorWatcherTest {

    // Comfortably past the 4-5s poll interval, so each advance elapses it and drives
    // exactly one poll.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;
    // Short of the interval's lower bound, so no advance of this size can elapse it.
    private static final float ADVANCE_WITHIN_POLL_INTERVAL = 1f;

    @Nested
    class IsDone {

        @Test
        void neverFinishesSoTheEngineKeepsPollingForTheLifeOfTheSave() {
            // The engine drops a script that reports done, and nothing reinstalls one before
            // the next load - so this answering true would silently stop live refresh for the
            // rest of the save, with no crash to point at it.
            try (var globalMock = stubGlobalLogger()) {

                var watcher = new MapLayerSectorWatcher(mock(MapLayerStalenessSource.class));

                assertThat(watcher.isDone())
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
            try (var globalMock = stubGlobalLogger()) {

                var watcher = new MapLayerSectorWatcher(mock(MapLayerStalenessSource.class));

                assertThat(watcher.runWhilePaused())
                    .isFalse();
            }
        }
    }

    @Nested
    class Advance {

        @Test
        void pollsTheSourceOnceTheIntervalElapses() {

            try (var globalMock = stubGlobalLogger()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new MapLayerSectorWatcher(stalenessSourceMock)
                    .advance(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }

        @Test
        void doesNotPollBeforeTheIntervalElapses() {

            try (var globalMock = stubGlobalLogger()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new MapLayerSectorWatcher(stalenessSourceMock)
                    .advance(ADVANCE_WITHIN_POLL_INTERVAL);

                verifyNoInteractions(stalenessSourceMock);
            }
        }

        @Test
        void pollsOncePerElapsedInterval() {

            try (var globalMock = stubGlobalLogger()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);
                var watcher = new MapLayerSectorWatcher(stalenessSourceMock);

                watcher.advance(ADVANCE_PAST_POLL_INTERVAL);
                watcher.advance(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock, times(2))
                    .markChangesSinceLastPoll();
            }
        }

        @Test
        void swallowsAFaultingSourceRatherThanThrowingIntoTheEngine() {
            // This runs on the campaign thread every few seconds, so a fault escaping here
            // would surface as an engine-level crash rather than a missed refresh.
            try (var globalMock = stubGlobalLogger()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                doThrow(new IllegalStateException("malformed system"))
                    .when(stalenessSourceMock)
                    .markChangesSinceLastPoll();

                var watcher = new MapLayerSectorWatcher(stalenessSourceMock);

                assertThatCode(() -> watcher.advance(ADVANCE_PAST_POLL_INTERVAL))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void keepsPollingAfterTheSourceFaults() {
            // A transient fault (a half-built system mid-generation) must not silently
            // retire the watcher for the rest of the save.
            try (var globalMock = stubGlobalLogger()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                doThrow(new IllegalStateException("malformed system"))
                    .when(stalenessSourceMock)
                    .markChangesSinceLastPoll();

                var watcher = new MapLayerSectorWatcher(stalenessSourceMock);

                watcher.advance(ADVANCE_PAST_POLL_INTERVAL);
                watcher.advance(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock, times(2))
                    .markChangesSinceLastPoll();
            }
        }
    }

    // The watcher's logger is resolved through Global at class init, so every test opens
    // the static mock before touching the class and hands back a no-op logger.
    private static MockedStatic<Global> stubGlobalLogger() {

        var globalMock = mockStatic(Global.class);
        globalMock
            .when(() -> Global.getLogger(any(Class.class)))
            .thenReturn(mock(Logger.class));

        return globalMock;
    }
}
