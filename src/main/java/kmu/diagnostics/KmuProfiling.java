package kmu.diagnostics;

import kmlib.profiling.Profiler;

/**
 * Holds KMU's single shared {@link Profiler}, so timing accumulated by
 * engine-instantiated code (e.g. the political-map terrain plugin) and the
 * console command that reports it both reach the same instance.
 *
 * <p>A static accessor rather than passed dependencies because the producers
 * and the reader are created independently by the engine and the console, with
 * no shared owner to thread an instance through. Diagnostics only: nothing in
 * gameplay depends on it.
 */
public final class KmuProfiling {
    private static final Profiler PROFILER = new Profiler();

    private KmuProfiling() {
    }

    /**
     * @return the shared profiler that all KMU timing records and reports use
     */
    public static Profiler getProfiler() {
        return PROFILER;
    }
}
