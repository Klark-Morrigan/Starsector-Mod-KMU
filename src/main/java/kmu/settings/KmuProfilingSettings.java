package kmu.settings;

import kmlib.profiling.ProfileLevel;

/**
 * How much of what the mod does is measured, and what a running capture flags as having taken too
 * long - the {@code Map - Dev} Profiling section, one class behind it.
 *
 * <p>Its own class beside the knob classes {@link KmuLunaSettings} lists, and split from them by
 * what it is about rather than by who reads it: a capture is a diagnostic laid over every feature,
 * so its knobs belong to nobody's feature in particular.
 *
 * <p>The two are read from opposite ends, which is worth saying because it looks like an
 * inconsistency. The level is read where the mod's start-up wiring is composed: which profiler is
 * bound is a decision about the whole mod, and the map layers - which are what a capture mostly
 * measures - must not be the ones deciding whether they are being watched. The budget is read by the
 * measured code itself, once a beat has ended, because it is not a decision about being watched at
 * all: it is the line a capture already running compares what it just timed against, and nothing but
 * the code holding that duration is in a position to ask.
 *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is what it is, and what a caller has
 * to know to use the value.
 */
public final class KmuProfilingSettings {

    private static final String FRAME_BEAT_BUDGET_MILLIS_FIELD =
        "kmu_map_dev_profiling_frameBeat_budgetMillis";

    private static final String PROFILING_LEVEL_FIELD = "kmu_map_dev_profiling_level";

    // A sixtieth of a second is the whole frame, and the map layers are one thing drawn in it beside
    // the game's own sector map - so a quarter of it is the share a beat can take before the frame
    // it sits in is the layers' fault.
    private static final double DEFAULT_FRAME_BEAT_BUDGET_MILLIS = 4.0;

    // Off, because a capture is a diagnostic rather than a feature: it keeps a tree of every
    // measured call for as long as it is bound, which is memory spent on nobody's behalf until
    // somebody opens the readout. The fallback and the CSV default agree, so a player who has never
    // touched the row is measured neither before nor after LunaLib loads.
    private static final ProfilingLevelChoice DEFAULT_PROFILING_LEVEL = ProfilingLevelChoice.OFF;

    private KmuProfilingSettings() {
    }

    /**
     * @return how long one beat of a map frame - a preparation, a paint band, a cursor read, a
     *         tooltip - may take before a running capture reports it as over budget, in
     *         milliseconds; 4ms by default, and 0 for no bound at all. Read as each beat ends
     *         while a capture is running, so moving it holds the next frame to what it now says
     */
    public static double getMapFrameBeatBudgetMillis() {
        return KmuLunaSettings.readDouble(
            FRAME_BEAT_BUDGET_MILLIS_FIELD,
            DEFAULT_FRAME_BEAT_BUDGET_MILLIS);
    }

    /**
     * @return how much detail a capture keeps: nothing at all (the default), the beats and passes a
     *         frame is made of, or those plus what one turn of a per-item loop costs. Read as the
     *         setting changes rather than per measurement, the level being a property of the
     *         profiler that is bound
     */
    public static ProfileLevel getProfilingLevel() {
        return KmuLunaSettings
            .readChoice(PROFILING_LEVEL_FIELD, DEFAULT_PROFILING_LEVEL)
            .resolveProfileLevel();
    }
}
