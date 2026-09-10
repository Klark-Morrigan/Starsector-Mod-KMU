package kmu.settings;

/**
 * What the mod writes about itself to the game's log: how loud it is, and the one diagnostic that
 * loudness alone does not reach.
 *
 * <p>Both rows on the Logging section live here, though only one of them has an accessor. The level
 * is never read at a call site - it is bound once to the logger subtree at load, and every line the
 * mod writes then answers to it through log4j rather than through a check anyone wrote. So what this
 * holds for it is the pair {@link KmuLunaSettings} binds: the field the player sets, and the logger
 * subtree it sets the level on. The two coincide with the mod id as strings and mean different
 * things.
 *
 * <p>Keeping the level's field id here rather than beside the binding is what makes this class the
 * one answer to which logging rows exist. A row named in the composition root instead would be a
 * second place to look, and the settings sweep that walks ids against the table would be walking
 * against two homes for one section.
 *
 * <p>The reflection switch is the row a level cannot express. What sets it apart is not how
 * important its lines are but what they cost, which is why it is asked for by name rather than
 * arriving with everything else DEBUG turns on.
 */
public final class KmuLoggingSettings {

    // The logger subtree the level field tunes. Every KMU class lives under the "kmu" package, so
    // that one logger name is the lever for the whole mod's verbosity.
    //
    // Package private, with the field below: both are read by KmuLunaSettings at bind time rather
    // than by any caller, and a getter for a constant nothing outside this package may see would say
    // less than the constant does.
    static final String LOGGER_ROOT = "kmu";

    // Bound straight to that subtree at load, so it has no accessor here. Named here anyway, this
    // being where a reader looks for the rows the Logging section holds.
    static final String LOG_LEVEL_FIELD = "kmu_dev_logging_level";

    // Whether the reflective walks of the game's own widget tree narrate themselves. Its own switch
    // beside the level rather than a level of its own, because what sets it apart is not how
    // important the lines are but what they cost: a walk of the live tree per frame, printed at
    // roughly a kilobyte a line, against the tens of bytes the rest of DEBUG writes.
    private static final String REFLECTION_PROBES_FIELD =
        "kmu_dev_logging_areReflectionProbesEnabled";

    private static final boolean DEFAULT_REFLECTION_PROBES = false;

    private KmuLoggingSettings() {
    }

    /**
     * @return whether the reflective UI-tree traces narrate themselves; off by default, and read
     *         alongside rather than instead of the log level, so a caller reports only where both
     *         this and DEBUG are on
     */
    public static boolean areReflectionProbesEnabled() {
        return KmuLunaSettings.readBoolean(REFLECTION_PROBES_FIELD, DEFAULT_REFLECTION_PROBES);
    }
}
