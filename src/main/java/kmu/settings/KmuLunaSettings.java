package kmu.settings;

import kmlib.settings.KmlibLunaSettings;
import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;
import kmlib.settings.LunaLogLevelBinding;
import kmlib.settings.LunaSettingsReader;
import kmlib.starsector.compatibility.ModIntegration;

import kmu.KmuMod;
import kmu.util.KmuCompatibilityConsumers;
import kmu.util.KmuStringKeys;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * KMU's LunaLib wiring: what the mod binds at load, the revision that says a setting
 * moved, and the typed reads every settings accessor goes through.
 *
 * <p>Called once from {@code KMU_ModPlugin.onApplicationLoad}. The fields themselves are
 * declared in the settings table, one per locale at localisation/&lt;locale&gt;/LunaSettings.csv
 * and written to the data/config/LunaSettings.csv LunaLib reads, rather than in Java - so binding
 * is the logger hookup and the settings-change listener and nothing more; what this class really
 * owns is the mod ID every read is scoped by.
 *
 * <p>The knobs sit in classes beside this one. How they are split, what each holds, where a
 * knob's prose lives and how a field ID is spelt and frozen are
 * <a href="README.md">the settings register</a>'s. The mod ID, the revision and the reads stay
 * here because they are mod-wide: a second layer's settings would want the same reads against the
 * same ID.
 *
 * <p>A row whose caller works in a 0..1 fraction is authored as a percentage, and its accessor
 * converts it. The settings slider rounds a Double to two decimals, which a fraction authored
 * directly could not survive being dragged.
 */
public final class KmuLunaSettings {

    // The ID every read below is scoped by. What the log-level binding needs beside it - the field
    // the player sets and the logger subtree it tunes - is named in KmuLoggingSettings with the rest
    // of that section's rows, so which logging rows exist has one answer rather than two.
    private static final String MOD_ID = KmuMod.MOD_ID;

    // Which of KMU's features a failed LunaLib binding costs, as the half of a latch key the mod ID
    // does not cover. One key for every binding, because they all go through the same LunaLib entry
    // point and what each costs is the same loss: KMU no longer answering a settings change.
    private static final String LUNALIB_SETTINGS_FEATURE_KEY = "lunalib-settings";

    // Bumped on every change to KMU's LunaLib settings. Consumers that cache
    // derived state (e.g. the owner-map overlay cache) read this revision and
    // rebuild only when it moves, so they react to settings changes live off a
    // single event rather than polling each setting every frame.
    private static final AtomicInteger settingsRevision = new AtomicInteger();

    private KmuLunaSettings() {
    }

    /**
     * KMU's LunaLib bindings as the compatibility channel states them: LunaLib as the third party,
     * and KMU as the mod that loses something by it.
     *
     * <p>One description for every step that binds a settings listener, whichever mod's settings it
     * listens to, so a LunaLib release that breaks the registration they share is one report rather
     * than one per step. Composed only once a step has failed, so the wording read out of
     * strings.json and the mod manager read behind the installed version stay off every load where
     * the bindings took.
     *
     * @return the integration a failed LunaLib binding is reported under
     */
    public static ModIntegration describeLunaLibIntegration() {

        return KmlibLunaSettings.describeLunaLibIntegration(
            KmuCompatibilityConsumers.describeConsumer(
                LUNALIB_SETTINGS_FEATURE_KEY,
                KmuStringKeys.COMPATIBILITY_LOST_LUNALIB_SETTINGS,
                KmuStringKeys.COMPATIBILITY_UNAFFECTED_LUNALIB_SETTINGS));
    }

    /**
     * Registers all of KMU's LunaLib bindings and applies their current
     * values. LunaLib is a hard dependency, so it has loaded by the time the
     * mod plugin calls this.
     */
    public static void installBindings() {
        LunaLogLevelBinding.bindLogLevel(
            MOD_ID,
            KmuLoggingSettings.LOGGER_ROOT,
            KmuLoggingSettings.LOG_LEVEL_FIELD);
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

    // The typed reads, each binding this mod's LunaLib settings ID once, so the LunaLib coupling
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
