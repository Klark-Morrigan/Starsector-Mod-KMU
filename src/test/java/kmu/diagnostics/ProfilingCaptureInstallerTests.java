package kmu.diagnostics;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileLevel;
import kmlib.profiling.Profiler;
import kmlib.profiling.SilentProfiler;

import kmu.maplayers.base.profiling.MapFrameBudgets;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuProfilingSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins which profiler a level asks for: off is the silent one, anything else a recording one
 * keeping detail to that level, and a level that has not moved rebinds nothing - which is what
 * stops an unrelated settings change throwing a running capture away.
 *
 * <p>And that the install binds the level the settings hold, follows that level when the player
 * moves it, and hands the framework the frame bound the settings hold.
 */
final class ProfilingCaptureInstallerTests {

    // The bound the framework holds until something registers one: none.
    private static final double NO_BOUND_MILLIS = 0d;

    @AfterEach
    void restoreTheSilentProfilerAndNoFrameBound() {
        // Both holders are process-wide, so a case that bound a recording profiler or a frame bound
        // would otherwise leave every later case measuring into it or judged by it.
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
        MapFrameBudgets.registerFrameBeatBudget(() -> NO_BOUND_MILLIS);
    }

    @Nested
    class BindProfilerKeeping {

        @Test
        void bindsTheSilentProfilerForOff() {
            // The silent one rather than a recording profiler bound at off, so nothing is
            // allocated and no scope is pushed on the paths a frame runs through.
            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.COARSE);
            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.OFF);

            assertThat(ActiveProfiler.resolveProfiler())
                .isSameAs(SilentProfiler.INSTANCE);
        }

        @Test
        void bindsARecordingProfilerKeepingTheLevelAskedFor() {

            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.COARSE);

            assertThat(ActiveProfiler.resolveProfiler().getRecordedLevel())
                .isEqualTo(ProfileLevel.COARSE);
        }

        @Test
        void rebindsNothingWhereTheLevelHasNotMoved() {
            // A rebind starts a fresh capture, and LunaLib announces that the settings changed
            // rather than which setting did - so acting on every announcement would discard the
            // capture whenever an unrelated slider moved.
            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.FINE);

            var boundProfiler = ActiveProfiler.resolveProfiler();

            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.FINE);

            assertThat(ActiveProfiler.resolveProfiler())
                .isSameAs(boundProfiler);
        }

        @Test
        void bindsAFreshProfilerWhereTheLevelMoved() {
            // The other half of the comparison: a capture taken at one level cannot answer for
            // rows the other level would have kept, so it is replaced rather than reused.
            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.COARSE);

            var boundProfiler = ActiveProfiler.resolveProfiler();

            ProfilingCaptureInstaller.bindProfilerKeeping(ProfileLevel.FINE);

            assertThat(ActiveProfiler.resolveProfiler())
                .isNotSameAs(boundProfiler);
        }
    }

    @Nested
    class InstallAll {

        @Test
        void bindsTheLevelTheSettingsHold() {

            var boundProfilers = installThenChangeLevel(ProfileLevel.COARSE, ProfileLevel.COARSE);

            assertThat(boundProfilers.afterInstall().getRecordedLevel())
                .isEqualTo(ProfileLevel.COARSE);
        }

        @Test
        void rebindsWhereASettingsChangeMovesTheLevel() {
            // The reason the listener is registered at all: a player turning profiling on mid-session
            // is measured from then on, rather than from the next launch.
            var boundProfilers = installThenChangeLevel(ProfileLevel.OFF, ProfileLevel.FINE);

            assertThat(boundProfilers.afterChange().getRecordedLevel())
                .isEqualTo(ProfileLevel.FINE);
        }

        @Test
        void keepsTheCaptureWhereASettingsChangeLeavesTheLevel() {

            var boundProfilers = installThenChangeLevel(ProfileLevel.FINE, ProfileLevel.FINE);

            assertThat(boundProfilers.afterChange())
                .isSameAs(boundProfilers.afterInstall());
        }

        @Test
        void handsTheFrameworkTheFrameBoundTheSettingsHold() {

            try (var settingsMock = mockStatic(KmuProfilingSettings.class);
                    var lunaMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock.when(KmuProfilingSettings::getProfilingLevel)
                    .thenReturn(ProfileLevel.OFF);
                settingsMock.when(KmuProfilingSettings::getMapFrameBeatBudgetMillis)
                    .thenReturn(12.5);

                ProfilingCaptureInstaller.installAll();

                assertThat(MapFrameBudgets.resolveFrameBeatBudgetMillis())
                    .isEqualTo(12.5);
            }
        }
    }

    // Runs the install with the settings holding `levelAtInstall`, then fires one settings change
    // with them holding `levelAtChange`, and hands back the profiler bound after each. LunaLib
    // announces the settings rather than the setting, so a change is a bare callback with the live
    // value read behind it - which is the shape being tested.
    private static BoundProfilers installThenChangeLevel(ProfileLevel levelAtInstall, ProfileLevel levelAtChange) {

        try (var settingsMock = mockStatic(KmuProfilingSettings.class);
                var lunaMock = mockStatic(KmuLunaSettings.class)) {

            var onSettingsChange = new Runnable[1];

            lunaMock
                .when(() -> KmuLunaSettings.runOnSettingsChange(any()))
                .thenAnswer(invocation -> {
                    onSettingsChange[0] = invocation.getArgument(0);
                    return null;
                });

            settingsMock
                .when(KmuProfilingSettings::getProfilingLevel)
                .thenReturn(levelAtInstall);

            ProfilingCaptureInstaller.installAll();

            var afterInstall = ActiveProfiler.resolveProfiler();

            settingsMock
                .when(KmuProfilingSettings::getProfilingLevel)
                .thenReturn(levelAtChange);

            onSettingsChange[0].run();

            return new BoundProfilers(afterInstall, ActiveProfiler.resolveProfiler());
        }
    }

    // The profiler bound once the install ran, and the one bound once the settings then changed.
    private record BoundProfilers(
        Profiler afterInstall,
        Profiler afterChange) {
    }
}
