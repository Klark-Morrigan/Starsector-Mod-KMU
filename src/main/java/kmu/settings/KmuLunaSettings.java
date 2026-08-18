package kmu.settings;

import kmlib.logging.KmLogging;
import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;
import kmlib.settings.LunaSettingsReader;
import kmlib.settings.LunaSettingsWriter;

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
 * <p>A LunaLib field id is the key its value is stored under, so ids are frozen once
 * shipped: renaming one resets that setting for every existing player, exactly as a
 * persisted class or memory key cannot be renamed.
 *
 * <p>A diagnostic that defaults off is the one id worth renaming anyway. What a rename
 * costs is a value a player set on purpose, and a toggle that draws the map's own workings
 * is switched on to answer a question and off again after - so there is nothing to reset,
 * and the id can be made to say what its subject actually is rather than which layer
 * happened to want it first.
 *
 * <p>{@link KmuRetiredSettings} is the counterpart to that freeze, holding the ids of fields
 * withdrawn since shipping and sweeping their orphaned values at load. It reaches the store
 * through here for the reason the three above do - the mod id is bound in one place - and holds
 * its ids apart from theirs because a retired id has no reader to be paired with.
 */
public final class KmuLunaSettings {

    // KMU's LunaLib settings id (matches data/config/LunaSettings.csv) and the
    // logger subtree the log-level field tunes. Every KMU class lives under
    // the "kmu" package, so that one logger name is the lever for the whole
    // mod's verbosity. The two coincide as strings but mean different things -
    // a settings id and a logger namespace.
    private static final String MOD_ID = KmuMod.MOD_ID;
    private static final String LOGGER_ROOT = "kmu";
    private static final String LOG_LEVEL_FIELD = "kmu_logLevel";

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
     * @return a counter that advances whenever KMU's LunaLib settings change;
     *         a consumer rebuilds its cached state when this differs from the
     *         value it last saw
     */
    public static int getSettingsRevision() {
        return settingsRevision.get();
    }

    // Sheds a retired field's stored value, binding this mod's settings id the way the reads below
    // do. The only write here, and it writes nothing: what it removes is a key no accessor names,
    // so no listener is told and no consumer has anything to rebuild over. Which ids are retired is
    // KmuRetiredSettings', that being a list with no reader rather than settings machinery.
    static void clearSetting(String fieldId) {
        LunaSettingsWriter.removeSetting(MOD_ID, fieldId);
    }

    // Reads any Radio field and maps its stored label back to a choice, falling back on that
    // field's default when unset, unreadable, or left over from an option that no longer exists.
    // A Radio stores the selected option's label whatever the enum behind it, so one read serves
    // every choice-backed setting; the constants to match against come off the fallback itself, so
    // a caller names the field and its default and nothing else.
    static <T extends Enum<T> & LabeledChoice> T readChoice(String fieldId, T fallback) {
        return LabeledChoices.fromLabel(
            fallback.getDeclaringClass().getEnumConstants(),
            readString(fieldId, fallback.getLabel()),
            fallback);
    }

    // The typed reads, each binding this mod's LunaLib settings id once. Every accessor in the
    // three settings classes names only its own field and default, so the mod id appears here
    // rather than at each of them, and the LunaLib coupling narrows to these few lines. Package
    // private rather than public: they are the shared machinery of one package, not a surface the
    // mod reads settings through.
    static boolean readBoolean(String fieldId, boolean fallback) {
        return LunaSettingsReader.getBoolean(MOD_ID, fieldId, fallback);
    }

    static double readDouble(String fieldId, double fallback) {
        return LunaSettingsReader.getDouble(MOD_ID, fieldId, fallback);
    }

    // The same Double field read for a caller that hands its value to a float API - a UI geometry, a
    // colour component, an animation pace. LunaLib stores every non-integer field as a double, so the
    // narrowing is real and happens somewhere either way; done here it happens once, rather than each
    // accessor carrying a cast that reads like a decision it made.
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
