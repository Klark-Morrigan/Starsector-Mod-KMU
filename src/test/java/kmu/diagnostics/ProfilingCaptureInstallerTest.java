package kmu.diagnostics;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileLevel;
import kmlib.profiling.SilentProfiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which profiler a level asks for: off is the silent one, anything else a recording one
 * keeping detail to that level, and a level that has not moved rebinds nothing - which is what
 * stops an unrelated settings change throwing a running capture away.
 */
final class ProfilingCaptureInstallerTest {

    @AfterEach
    void restoreTheSilentProfiler() {
        // The holder is process-wide, so a case that bound a recording profiler would otherwise
        // leave every later case measuring into it.
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
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
}
