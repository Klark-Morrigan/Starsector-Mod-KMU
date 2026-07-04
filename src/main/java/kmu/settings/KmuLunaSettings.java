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
 *
 * <p>The national-border geometry is exposed separately under the "Dev" tab: it
 * shapes the frontier rather than recoloring it, so it is a tuning surface for
 * experimentation, not a styling choice. The tab splits it into the three operations
 * that produce a border, in pipeline order - border tracing (weld tolerance, miter
 * limit), spike sanding, and corner rounding - each its own section. The two smoothing
 * operations are gated: their section leads with a boolean master switch that turns the
 * pass off whole without zeroing the knobs beneath it, so the switch reads as their gate.
 * The corner-rounding gate also covers the factionless (decivilised and uninhabited)
 * cell outlines, which reuse the same rounding. Like the visual fields all feed the same
 * drawables rebuild, so a change takes effect live. The "Label anchors" section that
 * follows carries the label-anchor search's modifiers - the direction and offset counts
 * that size the candidate grid, the vertical-penalty strength and exponent that trade a
 * line's slope against its length, and the end inset and icon clearance every candidate
 * is trimmed by - live for the same reason: the search is tuned by eye on the open map.
 *
 * <p>The "Dev" tab also carries the cell frontier resolution - the vertex count of
 * each raw Voronoi cell's rounded reach into empty space. It is the one field that
 * reseeds the geometry rather than restyling it, so it feeds the geometry rebuild;
 * it exists to trade map framerate against frontier smoothness.
 *
 * <p>The "Political map" tab holds the dominance rules - fields that change the
 * map's political verdicts rather than its styling, which is why they do not sit
 * under "Visuals customisation". Its one field chooses whether stability weighs
 * each colony's dominance contribution; it is on by default.
 *
 * <p>The "Market conditions" tab holds the condition-picker toggles. Its one field
 * chooses whether the picker offers every market condition or only the planetary
 * ones vanilla treats as hand-placeable; it is on by default, so non-planetary
 * conditions (such as decivilisation) are offered too.
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

    // Faction (core-faction cluster) style fields.
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

    // Independent (independent-held cluster) style fields, the same shape as faction.
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

    // Dominance rules (Political map tab): how the map decides a system's dominant
    // faction. Not a styling field - it changes the political verdicts themselves.
    private static final String STABILITY_WEIGHS_DOMINANCE_FIELD =
            "kmu_politicalMapStabilityWeighsDominance";

    // Cell geometry (Dev tab): the resolution of the raw Voronoi cells, upstream of
    // any border shaping. Unlike the border fields below - which restyle fixed
    // geometry through the drawables rebuild - this reseeds the cells themselves, so
    // it feeds the geometry rebuild. Every segment is a vertex on each frontier cell,
    // so it is the lever for trading map FPS against frontier smoothness.
    private static final String CELL_BOUND_SEGMENTS_FIELD =
            "kmu_politicalMapCellBoundSegments";

    // National-border geometry (Dev tab): the shape of the frontier stroked and filled
    // per cluster, exposed for live tuning rather than baked as constants. All feed the
    // drawables rebuild, so a change takes effect the moment it is applied. The Dev tab
    // groups these into the three operations that produce the border, in pipeline order
    // below; the two gated operations each lead with their master switch so the switch
    // reads as the gate for the knobs beneath it.

    // Border tracing: the raw ring chaining and miter inset. Always applied - it is
    // upstream of the two gated smoothing passes, so it has no switch of its own.
    private static final String BORDER_WELD_TOLERANCE_FIELD =
            "kmu_politicalMapBorderWeldTolerance";
    private static final String BORDER_MITER_LIMIT_FIELD =
            "kmu_politicalMapBorderMiterLimit";

    // Spike sanding: gate then its knobs. Splices out needle/cusp protrusions the
    // rounding cannot fix (a corner both sharper than the angle and shallower than the
    // height) from the resolved border before rounding. The gate leaves both knobs
    // unread when off, so their values survive for when it is switched back on.
    private static final String SAND_SPIKES_FIELD =
            "kmu_politicalMapSandSpikes";
    private static final String BORDER_SPIKE_HEIGHT_FIELD =
            "kmu_politicalMapBorderSpikeHeight";
    private static final String BORDER_SPIKE_ANGLE_FIELD =
            "kmu_politicalMapBorderSpikeAngle";

    // Corner rounding: gate then its knobs. Replaces each sharp corner with an arc. The
    // gate leaves the radius, segments, and chamfer unread when off, and also covers the
    // factionless cell outlines, which reuse this same corner-rounding pass.
    private static final String ROUND_CORNERS_FIELD =
            "kmu_politicalMapRoundCorners";
    private static final String BORDER_CORNER_RADIUS_FIELD =
            "kmu_politicalMapBorderCornerRadius";
    private static final String BORDER_CORNER_SEGMENTS_FIELD =
            "kmu_politicalMapBorderCornerSegments";
    private static final String BORDER_CHAMFER_ANGLE_FIELD =
            "kmu_politicalMapBorderChamferAngle";
    // Label anchors (Dev tab): the modifiers of the per-cluster label-anchor search -
    // the straight line a faction name will sit on, chosen by scoring many candidate
    // lines swept across the cluster (a fan of directions times a family of parallel
    // offsets), each fit inside the national border and clear of the system icons. Live
    // knobs rather than constants so the search can be tuned on the open map; all feed
    // the drawables rebuild, which re-runs the search for every anchor. The direction and
    // offset counts size the candidate grid; the vertical-penalty strength and exponent
    // shape how much a shallower (more horizontal) line may sacrifice in length and still
    // win, so horizontal preference is decided on measured lengths rather than by bending
    // any direction before the fit.
    private static final String ANCHOR_DIRECTION_COUNT_FIELD =
            "kmu_politicalMapAnchorDirectionCount";
    private static final String ANCHOR_OFFSET_COUNT_FIELD =
            "kmu_politicalMapAnchorOffsetCount";
    private static final String ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD =
            "kmu_politicalMapAnchorVerticalPenaltyStrength";
    private static final String ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD =
            "kmu_politicalMapAnchorVerticalPenaltyExponent";
    private static final String ANCHOR_END_INSET_MULTIPLE_FIELD =
            "kmu_politicalMapAnchorEndInsetMultiple";
    private static final String ANCHOR_ICON_CLEARANCE_FIELD =
            "kmu_politicalMapAnchorIconClearance";
    // Diagnostics (Dev tab): draws the per-cluster label anchors (a centre dot and the
    // accepted label line in green) so the clustering and axis fit behind the coming
    // faction labels can be eyeballed on the map. Off by default.
    private static final String SHOW_CLUSTER_ANCHORS_FIELD =
            "kmu_politicalMapShowClusterAnchors";
    // Diagnostics (Dev tab): the two extra anchor lines layered under the accepted one,
    // each behind its own toggle so the anchor overlay stays readable by default. The
    // rejected line (red) shows the best candidate a collapsed fit had before the
    // border, icon, or end-margin trim discarded it; the unbiased line (yellow) shows
    // what the fit would accept with no horizontal bias, so the bias knob's effect is
    // visible directly. Both meaningful only while the anchors themselves draw.
    private static final String SHOW_REJECTED_AXES_FIELD =
            "kmu_politicalMapShowRejectedAxes";
    private static final String SHOW_UNBIASED_AXES_FIELD =
            "kmu_politicalMapShowUnbiasedAxes";
    // Diagnostics (Dev tab): replaces the normal render with a border-tracing overlay that
    // layers the smoothing pipeline's stages (base, despiked, rounded) in distinct colors,
    // honouring the two smoothing gates. Off by default.
    private static final String DEBUG_BORDER_TRACING_FIELD =
            "kmu_politicalMapDebugBorderTracing";
    // Market-conditions tab: whether the condition picker offers every market
    // condition, or only the planetary ones vanilla treats as hand-placeable.
    private static final String OFFER_ALL_CONDITIONS_FIELD =
            "kmu_conditionsShowAll";

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
    // On by default: a destabilised colony should hold less of its system than a
    // functioning one; the toggle exists to opt back into raw-size dominance.
    private static final boolean DEFAULT_STABILITY_WEIGHS_DOMINANCE = true;
    // Mirrors both the CSV defaultValue and VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
    // the geometric default this setting overrides; kept a literal like the other
    // fallbacks so this class stays decoupled from the geometry library.
    private static final int DEFAULT_CELL_BOUND_SEGMENTS = 48;
    // Border-tracing knobs (ungated).
    private static final double DEFAULT_BORDER_WELD_TOLERANCE = 100.0;
    private static final double DEFAULT_BORDER_MITER_LIMIT = 4.0;
    // Spike-sanding gate then its knobs; the gate leaves the pass off by default.
    private static final boolean DEFAULT_SAND_SPIKES = false;
    private static final double DEFAULT_BORDER_SPIKE_HEIGHT = 150.0;
    private static final double DEFAULT_BORDER_SPIKE_ANGLE_DEGREES = 60.0;
    // Corner-rounding gate then its knobs; likewise on by default.
    private static final boolean DEFAULT_ROUND_CORNERS = true;
    private static final double DEFAULT_BORDER_CORNER_RADIUS = 300.0;
    private static final int DEFAULT_BORDER_CORNER_SEGMENTS = 3;
    private static final double DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES = 35.0;
    // Label-anchor search knobs.
    private static final int DEFAULT_ANCHOR_DIRECTION_COUNT = 9;
    private static final int DEFAULT_ANCHOR_OFFSET_COUNT = 15;
    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH = 0.2;
    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT = 2.0;
    private static final double DEFAULT_ANCHOR_END_INSET_MULTIPLE = 4.0;
    private static final double DEFAULT_ANCHOR_ICON_CLEARANCE = 750.0;
    private static final boolean DEFAULT_SHOW_CLUSTER_ANCHORS = false;
    private static final boolean DEFAULT_SHOW_REJECTED_AXES = false;
    private static final boolean DEFAULT_SHOW_UNBIASED_AXES = false;
    private static final boolean DEFAULT_DEBUG_BORDER_TRACING = false;
    // On by default: the picker is a hands-on condition manager, so it lists every
    // condition unless the player narrows it to vanilla's planetary set.
    private static final boolean DEFAULT_OFFER_ALL_CONDITIONS = true;

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

    /**
     * @return whether each colony's dominance contribution is scaled by its
     *         stability - at 0 stability a colony holds no political weight, at 10
     *         its full size; on by default, off ranks colonies by raw size alone
     */
    public static boolean shouldWeighDominanceByStability() {
        return LunaSettingsReader.getBoolean(MOD_ID, STABILITY_WEIGHS_DOMINANCE_FIELD,
                DEFAULT_STABILITY_WEIGHS_DOMINANCE);
    }

    /**
     * @return the sides of the polygon that rounds each system cell's outer
     *         frontier - its reach into empty space; higher is smoother but adds a
     *         vertex per segment to every frontier cell (a map-FPS cost), lower
     *         trades a faceted frontier for fewer vertices
     */
    public static int getPoliticalMapCellBoundSegments() {
        return LunaSettingsReader.getInt(MOD_ID, CELL_BOUND_SEGMENTS_FIELD,
                DEFAULT_CELL_BOUND_SEGMENTS);
    }

    /**
     * @return the corner-rounding radius of the national border, in world
     *         units; higher rounds the cluster outline more
     */
    public static double getPoliticalMapBorderCornerRadius() {
        return LunaSettingsReader.getDouble(MOD_ID, BORDER_CORNER_RADIUS_FIELD,
                DEFAULT_BORDER_CORNER_RADIUS);
    }

    /**
     * @return the arc segments per rounded corner of the national border; higher is
     *         smoother
     */
    public static int getPoliticalMapBorderCornerSegments() {
        return LunaSettingsReader.getInt(MOD_ID, BORDER_CORNER_SEGMENTS_FIELD,
                DEFAULT_BORDER_CORNER_SEGMENTS);
    }

    /**
     * @return the interior angle below which a national-border corner is chamfered
     *         flat rather than rounded, in radians (the setting is authored in
     *         degrees and converted here, since the rounding math works in radians)
     */
    public static double getPoliticalMapBorderChamferAngleRadians() {
        return Math.toRadians(LunaSettingsReader.getDouble(MOD_ID, BORDER_CHAMFER_ANGLE_FIELD,
                DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES));
    }

    /**
     * @return how far apart two outline points may be and still weld into one corner
     *         when chaining the national border, in world units; raised if borders
     *         go missing, lowered if distinct corners merge
     */
    public static double getPoliticalMapBorderWeldTolerance() {
        return LunaSettingsReader.getDouble(MOD_ID, BORDER_WELD_TOLERANCE_FIELD,
                DEFAULT_BORDER_WELD_TOLERANCE);
    }

    /**
     * @return the multiple of the border inset past which a sharp corner's miter is
     *         bevelled instead of pointed; lower bevels sooner (rounder corners,
     *         no inward spikes), higher keeps crisper points
     */
    public static double getPoliticalMapBorderMiterLimit() {
        return LunaSettingsReader.getDouble(MOD_ID, BORDER_MITER_LIMIT_FIELD,
                DEFAULT_BORDER_MITER_LIMIT);
    }

    /**
     * @return the depth (world units) up to which a sharp corner counts as a spike
     *         to sand off the border before rounding; a sharp corner that juts farther
     *         than this is real shape and is kept. Zero disables the spike pass
     */
    public static double getPoliticalMapBorderSpikeHeight() {
        return LunaSettingsReader.getDouble(MOD_ID, BORDER_SPIKE_HEIGHT_FIELD,
                DEFAULT_BORDER_SPIKE_HEIGHT);
    }

    /**
     * @return the interior angle (radians) below which a shallow corner counts as a
     *         spike to sand off the border before rounding; a gentler corner is kept.
     *         Zero disables the spike pass
     */
    public static double getPoliticalMapBorderSpikeAngleRadians() {
        return Math.toRadians(LunaSettingsReader.getDouble(MOD_ID, BORDER_SPIKE_ANGLE_FIELD,
                DEFAULT_BORDER_SPIKE_ANGLE_DEGREES));
    }

    /**
     * @return whether the national-border corner rounding runs; on by default. When
     *         off the resolved envelope keeps its sharp corners and the radius,
     *         segments, and chamfer knobs go unread. Also gates the factionless
     *         (decivilised and uninhabited) cell outlines, which reuse the same
     *         corner-rounding pass, so the whole map's corners round or not together
     */
    public static boolean shouldRoundBorderCorners() {
        return LunaSettingsReader.getBoolean(MOD_ID, ROUND_CORNERS_FIELD,
                DEFAULT_ROUND_CORNERS);
    }

    /**
     * @return whether the spike-sanding pass runs before corner rounding; on by
     *         default. When off the spike height and angle knobs go unread and needle
     *         or cusp protrusions are left in the border for the rounding to meet
     */
    public static boolean shouldSandBorderSpikes() {
        return LunaSettingsReader.getBoolean(MOD_ID, SAND_SPIKES_FIELD,
                DEFAULT_SAND_SPIKES);
    }

    /**
     * @return how many directions the label-anchor search fans over the half-circle
     *         (0..180 degrees, since a label line is undirected); more directions
     *         let the accepted line align more closely with the cluster's open space
     *         at a higher search cost. Pure horizontal and the cluster's own principal
     *         axis are always searched on top of the fan
     */
    public static int getPoliticalMapAnchorDirectionCount() {
        return LunaSettingsReader.getInt(MOD_ID, ANCHOR_DIRECTION_COUNT_FIELD,
                DEFAULT_ANCHOR_DIRECTION_COUNT);
    }

    /**
     * @return how many parallel lines the search sweeps across the cluster per
     *         direction, spaced evenly over the cluster's extent perpendicular to
     *         that direction; this is what lets the accepted line slide off the
     *         centroid into a roomier part of the cluster. 1 degenerates to a single
     *         centred line per direction
     */
    public static int getPoliticalMapAnchorOffsetCount() {
        return LunaSettingsReader.getInt(MOD_ID, ANCHOR_OFFSET_COUNT_FIELD,
                DEFAULT_ANCHOR_OFFSET_COUNT);
    }

    /**
     * @return how much length a shallower (more horizontal) candidate line may give
     *         up and still win the search, 0..1: a candidate's clear length is scaled
     *         by {@code 1 - strength * sin(angle)^exponent}, so 0 picks the pure
     *         longest line regardless of slope and 1 scores a vertical line zero
     */
    public static double getPoliticalMapAnchorVerticalPenaltyStrength() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD,
                DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH);
    }

    /**
     * @return how sharply the vertical penalty concentrates toward vertical: the
     *         exponent on {@code sin(angle)} in the score, at least 1. A higher
     *         exponent leaves already-shallow lines almost unpenalised and bites only
     *         as a line approaches vertical, the falloff a flat vertical-component
     *         scale got backwards
     */
    public static double getPoliticalMapAnchorVerticalPenaltyExponent() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD,
                DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT);
    }

    /**
     * @return how far short of the national border each end of a label anchor stops,
     *         in multiples of the border inset channel - the gap a faction name
     *         needs so it does not touch the border; a clear line shorter than twice
     *         this collapses to the dot
     */
    public static double getPoliticalMapAnchorEndInsetMultiple() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_END_INSET_MULTIPLE_FIELD,
                DEFAULT_ANCHOR_END_INSET_MULTIPLE);
    }

    /**
     * @return the keep-out radius around each system icon that a label anchor must
     *         not cross, in world units - a tuned approximation of the icon's on-map
     *         footprint, since icons draw at a fixed pixel size while the anchor is
     *         fitted once in world space
     */
    public static double getPoliticalMapAnchorIconClearance() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_ICON_CLEARANCE_FIELD,
                DEFAULT_ANCHOR_ICON_CLEARANCE);
    }

    /**
     * @return whether the political map draws the per-cluster label anchors - a dot at
     *         each contiguous cluster's centre and, in green, the accepted label line
     *         its fit produced; off by default, a diagnostic for the coming faction
     *         labels. Master switch for the anchor overlay: the rejected- and
     *         unbiased-axis toggles below only add lines while this is on
     */
    public static boolean getPoliticalMapShowClusterAnchors() {
        return LunaSettingsReader.getBoolean(MOD_ID, SHOW_CLUSTER_ANCHORS_FIELD,
                DEFAULT_SHOW_CLUSTER_ANCHORS);
    }

    /**
     * @return whether a cluster whose accepted label line collapsed to the dot also
     *         draws, in red, the best candidate line its fit found before the border,
     *         icon, or end-margin trim discarded it - how close the cluster came to
     *         carrying a line; off by default, meaningful only while the cluster
     *         anchors themselves draw
     */
    public static boolean getPoliticalMapShowRejectedAxes() {
        return LunaSettingsReader.getBoolean(MOD_ID, SHOW_REJECTED_AXES_FIELD,
                DEFAULT_SHOW_REJECTED_AXES);
    }

    /**
     * @return whether each cluster also draws, in yellow, the label line its fit would
     *         accept with no horizontal bias applied - the anchor bias knob's effect
     *         made visible by contrast. Drawn only when the unbiased direction actually
     *         differs from the accepted line's (with the bias at 1, or an axis already
     *         horizontal, the accepted line is the unbiased line); off by default,
     *         meaningful only while the cluster anchors themselves draw
     */
    public static boolean getPoliticalMapShowUnbiasedAxes() {
        return LunaSettingsReader.getBoolean(MOD_ID, SHOW_UNBIASED_AXES_FIELD,
                DEFAULT_SHOW_UNBIASED_AXES);
    }

    /**
     * @return whether to replace the normal political-map render with the border-tracing
     *         diagnostic that layers the smoothing pipeline's stages (base, despiked,
     *         rounded) in distinct colors; off by default. Respects the two smoothing
     *         gates, so a stage draws only when its pass ran
     */
    public static boolean shouldTraceBordersForDebug() {
        return LunaSettingsReader.getBoolean(MOD_ID, DEBUG_BORDER_TRACING_FIELD,
                DEFAULT_DEBUG_BORDER_TRACING);
    }

    /**
     * @return whether the market-condition picker offers every condition, or only
     *         the planetary ones vanilla treats as hand-placeable; on by default,
     *         so non-planetary conditions (such as decivilisation) are offered too
     */
    public static boolean shouldOfferAllConditions() {
        return LunaSettingsReader.getBoolean(MOD_ID, OFFER_ALL_CONDITIONS_FIELD,
                DEFAULT_OFFER_ALL_CONDITIONS);
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
