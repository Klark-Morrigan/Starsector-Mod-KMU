package kmu.settings;

import kmlib.logging.KmLogging;
import kmlib.settings.LunaSettingsReader;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single place where KMU registers its LunaLib settings bindings.
 *
 * <p>Called once from {@code KMU_ModPlugin.onApplicationLoad}. Keeping every
 * LunaLib wiring here - rather than scattered across the classes that consume
 * the settings - gives the mod one obvious home for "what does KMU bind, and
 * to what", and lets the mod plugin stay a thin entry point. New settings
 * bindings are added here as the mod grows.
 */
public final class KmuLunaSettings {
    // KMU's LunaLib settings id (matches data/config/LunaSettings.csv) and the
    // logger subtree the log-level field tunes. Every KMU class lives under
    // the "kmu" package, so that one logger name is the lever for the whole
    // mod's verbosity. The two coincide as strings but mean different things -
    // a settings id and a logger namespace.
    private static final String MOD_ID = "kmu";
    private static final String LOGGER_ROOT = "kmu";
    private static final String LOG_LEVEL_FIELD = "kmu_logLevel";

    // Political-map overlay: whether uninhabited systems are drawn at all.
    // Matches the Boolean field in data/config/LunaSettings.csv.
    private static final String SHOW_UNINHABITED_FIELD = "kmu_politicalMapShowUninhabited";

    // Bumped on every change to KMU's LunaLib settings. Consumers that cache
    // derived state (e.g. the political-map overlay) read this generation and
    // rebuild only when it moves, so they react to settings changes live off a
    // single event rather than polling each setting every frame.
    private static final AtomicInteger settingsGeneration = new AtomicInteger();

    private KmuLunaSettings() {
    }

    /**
     * Registers all of KMU's LunaLib bindings and applies their current
     * values. LunaLib is a hard dependency, so it has loaded by the time the
     * mod plugin calls this.
     */
    public static void installBindings() {
        KmLogging.bindToLunaSetting(MOD_ID, LOGGER_ROOT, LOG_LEVEL_FIELD);
        // One listener, registered once at load, advances the generation on any
        // KMU settings change - the live-update signal for cached consumers.
        LunaSettingsReader.runOnSettingsChange(MOD_ID, settingsGeneration::incrementAndGet);
    }

    /**
     * @return a counter that advances whenever KMU's LunaLib settings change;
     *         a consumer rebuilds its cached state when this differs from the
     *         value it last saw
     */
    public static int getSettingsGeneration() {
        return settingsGeneration.get();
    }

    /**
     * Reads whether the player has opted to draw uninhabited systems on the
     * political map. Off by default, so only faction-held systems show unless
     * the player turns it on.
     *
     * @return true when uninhabited systems should be outlined; false (the
     *         default) when the setting is unset or unreadable
     */
    public static boolean isShowUninhabitedSystemsEnabled() {
        return LunaSettingsReader.getBoolean(MOD_ID, SHOW_UNINHABITED_FIELD, false);
    }
}
