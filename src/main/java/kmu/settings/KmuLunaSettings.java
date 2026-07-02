package kmu.settings;

import kmlib.logging.KmLogging;
import kmlib.settings.LunaSettingsReader;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single place where KMU registers its LunaLib settings bindings and reads its
 * settings values.
 *
 * <p>Called once from {@code KMU_ModPlugin.onApplicationLoad}. Keeping every
 * LunaLib wiring here - rather than scattered across the classes that consume
 * the settings - gives the mod one obvious home for "what does KMU bind, and
 * to what", and lets the mod plugin stay a thin entry point. New settings
 * bindings are added here as the mod grows.
 *
 * <p>The political-map fields, matching data/config/LunaSettings.csv, style each
 * category of system on the sector map. Owned categories - core factions and
 * independent space - each get a fill, an outer (national) border, and an inner
 * (province seam) border, every one with a palette-color choice, an opacity, and
 * (for the borders) a line width. Factionless categories - decivilised and
 * uninhabited systems - draw only a single outline, so they get just a neutral
 * color choice (or none, to hide them), an opacity, and a width. All are tuned
 * under the LunaLib "Visuals customisation" tab.
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

    // Faction (core-faction bloc) style fields.
    private static final String FACTION_OUTER_BORDER_COLOR_FIELD =
            "kmu_politicalMapFactionOuterBorderColor";
    private static final String FACTION_OUTER_BORDER_OPACITY_FIELD =
            "kmu_politicalMapFactionOuterBorderOpacity";
    private static final String FACTION_OUTER_BORDER_WIDTH_FIELD =
            "kmu_politicalMapFactionOuterBorderWidth";
    private static final String FACTION_INNER_BORDER_COLOR_FIELD =
            "kmu_politicalMapFactionInnerBorderColor";
    private static final String FACTION_INNER_BORDER_OPACITY_FIELD =
            "kmu_politicalMapFactionInnerBorderOpacity";
    private static final String FACTION_INNER_BORDER_WIDTH_FIELD =
            "kmu_politicalMapFactionInnerBorderWidth";
    private static final String FACTION_FILL_COLOR_FIELD = "kmu_politicalMapFactionFillColor";
    private static final String FACTION_FILL_OPACITY_FIELD = "kmu_politicalMapFactionFillOpacity";

    // Independent (independent-held bloc) style fields, the same shape as faction.
    private static final String INDEPENDENT_OUTER_BORDER_COLOR_FIELD =
            "kmu_politicalMapIndependentOuterBorderColor";
    private static final String INDEPENDENT_OUTER_BORDER_OPACITY_FIELD =
            "kmu_politicalMapIndependentOuterBorderOpacity";
    private static final String INDEPENDENT_OUTER_BORDER_WIDTH_FIELD =
            "kmu_politicalMapIndependentOuterBorderWidth";
    private static final String INDEPENDENT_INNER_BORDER_COLOR_FIELD =
            "kmu_politicalMapIndependentInnerBorderColor";
    private static final String INDEPENDENT_INNER_BORDER_OPACITY_FIELD =
            "kmu_politicalMapIndependentInnerBorderOpacity";
    private static final String INDEPENDENT_INNER_BORDER_WIDTH_FIELD =
            "kmu_politicalMapIndependentInnerBorderWidth";
    private static final String INDEPENDENT_FILL_COLOR_FIELD =
            "kmu_politicalMapIndependentFillColor";
    private static final String INDEPENDENT_FILL_OPACITY_FIELD =
            "kmu_politicalMapIndependentFillOpacity";

    // Decivilised and uninhabited (factionless outline) style fields.
    private static final String DECIVILISED_BORDER_COLOR_FIELD =
            "kmu_politicalMapDecivilisedBorderColor";
    private static final String DECIVILISED_BORDER_OPACITY_FIELD =
            "kmu_politicalMapDecivilisedBorderOpacity";
    private static final String DECIVILISED_BORDER_WIDTH_FIELD =
            "kmu_politicalMapDecivilisedBorderWidth";
    private static final String UNINHABITED_BORDER_COLOR_FIELD =
            "kmu_politicalMapUninhabitedBorderColor";
    private static final String UNINHABITED_BORDER_OPACITY_FIELD =
            "kmu_politicalMapUninhabitedBorderOpacity";
    private static final String UNINHABITED_BORDER_WIDTH_FIELD =
            "kmu_politicalMapUninhabitedBorderWidth";

    // Fallbacks used only when a setting is read before LunaLib has loaded it;
    // the live values come from LunaLib. These mirror the defaultValue column in
    // data/config/LunaSettings.csv and must be kept in step with it.
    private static final FactionPaletteChoice DEFAULT_FACTION_OUTER_BORDER_COLOR =
            FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_OUTER_BORDER_OPACITY = 1.0;
    private static final double DEFAULT_FACTION_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_INNER_BORDER_COLOR =
            FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_FACTION_INNER_BORDER_WIDTH = 5.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_FILL_COLOR =
            FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_FILL_OPACITY = 0.4;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_OUTER_BORDER_COLOR =
            FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY = 0.5;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_INNER_BORDER_COLOR =
            FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH = 5.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_FILL_COLOR =
            FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_FILL_OPACITY = 0.2;
    private static final NeutralColorChoice DEFAULT_DECIVILISED_BORDER_COLOR =
            NeutralColorChoice.NEUTRAL;
    private static final double DEFAULT_DECIVILISED_BORDER_OPACITY = 0.35;
    private static final double DEFAULT_DECIVILISED_BORDER_WIDTH = 3.0;
    // Uninhabited defaults to No color (hidden), so only faction-held, independent,
    // and decivilised systems draw unless the player turns uninhabited on.
    private static final NeutralColorChoice DEFAULT_UNINHABITED_BORDER_COLOR =
            NeutralColorChoice.NONE;
    private static final double DEFAULT_UNINHABITED_BORDER_OPACITY = 0.15;
    private static final double DEFAULT_UNINHABITED_BORDER_WIDTH = 3.0;

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
     * @return which faction palette color the outer (national) border draws in,
     *         or NONE to hide it; the primary (bright) color by default
     */
    public static FactionPaletteChoice getFactionOuterBorderColor() {
        return readPaletteChoice(FACTION_OUTER_BORDER_COLOR_FIELD, DEFAULT_FACTION_OUTER_BORDER_COLOR);
    }

    /**
     * @return the outer (national) border opacity for faction systems, 0..1
     */
    public static double getFactionOuterBorderOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, FACTION_OUTER_BORDER_OPACITY_FIELD,
                DEFAULT_FACTION_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for faction systems, pixels
     */
    public static double getFactionOuterBorderWidth() {
        return LunaSettingsReader.getDouble(MOD_ID, FACTION_OUTER_BORDER_WIDTH_FIELD,
                DEFAULT_FACTION_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette color the inner (province seam) borders draw
     *         in, or NONE to hide them; the secondary (dark) color by default
     */
    public static FactionPaletteChoice getFactionInnerBorderColor() {
        return readPaletteChoice(FACTION_INNER_BORDER_COLOR_FIELD, DEFAULT_FACTION_INNER_BORDER_COLOR);
    }

    /**
     * @return the inner (province seam) border opacity for faction systems, 0..1
     */
    public static double getFactionInnerBorderOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, FACTION_INNER_BORDER_OPACITY_FIELD,
                DEFAULT_FACTION_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for faction systems,
     *         pixels
     */
    public static double getFactionInnerBorderWidth() {
        return LunaSettingsReader.getDouble(MOD_ID, FACTION_INNER_BORDER_WIDTH_FIELD,
                DEFAULT_FACTION_INNER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette color the territory fill draws in, or NONE to
     *         leave it unfilled; the primary (bright) color by default
     */
    public static FactionPaletteChoice getFactionFillColor() {
        return readPaletteChoice(FACTION_FILL_COLOR_FIELD, DEFAULT_FACTION_FILL_COLOR);
    }

    /**
     * @return the fill opacity for faction systems, 0..1
     */
    public static double getFactionFillOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, FACTION_FILL_OPACITY_FIELD,
                DEFAULT_FACTION_FILL_OPACITY);
    }

    /**
     * @return which independent palette color the outer (national) border draws
     *         in, or NONE to hide it; the primary (bright) color by default
     */
    public static FactionPaletteChoice getIndependentOuterBorderColor() {
        return readPaletteChoice(INDEPENDENT_OUTER_BORDER_COLOR_FIELD,
                DEFAULT_INDEPENDENT_OUTER_BORDER_COLOR);
    }

    /**
     * @return the outer (national) border opacity for independent-held systems,
     *         0..1
     */
    public static double getIndependentOuterBorderOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, INDEPENDENT_OUTER_BORDER_OPACITY_FIELD,
                DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for independent-held systems,
     *         pixels
     */
    public static double getIndependentOuterBorderWidth() {
        return LunaSettingsReader.getDouble(MOD_ID, INDEPENDENT_OUTER_BORDER_WIDTH_FIELD,
                DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette color the inner (province seam) borders
     *         draw in, or NONE to hide them; the secondary (dark) color by default
     */
    public static FactionPaletteChoice getIndependentInnerBorderColor() {
        return readPaletteChoice(INDEPENDENT_INNER_BORDER_COLOR_FIELD,
                DEFAULT_INDEPENDENT_INNER_BORDER_COLOR);
    }

    /**
     * @return the inner (province seam) border opacity for independent-held
     *         systems, 0..1
     */
    public static double getIndependentInnerBorderOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, INDEPENDENT_INNER_BORDER_OPACITY_FIELD,
                DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for independent-held
     *         systems, pixels
     */
    public static double getIndependentInnerBorderWidth() {
        return LunaSettingsReader.getDouble(MOD_ID, INDEPENDENT_INNER_BORDER_WIDTH_FIELD,
                DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette color the territory fill draws in, or NONE
     *         to leave it unfilled; the primary (bright) color by default
     */
    public static FactionPaletteChoice getIndependentFillColor() {
        return readPaletteChoice(INDEPENDENT_FILL_COLOR_FIELD, DEFAULT_INDEPENDENT_FILL_COLOR);
    }

    /**
     * @return the fill opacity for independent-held systems, 0..1
     */
    public static double getIndependentFillOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, INDEPENDENT_FILL_OPACITY_FIELD,
                DEFAULT_INDEPENDENT_FILL_OPACITY);
    }

    /**
     * @return whether decivilised systems draw their outline in the neutral color
     *         or not at all; the neutral color by default (a known dead colony is
     *         presence, so it draws unless the player hides it)
     */
    public static NeutralColorChoice getDecivilisedBorderColor() {
        return readNeutralChoice(DECIVILISED_BORDER_COLOR_FIELD, DEFAULT_DECIVILISED_BORDER_COLOR);
    }

    /**
     * @return the outline opacity for decivilised systems, 0..1
     */
    public static double getDecivilisedBorderOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, DECIVILISED_BORDER_OPACITY_FIELD,
                DEFAULT_DECIVILISED_BORDER_OPACITY);
    }

    /**
     * @return the outline line width for decivilised systems, pixels
     */
    public static double getDecivilisedBorderWidth() {
        return LunaSettingsReader.getDouble(MOD_ID, DECIVILISED_BORDER_WIDTH_FIELD,
                DEFAULT_DECIVILISED_BORDER_WIDTH);
    }

    /**
     * @return whether uninhabited systems draw their outline in the neutral color
     *         or not at all; NONE (hidden) by default, so only faction-held,
     *         independent, and decivilised systems draw unless the player turns
     *         uninhabited on
     */
    public static NeutralColorChoice getUninhabitedBorderColor() {
        return readNeutralChoice(UNINHABITED_BORDER_COLOR_FIELD, DEFAULT_UNINHABITED_BORDER_COLOR);
    }

    /**
     * @return the outline opacity for uninhabited systems, 0..1
     */
    public static double getUninhabitedBorderOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, UNINHABITED_BORDER_OPACITY_FIELD,
                DEFAULT_UNINHABITED_BORDER_OPACITY);
    }

    /**
     * @return the outline line width for uninhabited systems, pixels
     */
    public static double getUninhabitedBorderWidth() {
        return LunaSettingsReader.getDouble(MOD_ID, UNINHABITED_BORDER_WIDTH_FIELD,
                DEFAULT_UNINHABITED_BORDER_WIDTH);
    }

    // Reads a faction/independent palette Radio and maps its label to a choice,
    // falling back on that field's default when unset or unreadable.
    private static FactionPaletteChoice readPaletteChoice(String fieldId,
            FactionPaletteChoice fallback) {
        return FactionPaletteChoice.fromLabel(
                LunaSettingsReader.getString(MOD_ID, fieldId, fallback.getLabel()), fallback);
    }

    // Reads a factionless neutral-color Radio and maps its label to a choice,
    // falling back on that field's default when unset or unreadable.
    private static NeutralColorChoice readNeutralChoice(String fieldId,
            NeutralColorChoice fallback) {
        return NeutralColorChoice.fromLabel(
                LunaSettingsReader.getString(MOD_ID, fieldId, fallback.getLabel()), fallback);
    }
}
