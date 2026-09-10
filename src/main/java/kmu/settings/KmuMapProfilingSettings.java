package kmu.settings;

/**
 * What a running capture flags as having taken too long on a map frame.
 *
 * <p>Apart from {@link KmuProfilingSettings}, which holds the level, although both are rows of the
 * one Profiling section. The two are read from opposite ends and only one of them may be read from
 * inside the framework. The level decides whether the map layers are being measured at all, so they
 * must not be able to read it - a framework that could would be able to act on it, and a measurement
 * the measured code can see is not one. The budget decides nothing of the sort: it is the line a
 * capture already running compares what it just timed against, and nothing but the code holding that
 * duration is in a position to ask. Sharing one class would put the level within reach of every
 * framework file that wanted the budget.
 *
 * <p>What the knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why the default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapProfilingSettings {

    private static final String FRAME_BEAT_BUDGET_MILLIS_FIELD =
        "kmu_map_dev_profiling_frameBeat_budgetMillis";

    // A sixtieth of a second is the whole frame, and the map layers are one thing drawn in it beside
    // the game's own sector map - so a quarter of it is the share a beat can take before the frame
    // it sits in is the layers' fault.
    private static final double DEFAULT_FRAME_BEAT_BUDGET_MILLIS = 4.0;

    private KmuMapProfilingSettings() {
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
}
