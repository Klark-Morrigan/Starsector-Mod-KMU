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
 * the mod ID every read is scoped by.
 *
 * <p>The knobs sit in classes beside this one, split first by which package reads them:
 * the {@code KmuMap*Settings} readers and {@link KmuMapKeybindSettings} for what the map-layer
 * framework's own chrome, geometry and keys need, {@link KmuMarketConditionSettings} for the
 * condition picker, {@link KmuProfilingSettings} for how much of what the mod does is measured and
 * what a capture flags, {@link KmuLoggingSettings} for how much of it the mod says, and the
 * political map layer's own set. Splitting on the reader rather than on the settings tab
 * is what keeps a layer's knobs out of reach of the framework: a class no framework code imports
 * cannot leak a feature's vocabulary into it, which a tab-shaped split could not promise.
 * {@code Map - Dev} is where the two part company - it carries the tuning of geometry every layer
 * shares, but most of that tuning is read by the political layer that resolves it, so a class named
 * for that tab would be imported by both halves and be the shared surface again under a new name.
 * {@link KmuMapRefreshSettings} is the framework's own row on that tab: the cadence every staleness
 * poll runs on, read by the loop the polls share and by nothing a layer holds.
 *
 * <p>The political map's own set is split again by what its knobs act on, one class per section
 * of the settings screen: {@link KmuPoliticalMapTerritorySettings} for how each kind of
 * territory paints, {@link KmuPoliticalMapRibbonSettings} for the presence bands,
 * {@link KmuPoliticalMapHighlightSettings} for what lights up under a hover,
 * {@link KmuPoliticalMapDrawOrderSettings} for which side of the nebulae each sub-layer paints
 * on, {@link KmuPoliticalMapDominanceSettings} for the verdicts rather than the paint,
 * {@link KmuPoliticalMapGeometrySettings} for the shapes the map is built from, and
 * {@link KmuPoliticalMapDiagnosticsSettings} for the dev overlays. That second split is by
 * section rather than by reader because within one feature every knob has the same reader:
 * what a class named for a section buys is that nothing imports more of the layer's settings
 * surface than the part it actually reads.
 *
 * <p>{@link KmuMapKeybindSettings} is the framework's one such section, and is named for its tab
 * because the tab is what it holds - every key the overlay answers to and nothing else. It buys no
 * isolation, the framework reading both halves; what it buys is that one screen of the settings
 * dialog has one class behind it, so a key added to that tab has an obvious home.
 *
 * <p>All of them hold field IDs, fallbacks and accessors only. The mod ID, the revision and
 * the reads stay here because they are mod-wide - a second layer's settings would want the
 * same reads against the same ID - and because a fallback that mirrors a CSV default belongs
 * beside the accessor that answers with it, not beside the reader that fetches it. The keybind
 * section is the one that mirrors no default at all, and says there why.
 *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * in those classes answers only what that column cannot: why a default is the number it is, and
 * what a caller has to know to use the value.
 *
 * <p>Every field ID spells the tab path a player finds the row under, segment by segment -
 * {@code kmu_<tab>_<section>_<group>_<knob>} - so a knob's stored key says where it is set
 * rather than which feature first wanted it. A noun two or more knobs share is a segment of
 * its own and the leaf never repeats it, so {@code cellGeometry_radius} rather than
 * {@code cellGeometry_cellRadius}. A switch names itself as a predicate ({@code is},
 * {@code are}, {@code should}) so its ID reads as the question the row answers, and a group's
 * own on/off is {@code <group>_isEnabled}.
 *
 * <p>A LunaLib field ID is the key its value is stored under, so IDs are frozen once
 * shipped: renaming one resets that setting for every existing player, exactly as a
 * persisted class or memory key cannot be renamed. LunaLib only ever adds - it seeds a default
 * for every row the shipped table declares and prunes nothing - so a value left behind by a
 * renamed or withdrawn row stays in the player's settings file unread rather than being
 * cleaned up, and would be handed to any later field that reused the id.
 */
public final class KmuLunaSettings {

    /**
     * What a row authored as a percentage is divided by to reach the 0..1 fraction its caller
     * works in.
     *
     * <p>Several rows are stated as percentages because the settings slider rounds a Double to two
     * decimals, which a fraction authored directly could not survive being dragged. Held here
     * rather than restated per reader so no accessor reaches for its own row's ceiling to divide
     * by: a bound doing duty as a unit is correct only while that bound happens to be a hundred,
     * and silently rescales the setting the day it is narrowed.
     */
    static final double PERCENT_PER_UNIT = 100.0;

    // The ID every read below is scoped by. What the log-level binding needs beside it - the field
    // the player sets and the logger subtree it tunes - is named in KmuLoggingSettings with the rest
    // of that section's rows, so which logging rows exist has one answer rather than two.
    private static final String MOD_ID = KmuMod.MOD_ID;

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
        KmLogging.bindToLunaSetting(
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
