package kmu.diagnostics;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileLevel;
import kmlib.profiling.Profiler;
import kmlib.profiling.SilentProfiler;
import kmlib.profiling.recording.RecordingProfiler;

import kmu.KmuWiringSteps;
import kmu.maplayers.base.profiling.MapFrameBudgets;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuProfilingSettings;

/**
 * Binds the profiler the library measures through, at the level the player asked for, and rebinds
 * it wherever they move that knob.
 *
 * <p>The library is silent until a mod binds otherwise, and KMU is the mod whose console command
 * reads the readout - so this is the one place a profiler is chosen. Off is what makes the choice
 * worth having: a capture holds a tree of every measured call for as long as it is bound, so a
 * profiler bound once at load would measure every player who never opens the readout.
 *
 * <p>A rebind starts a fresh capture, what the previous profiler accumulated staying with it and
 * out of reach. That is why the level is compared against what is already bound rather than applied
 * on every announcement: LunaLib says that the settings changed rather than which setting did, so
 * acting on each would throw a running capture away whenever an unrelated slider moved.
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

        // The bound a capture judges a map frame by, handed to the framework rather than read there.
        // Here because this is where a settings class may be named on the framework's behalf: a file
        // under the framework naming one would have the level beside it within reach, and code able
        // to read whether it is measured can act on it. Bound ahead of the profiler for tidiness
        // only - nothing consults a bound until a capture is running.
        KmuWiringSteps.runGuardedStep(
            () -> MapFrameBudgets.registerFrameBeatBudget(
                KmuProfilingSettings::getMapFrameBeatBudgetMillis),
            "Failed to bind the KMU map frame beat budget");

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
     * <p>Takes the level rather than reading it, so which profiler answers for a level is settled
     * apart from where that level came from.
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
