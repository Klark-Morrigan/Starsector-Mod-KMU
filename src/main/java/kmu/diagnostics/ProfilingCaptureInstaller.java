package kmu.diagnostics;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileLevel;
import kmlib.profiling.Profiler;
import kmlib.profiling.SilentProfiler;
import kmlib.profiling.recording.RecordingProfiler;

import kmu.KmuWiringSteps;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuProfilingSettings;

/**
 * Binds the profiler the library measures through, at the level the player asked for, and rebinds
 * it wherever they move that knob.
 *
 * <p>The library is silent until a mod binds otherwise, and KMU is the mod whose console command
 * reads the readout - so this is the one place a profiler is chosen. Which one it is follows from
 * the level alone: off is the silent profiler, and anything else is a recording one keeping detail
 * to that level.
 *
 * <p>Rebinding is what makes off real. A capture holds a tree of every measured call for as long as
 * it is bound, so a mod that bound a recording profiler at load and left it there would measure
 * every player who never opens the readout.
 *
 * <p>A rebind starts a fresh capture: what the previous profiler accumulated stays with it and is
 * no longer reachable. That is why the level is compared against what is already bound rather than
 * applied on every announcement - LunaLib says that the settings changed rather than which setting
 * did, so acting on each one would throw a capture away whenever an unrelated slider moved.
 */
public final class ProfilingCaptureInstaller {

    private ProfilingCaptureInstaller() {
    }

    /**
     * Binds the profiler the settings currently ask for, and keeps it in line with them.
     *
     * <p>Each step is guarded on its own, so the entry point calls this directly.
     */
    public static void installAll() {

        KmuWiringSteps.runGuardedStep(
            ProfilingCaptureInstaller::applySettingsLevel,
            "Failed to bind the KMU profiler");

        KmuWiringSteps.runGuardedStep(
            () -> KmuLunaSettings.runOnSettingsChange(
                ProfilingCaptureInstaller::applySettingsLevel),
            "Failed to install the KMU profiling level listener");
    }

    /**
     * Binds a profiler keeping detail to {@code wantedLevel}, unless one already is.
     *
     * <p>Apart from the knob it is usually driven by, because what to bind for a level and where
     * the level came from are two decisions - and only the first of them is about profiling.
     *
     * @param wantedLevel the finest detail a capture should be keeping from now on
     */
    static void bindProfilerKeeping(ProfileLevel wantedLevel) {

        // What is bound is asked rather than remembered: the holder is where the answer lives, and
        // a copy of it here could disagree with it after any other binding.
        if (wantedLevel == ActiveProfiler.resolveProfiler().getRecordedLevel()) {
            return;
        }
        ActiveProfiler.bindProfiler(createProfilerKeeping(wantedLevel));
    }

    private static void applySettingsLevel() {
        bindProfilerKeeping(KmuProfilingSettings.getProfilingLevel());
    }

    // The silent profiler for off rather than a recording one bound at off, so nothing is
    // allocated and no scope is pushed on the paths a frame runs through.
    private static Profiler createProfilerKeeping(ProfileLevel level) {
        return level == ProfileLevel.OFF ? SilentProfiler.INSTANCE : new RecordingProfiler(level);
    }
}
