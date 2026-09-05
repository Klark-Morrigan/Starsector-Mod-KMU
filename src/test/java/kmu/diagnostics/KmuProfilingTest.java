package kmu.diagnostics;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.Profiler;
import kmlib.profiling.RecordingProfiler;
import kmlib.profiling.SilentProfiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins KMU's side of the library's profiler seam: that what KMU binds is one
 * that keeps timings, and that what KMU reports is whatever the library holds
 * rather than an instance of its own.
 *
 * <p>Both halves are the point of going through the holder. A binding that
 * kept nothing would leave the console command printing an empty table, and a
 * profiler held here instead would split the readout in two - KMU's own
 * measurements in one and the library's in another, with neither saying so.
 */
final class KmuProfilingTest {

    // The holder is process-wide, so every case leaves it as it found it: silent.
    @AfterEach
    void restoreSilentProfiler() {
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
    }

    @Nested
    class BindRecordingProfiler {

        @Test
        void bindsAProfilerThatKeepsTimingsOnTheLibraryHolder() {

            ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);

            KmuProfiling.bindRecordingProfiler();

            assertThat(ActiveProfiler.resolveProfiler())
                .isInstanceOf(RecordingProfiler.class);
        }
    }

    @Nested
    class GetProfiler {

        @Test
        void answersWhicheverProfilerTheLibraryHolds() {
            // Bound through the library rather than through KMU, so a KmuProfiling that went back
            // to holding its own instance fails here rather than quietly reporting a second
            // profiler nothing else measures into.
            var profilerMock = mock(Profiler.class);

            ActiveProfiler.bindProfiler(profilerMock);

            assertThat(KmuProfiling.getProfiler())
                .isSameAs(profilerMock);
        }
    }
}
