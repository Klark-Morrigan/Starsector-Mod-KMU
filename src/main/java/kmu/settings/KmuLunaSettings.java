package kmu.settings;

import kmlib.logging.KmLogging;
import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;
import kmlib.settings.LunaSettingsReader;

import kmu.KmuMod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * KMU's LunaLib wiring: what the mod binds at load, the revision that says a setting
 * moved, and the typed reads every settings accessor goes through.
 *
 * <p>Called once from {@code KMU_ModPlugin.onApplicationLoad}. The fields themselves are
 * declared in data/config/LunaSettings.csv rather than in Java, so binding is the logger
 * hookup and the settings-change listener and nothing more; what this class really owns is
 * the mod id every read is scoped by.
 *
 * <p>The knobs sit in three classes beside this one, split by which package reads them:
 * {@link KmuMapLayerSettings} for what the map-layer framework's own chrome and geometry
 * need, {@link KmuPoliticalMapSettings} for the political map layer's paint and verdicts,
 * and {@link KmuMarketConditionSettings} for the condition picker. Splitting on the reader
 * rather than on the settings tab is what keeps a layer's knobs out of reach of the
 * framework: a class no framework code imports cannot leak a feature's vocabulary into it,
 * which a tab-shaped split could not promise. {@code Map - Dev} is where the two part
 * company - it carries the tuning of geometry every layer shares, but most of that tuning is
 * read by the political layer that resolves it, so a class named for that tab would be
 * imported by both halves and be the shared surface again under a new name.
 *
 * <p>Those three hold field ids, fallbacks and accessors only. The mod id, the revision and
 * the reads stay here because they are mod-wide - a second layer's settings would want the
 * same reads against the same id - and because a fallback that mirrors a CSV default belongs
 * beside the accessor that answers with it, not beside the reader that fetches it.
 *
 * <p>Every field id spells the tab path a player finds the row under, segment by segment -
 * {@code kmu_<tab>_<section>_<group>_<knob>} - so a knob's stored key says where it is set
 * rather than which feature first wanted it. A noun two or more knobs share is a segment of
 * its own and the leaf never repeats it, so {@code cellGeometry_radius} rather than
 * {@code cellGeometry_cellRadius}. A switch names itself as a predicate ({@code is},
 * {@code are}, {@code should}) so its id reads as the question the row answers, and a group's
 * own on/off is {@code <group>_isEnabled}.
 *
 * <p>A LunaLib field id is the key its value is stored under, so ids are frozen once
 * shipped: renaming one resets that setting for every existing player, exactly as a
 * persisted class or memory key cannot be renamed. LunaLib only ever adds - it seeds a default
 * for every row the shipped table declares and prunes nothing - so a value left behind by a
 * renamed or withdrawn row stays in the player's settings file unread rather than being
 * cleaned up, and would be handed to any later field that reused the id.
 */
public final class KmuLunaSettings {

    // The settings id and the logger subtree the log-level field tunes. Every KMU class lives
    // under the "kmu" package, so that one logger name is the lever for the whole mod's
    // verbosity. The two coincide as strings but mean different things.
    private static final String MOD_ID = KmuMod.MOD_ID;
    private static final String LOGGER_ROOT = "kmu";
    private static final String LOG_LEVEL_FIELD = "kmu_dev_logging_level";

    // Bumped on every change to KMU's LunaLib settings. Consumers that cache
    // derived state (e.g. the political-map overlay) read this revision and
    // rebuild only when it moves, so they react to settings changes live off a
    // single event rather than polling each setting every frame.
    private static final AtomicInteger settingsRevision = new AtomicInteger();

    private KmuLunaSettings() {
    }

    /**
     * Registers all of KMU's LunaLib bindings and applies their current
     * values. LunaLib is a hard dependency, so it has loaded by the time the
     * mod plugin calls this.
     */
    public static void installBindings() {
        KmLogging.bindToLunaSetting(MOD_ID, LOGGER_ROOT, LOG_LEVEL_FIELD);
        // One listener, registered once at load, advances the revision on any
        // KMU settings change - the live-update signal for cached consumers.
        LunaSettingsReader.runOnSettingsChange(MOD_ID, settingsRevision::incrementAndGet);
    }

    /**
     * Runs {@code onChange} whenever any of KMU's LunaLib settings changes.
     *
     * <p>The callback fires for a change to any field, not to one - LunaLib announces the settings
     * rather than the setting - so a caller acting on one knob compares its value against what it
     * last acted on rather than acting every time.
     *
     * @param onChange what to run after a settings change lands
     */
    public static void runOnSettingsChange(Runnable onChange) {
        LunaSettingsReader.runOnSettingsChange(MOD_ID, onChange);
    }

    /**
     * @return a counter that advances whenever KMU's LunaLib settings change;
     *         a consumer rebuilds its cached state when this differs from the
     *         value it last saw
     */
    public static int getSettingsRevision() {
        return settingsRevision.get();
    }

    // Reads any Radio field and maps its stored label back to a choice, falling back on that
    // field's default when unset, unreadable, or left over from an option that no longer exists.
    // A Radio stores the selected option's label whatever the enum behind it, so one read serves
    // every choice-backed setting.
    static <T extends Enum<T> & LabeledChoice> T readChoice(String fieldId, T fallback) {
        return LabeledChoices.fromLabel(
            fallback.getDeclaringClass().getEnumConstants(),
            readString(fieldId, fallback.getLabel()),
            fallback);
    }

    // The typed reads, each binding this mod's LunaLib settings id once, so the LunaLib coupling
    // narrows to these few lines and an accessor names only its own field and default. Package
    // private rather than public: they are the shared machinery of one package, not a surface the
    // mod reads settings through.
    static boolean readBoolean(String fieldId, boolean fallback) {
        return LunaSettingsReader.getBoolean(MOD_ID, fieldId, fallback);
    }

    static double readDouble(String fieldId, double fallback) {
        return LunaSettingsReader.getDouble(MOD_ID, fieldId, fallback);
    }

    // The same Double field read for a caller that hands its value to a float API. LunaLib stores
    // every non-integer field as a double, so the narrowing happens somewhere either way; done here
    // it happens once, rather than each accessor carrying a cast that reads like a decision it made.
    static float readFloat(String fieldId, float fallback) {
        return (float) LunaSettingsReader.getDouble(MOD_ID, fieldId, fallback);
    }

    static int readInt(String fieldId, int fallback) {
        return LunaSettingsReader.getInt(MOD_ID, fieldId, fallback);
    }

    private static String readString(String fieldId, String fallback) {
        return LunaSettingsReader.getString(MOD_ID, fieldId, fallback);
    }
}
