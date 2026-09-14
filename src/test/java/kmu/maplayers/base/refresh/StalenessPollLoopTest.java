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
 *
 * <p>The cases that open no settings scope are the unreadable-setting case as well as the cadence
 * one: nothing here runs inside a game, so the reader answers with its own fallback and the loop
 * polls on the shipped window. Only the retune cases stand the reader in, and they are the ones
 * about a player having moved the row.
 */
final class StalenessPollLoopTest {

    // Comfortably past the 4-5s poll interval, so each advance elapses it and drives
    // exactly one poll.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;
    // Short of the interval's lower bound, so no advance of this size can elapse it.
    private static final float ADVANCE_WITHIN_POLL_INTERVAL = 1f;
    // Under the 4s floor, so no single advance of this size elapses the shipped interval, and past
    // half the 5s ceiling, so two of them do - which is what parts an interval left alone from one
    // a retune reset.
    private static final float ADVANCE_HALF_PAST_POLL_INTERVAL = 3f;

    // The shipped window's own two ends, which are the knob's default and the jitter ratio over it
    // and are stated as a fixed 4-5 seconds in the register and the notes. A hair under four cannot
    // elapse an interval floored at four; five always elapses one ceilinged at five.
    private static final float ADVANCE_UNDER_SHIPPED_FLOOR = 3.99f;
    private static final float ADVANCE_AT_SHIPPED_CEILING = 5f;

    // Far enough past the shipped window that one advance can sit between the two: past the shipped
    // ceiling, so the old cadence would have polled, and short of this one's floor.
    private static final int RETUNED_POLL_SECONDS = 20;
    // Past the retuned ceiling of 25s, so an advance of this size elapses the retuned interval
    // whatever the jitter drew.
    private static final float ADVANCE_PAST_RETUNED_INTERVAL = 30f;

    private static final int MOVED_SETTINGS_REVISION = 1;

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

        @Test
        void pollsNoSoonerThanTheShippedWindowsFloor() {
            // The window is stated as a fixed 4-5 seconds wherever it is written down, and it is the
            // shipped default times the jitter ratio rather than a constant any longer. This end and
            // the one below are what hold that ratio to the window it is said to produce.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new StalenessPollLoop(stalenessSourceMock)
                    .advancePoll(ADVANCE_UNDER_SHIPPED_FLOOR);

                verifyNoInteractions(stalenessSourceMock);
            }
        }

        @Test
        void pollsNoLaterThanTheShippedWindowsCeiling() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);

                new StalenessPollLoop(stalenessSourceMock)
                    .advancePoll(ADVANCE_AT_SHIPPED_CEILING);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }

        @Test
        void installsARetunedCadenceOnTheNextSettingsRevision() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers();
                 var settingsScope = RefreshSettingsScope.openOnTheShippedCadence()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);
                var pollLoop = new StalenessPollLoop(stalenessSourceMock);

                settingsScope.settleCadence(MOVED_SETTINGS_REVISION, RETUNED_POLL_SECONDS);

                // Past the cadence the loop was built on and short of the one it has just been
                // handed, so what this observes is which of the two is installed.
                pollLoop.advancePoll(ADVANCE_PAST_POLL_INTERVAL);

                verifyNoInteractions(stalenessSourceMock);

                pollLoop.advancePoll(ADVANCE_PAST_RETUNED_INTERVAL);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }

        @Test
        void leavesTheIntervalStandingWhereARevisionMovedTheCadenceNowhere() {
            // LunaLib announces that the settings changed rather than which one, so every KMU knob
            // the player moves reaches the loop. Re-applying an unchanged cadence would draw a fresh
            // interval and zero the elapsed time with it, so a poll part-way through its wait would
            // start over on every settings change the player made.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers();
                 var settingsScope = RefreshSettingsScope.openOnTheShippedCadence()) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);
                var pollLoop = new StalenessPollLoop(stalenessSourceMock);

                pollLoop.advancePoll(ADVANCE_HALF_PAST_POLL_INTERVAL);

                settingsScope.settleRevisionAtTheShippedCadence(MOVED_SETTINGS_REVISION);

                pollLoop.advancePoll(ADVANCE_HALF_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock)
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
