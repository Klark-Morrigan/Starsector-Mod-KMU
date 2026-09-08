package kmu.settings;

import kmlib.profiling.ProfileLevel;

/**
 * How much of what the mod does is measured.
 *
 * <p>Its own class beside the knob classes {@link KmuLunaSettings} lists, and split from them by
 * the same rule: this is read where the mod's start-up wiring is composed rather than by any
 * feature. Which profiler is bound is a decision about the whole mod, and the map layers - which
 * are what a capture mostly measures - must not be the ones deciding whether they are being
 * watched.
 *
 * <p>What the knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why the default is what it is, and what a caller has
 * to know to use the value.
 */
public final class KmuProfilingSettings {

    private static final String PROFILING_LEVEL_FIELD = "kmu_map_dev_profiling_level";

    // Off, because a capture is a diagnostic rather than a feature: it keeps a tree of every
    // measured call for as long as it is bound, which is memory spent on nobody's behalf until
    // somebody opens the readout. The fallback and the CSV default agree, so a player who has never
    // touched the row is measured neither before nor after LunaLib loads.
    private static final ProfilingLevelChoice DEFAULT_PROFILING_LEVEL = ProfilingLevelChoice.OFF;

    private KmuProfilingSettings() {
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
