package kmu.maplayers.base.refresh;

import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapRefreshSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pins the cadence every staleness poll runs on, wherever it is driven from: the source is asked
 * once per elapsed interval and never in between, and a source that faults neither escapes into
 * the engine's per-frame path nor stops the next poll. What the source then marks stale is its own
 * test's subject.
 *
 * <p>The cases that settle no setting are the unreadable-setting case as well as the cadence one:
 * nothing here runs inside a game, so the reader answers with its own fallback and the loop polls on
 * the shipped window. Only the two retune cases stand the reader in, and they are the ones about a
 * player having moved the row.
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

    // The shipped cadence, stood in so a retune has something to move away from.
    private static final int SHIPPED_POLL_SECONDS = 4;
    // Far enough past the shipped window that one advance can sit between the two: past the shipped
    // ceiling, so the old cadence would have polled, and short of this one's floor.
    private static final int RETUNED_POLL_SECONDS = 20;
    // Past the retuned ceiling of 25s, so an advance of this size elapses the retuned interval
    // whatever the jitter drew.
    private static final float ADVANCE_PAST_RETUNED_INTERVAL = 30f;

    private static final int UNMOVED_SETTINGS_REVISION = 0;
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
        void installsARetunedCadenceOnTheNextSettingsRevision() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers();
                 var lunaSettingsMock = mockStatic(KmuLunaSettings.class);
                 var refreshSettingsMock = mockStatic(KmuMapRefreshSettings.class)) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);
                var pollLoop = buildPollLoopOnTheShippedCadence(
                    lunaSettingsMock,
                    refreshSettingsMock,
                    stalenessSourceMock);

                stubSettings(
                    lunaSettingsMock,
                    refreshSettingsMock,
                    MOVED_SETTINGS_REVISION,
                    RETUNED_POLL_SECONDS);

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
                 var lunaSettingsMock = mockStatic(KmuLunaSettings.class);
                 var refreshSettingsMock = mockStatic(KmuMapRefreshSettings.class)) {

                var stalenessSourceMock = mock(MapLayerStalenessSource.class);
                var pollLoop = buildPollLoopOnTheShippedCadence(
                    lunaSettingsMock,
                    refreshSettingsMock,
                    stalenessSourceMock);

                pollLoop.advancePoll(ADVANCE_HALF_PAST_POLL_INTERVAL);

                stubSettings(
                    lunaSettingsMock,
                    refreshSettingsMock,
                    MOVED_SETTINGS_REVISION,
                    SHIPPED_POLL_SECONDS);

                pollLoop.advancePoll(ADVANCE_HALF_PAST_POLL_INTERVAL);

                verify(stalenessSourceMock)
                    .markChangesSinceLastPoll();
            }
        }
    }

    // A loop built while the reader answers the shipped cadence, so a case about a retune starts
    // from the window every other case here runs on.
    private static StalenessPollLoop buildPollLoopOnTheShippedCadence(
            MockedStatic<KmuLunaSettings> lunaSettingsMock,
            MockedStatic<KmuMapRefreshSettings> refreshSettingsMock,
            MapLayerStalenessSource stalenessSource) {

        stubSettings(
            lunaSettingsMock,
            refreshSettingsMock,
            UNMOVED_SETTINGS_REVISION,
            SHIPPED_POLL_SECONDS);

        return new StalenessPollLoop(stalenessSource);
    }

    // The two reads a retune is decided by, settled together: a revision on its own says nothing
    // until there is a cadence behind it to compare.
    private static void stubSettings(
            MockedStatic<KmuLunaSettings> lunaSettingsMock,
            MockedStatic<KmuMapRefreshSettings> refreshSettingsMock,
            int settingsRevision,
            int pollSeconds) {

        lunaSettingsMock
            .when(KmuLunaSettings::getSettingsRevision)
            .thenReturn(settingsRevision);
        refreshSettingsMock
            .when(KmuMapRefreshSettings::getMapRefreshPollSeconds)
            .thenReturn(pollSeconds);
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
