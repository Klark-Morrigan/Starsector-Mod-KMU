package kmu.diagnostics;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.Profiler;
import kmlib.profiling.RecordingProfiler;

/**
 * KMU's side of KMLib's profiler seam: binds the recording profiler at launch
 * and resolves it for timing accumulated by engine-instantiated code (e.g. the
 * political-map terrain plugin) and the console command that reports it.
 *
 * <p>Resolved through {@link ActiveProfiler} rather than held here, so what
 * KMU measures and what the library measures on KMU's behalf land in one
 * readout. A static accessor rather than passed dependencies because the
 * producers and the reader are created independently by the engine and the
 * console, with no shared owner to thread an instance through. Diagnostics
 * only: nothing in gameplay depends on it.
 */
public final class KmuProfiling {

    private KmuProfiling() {
    }

    /**
     * Binds a fresh recording profiler as the one every measurement reaches.
     * The library is silent until a mod does this, and KMU is the mod that
     * reads the readout.
     */
    public static void bindRecordingProfiler() {
        ActiveProfiler.bindProfiler(new RecordingProfiler());
    }

    /**
     * @return the profiler that all KMU timing records and reports use
     */
    public static Profiler getProfiler() {
        return ActiveProfiler.resolveProfiler();
    }
}
