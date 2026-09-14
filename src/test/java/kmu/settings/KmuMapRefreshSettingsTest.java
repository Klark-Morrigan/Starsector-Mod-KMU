package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one thing this reader does with what was stored rather than hand it straight back: the
 * clamp that holds the poll cadence inside the range the framework is willing to run.
 *
 * <p>Both ends are pinned because both answer a value the slider cannot produce - a settings file
 * edited by hand, or a value left behind by an earlier spelling of the row, which LunaLib never
 * prunes and hands to whatever later reads that ID. Neither is something a player can see the cause
 * of: a cadence of zero reads the whole sector on the campaign thread every frame, and one far past
 * the ceiling leaves changes off the map for minutes with nothing saying they are coming.
 */
final class KmuMapRefreshSettingsTest {

    // The live field ID, as the shipped table spells it. A rename in the reader would otherwise read
    // a row the table never writes, leaving the polls on their fallback with nothing to say why.
    private static final String POLL_SECONDS_FIELD = "kmu_map_dev_refresh_pollSeconds";

    @Nested
    class GetMapRefreshPollSeconds {

        @Test
        void getMapRefreshPollSecondsHandsBackACadenceInsideTheRange() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(POLL_SECONDS_FIELD), anyInt()))
                    .thenReturn(12);

                assertThat(KmuMapRefreshSettings.getMapRefreshPollSeconds())
                    .isEqualTo(12);
            }
        }

        @Test
        void getMapRefreshPollSecondsLiftsACadenceUnderTheFloor() {
            // Zero is the value the floor exists for: it polls every frame on the campaign thread,
            // which turns a diagnostics row into a frame-cost hazard.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(POLL_SECONDS_FIELD), anyInt()))
                    .thenReturn(0);

                assertThat(KmuMapRefreshSettings.getMapRefreshPollSeconds())
                    .isEqualTo(1);
            }
        }

        @Test
        void getMapRefreshPollSecondsHoldsACadenceOverTheCeiling() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(POLL_SECONDS_FIELD), anyInt()))
                    .thenReturn(600);

                assertThat(KmuMapRefreshSettings.getMapRefreshPollSeconds())
                    .isEqualTo(30);
            }
        }

        @Test
        void getMapRefreshPollSecondsClampsItsOwnFallbackWhileNothingIsStored() {
            // Stood in as a substrate answering with whatever fallback it is handed, which is what
            // an unset row - and a read taken outside a running game - gets. The fallback passes the
            // clamp untouched, so the polls before the settings load run at the shipped cadence.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(anyString(), anyInt()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

                assertThat(KmuMapRefreshSettings.getMapRefreshPollSeconds())
                    .isEqualTo(4);
            }
        }
    }
}
