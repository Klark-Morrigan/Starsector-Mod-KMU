package kmu.maplayers.base.refresh;

import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pins the cadence every staleness poll runs on, wherever it is driven from: the source is asked
 * once per elapsed interval and never in between, and a source that faults neither escapes into
 * the engine's per-frame path nor stops the next poll. What the source then marks stale is its own
 * test's subject.
 */
final class StalenessPollLoopTest {

    // Comfortably past the 4-5s poll interval, so each advance elapses it and drives
    // exactly one poll.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;
    // Short of the interval's lower bound, so no advance of this size can elapse it.
    private static final float ADVANCE_WITHIN_POLL_INTERVAL = 1f;

    @Nested
    class AdvancePoll {

        @Test
        void pollsTheSourceOnceTheIntervalElapses() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new StalenessPollLoop(stalenessSourceMock)
                    .advancePoll(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }

        @Test
        void doesNotPollBeforeTheIntervalElapses() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new StalenessPollLoop(stalenessSourceMock)
                    .advancePoll(ADVANCE_WITHIN_POLL_INTERVAL);

                verifyNoInteractions(stalenessSourceMock);
            }
        }

        @Test
        void pollsOncePerElapsedInterval() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);
                var pollLoop = new StalenessPollLoop(stalenessSourceMock);

                pollLoop.advancePoll(ADVANCE_PAST_POLL_INTERVAL);
                pollLoop.advancePoll(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock, times(2))
                    .markChangesSinceLastPoll();
            }
        }

        @Test
        void swallowsAFaultingSourceRatherThanThrowingIntoTheEngine() {
            // This runs on the campaign thread every few seconds, so a fault escaping here
            // would surface as an engine-level crash rather than a missed refresh.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var pollLoop = new StalenessPollLoop(buildFaultingSource());

                assertThatCode(() -> pollLoop.advancePoll(ADVANCE_PAST_POLL_INTERVAL))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void keepsPollingAfterTheSourceFaults() {
            // A transient fault (a half-built system mid-generation) must not silently
            // retire the poll for the rest of the save.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = buildFaultingSource();
                var pollLoop = new StalenessPollLoop(stalenessSourceMock);

                pollLoop.advancePoll(ADVANCE_PAST_POLL_INTERVAL);
                pollLoop.advancePoll(ADVANCE_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock, times(2))
                    .markChangesSinceLastPoll();
            }
        }
    }

    // A source whose every poll throws, for the two cases about what a fault costs.
    private static MapLayerStalenessSource buildFaultingSource() {

        var stalenessSourceMock = mock(MapLayerStalenessSource.class);

        doThrow(new IllegalStateException("malformed system"))
            .when(stalenessSourceMock)
            .markChangesSinceLastPoll();

        return stalenessSourceMock;
    }
}
