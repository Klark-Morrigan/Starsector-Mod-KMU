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
 * each raw Voronoi cell's rounded reach into empty space - and two reveal overrides
 * that widen what the map draws for inspection: show-all-factions, which draws
 * undiscovered colonies too, and force-all-systems, which seeds a cell for every star
 * system. All three reseed the geometry rather than restyling it, so they feed the
 * geometry rebuild rather than the drawables restyle; the frontier resolution trades
 * map framerate against frontier smoothness, and the two overrides open up the whole
 * sector's politics or the full cell partition.
 *
 * <p>The "Political map - domination" tab holds the dominance rules - fields that change the
 * map's political verdicts rather than its styling, which is why they do not sit
 * under "Visuals customisation". They set how heavily a colony's raw size weighs on
 * its system's dominant faction, whether stability further scales that contribution,
 * and whether (and by how many size points) an attached defensive station lifts it
 * (paired with the fraction of that station weight a hidden base earns); the size and
 * station weights default to 1 and the two weighting toggles are on by default. The
 * tab groups them under Colony size, Stability, and Orbital stations headers.
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

    // Faction-name label field (Political map - visuals tab): whether each contiguous
    // faction cluster draws its owner's name across it, HOI4-style. On by default - the
    // names are the point of the merged-territory look, not a diagnostic.
    private static final String SHOW_FACTION_NAMES_FIELD =
            "kmu_politicalMapShowFactionNames";
    // The bitmap font faction names render in, chosen from the game's graphics/fonts by
    // basename; the label renderer resolves the basename to its .fnt path.
    private static final String FACTION_NAME_FONT_FIELD =
            "kmu_politicalMapFactionNameFont";
    // Whether each cluster label spells its owner's full name or its short name; the
    // short form fits a tighter cluster at a larger font.
    private static final String FACTION_NAME_FORMAT_FIELD =
            "kmu_politicalMapFactionNameFormat";
    // Overlay sidebar fields (Political map - visuals tab): the small on-map box carrying
    // the overlay's on/off tab. Anchor is where the box sits on screen; opacity is its
    // background translucency.
    private static final String SIDEBAR_ANCHOR_FIELD =
            "kmu_politicalMapSidebarAnchor";
    private static final String SIDEBAR_OPACITY_FIELD =
            "kmu_politicalMapSidebarOpacity";

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
    // faction. Not styling fields - they change the political verdicts themselves.
    // The colony-size weight multiplies each market's base size rating; the stability
    // toggle then scales that rating; the station toggle adds the station weight in
    // size points for an attached defensive station, paired with the fraction of that
    // weight a hidden base earns. All fold into one DominanceWeighting read once per
    // resolution pass.
    private static final String COLONY_SIZE_WEIGHT_FIELD =
            "kmu_politicalMapColonySizeWeight";
    private static final String STABILITY_WEIGHS_DOMINANCE_FIELD =
            "kmu_politicalMapStabilityWeighsDominance";
    private static final String STATION_WEIGHS_DOMINANCE_FIELD =
            "kmu_politicalMapStationWeighsDominance";
    private static final String STATION_WEIGHT_FIELD =
            "kmu_politicalMapStationWeight";
    private static final String STATION_HIDDEN_MARKET_RATE_FIELD =
            "kmu_politicalMapStationHiddenMarketRate";

    // Cell geometry (Dev tab): the resolution of the raw Voronoi cells, upstream of
    // any border shaping. Unlike the border fields below - which restyle fixed
    // geometry through the drawables rebuild - this reseeds the cells themselves, so
    // it feeds the geometry rebuild. Every segment is a vertex on each frontier cell,
    // so it is the lever for trading map FPS against frontier smoothness.
    private static final String CELL_BOUND_SEGMENTS_FIELD =
            "kmu_politicalMapCellBoundSegments";

    // Cell reach (visuals tab): how far each system's territory extends into empty space
    // before the frontier bound closes it off. A player-facing appearance knob - it sets
    // how much open space each system colours in - so it sits on the visuals tab, not the
    // Dev resolution knob above. Like the resolution it reseeds the cells, so it feeds the
    // geometry rebuild rather than the drawables restyle.
    private static final String CELL_RADIUS_FIELD =
            "kmu_politicalMapCellRadius";

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
    // shape how much a line straying from the cluster's own lean may sacrifice in length
    // and still win, so slant preference is decided on measured lengths rather than by
    // bending any direction before the fit; the max-slant degrees cap that lean short of
    // vertical (and it fades to level for round clusters whose axis is meaningless).
    private static final String ANCHOR_DIRECTION_COUNT_FIELD =
            "kmu_politicalMapAnchorDirectionCount";
    private static final String ANCHOR_OFFSET_COUNT_FIELD =
            "kmu_politicalMapAnchorOffsetCount";
    private static final String ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD =
            "kmu_politicalMapAnchorVerticalPenaltyStrength";
    private static final String ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD =
            "kmu_politicalMapAnchorVerticalPenaltyExponent";
    private static final String ANCHOR_MAX_SLANT_DEGREES_FIELD =
            "kmu_politicalMapAnchorMaxSlantDegrees";
    private static final String ANCHOR_END_INSET_MULTIPLE_FIELD =
            "kmu_politicalMapAnchorEndInsetMultiple";
    private static final String ANCHOR_ICON_CLEARANCE_FIELD =
            "kmu_politicalMapAnchorIconClearance";
    // Debug band-quad knobs (Dev tab, Label anchors): the opacity is the band quad's
    // fill alpha, and the line opacity the separate alpha of that box's strokes so the
    // outline can read stronger than the fill it sits on.
    private static final String ANCHOR_BAND_OPACITY_FIELD =
            "kmu_politicalMapAnchorBandOpacity";
    private static final String ANCHOR_BAND_LINE_OPACITY_FIELD =
            "kmu_politicalMapAnchorBandLineOpacity";
    // Name-fit knobs (Political map - visuals tab, Faction names): a label is a box with
    // girth, sized to the space it sits in and to the owner's actual name - player-facing
    // appearance, so they live beside the name toggle and font, not among the Dev
    // diagnostics. Min/max font size clamp the per-line height the fit searches (the
    // readability floor and the oversize ceiling); max lines and line spacing let a
    // length-poor cluster wrap the name into a taller-font block instead of shrinking it.
    private static final String NAME_MIN_FONT_SIZE_FIELD =
            "kmu_politicalMapNameMinFontSize";
    private static final String NAME_MAX_FONT_SIZE_FIELD =
            "kmu_politicalMapNameMaxFontSize";
    private static final String NAME_MAX_LINES_FIELD =
            "kmu_politicalMapNameMaxLines";
    private static final String NAME_LINE_SPACING_FIELD =
            "kmu_politicalMapNameLineSpacing";
    // Reveal overrides (Dev tab): two toggles that widen what the map draws for
    // inspection, each bypassing a normal gate. Show-all-factions drops the
    // known-to-player footprint filter so undiscovered colonies count toward
    // dominance, inhabitation, and geometry; force-all-systems bypasses the map
    // visibility rule so every star system seeds a cell. Both off by default. They
    // reseed the geometry (they change which systems get a cell), so the plugin
    // treats them like the frontier resolution - a change forces a geometry rebuild,
    // not just the drawables restyle a styling setting triggers.
    private static final String SHOW_ALL_FACTIONS_FIELD =
            "kmu_politicalMapShowAllFactions";
    private static final String FORCE_ALL_SYSTEMS_ON_MAP_FIELD =
            "kmu_politicalMapForceAllSystemsOnMap";
    // Diagnostics (Dev tab): draws the per-cluster label anchors (a centre dot and the
    // accepted label line in green) so the clustering and axis fit behind the faction
    // labels can be eyeballed on the map. Off by default.
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
    // On by default: the faction names are the payoff of the merged-territory map, so
    // they show unless the player turns them off.
    private static final boolean DEFAULT_SHOW_FACTION_NAMES = true;
    // Top-left by default: the least-used corner of the sector map, so the box starts clear
    // of the systems the player reads. Mirrors the CSV row's defaultValue.
    private static final SidebarAnchorChoice DEFAULT_SIDEBAR_ANCHOR = SidebarAnchorChoice.TOP_LEFT;
    // 80% opaque by default: readable over the map without fully masking what is behind it.
    // Stored 0..100 in the CSV, exposed 0..1. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_OPACITY_PERCENT = 80;
    private static final int MIN_SIDEBAR_OPACITY_PERCENT = 0;
    private static final int MAX_SIDEBAR_OPACITY_PERCENT = 100;
    // The default label font: the highest-resolution antialiased LazyFont face the game
    // ships (a 42px glyph atlas). A name is stretched far past its atlas resolution to
    // span a cluster, so the magnified glyphs stay as clean as a bitmap face allows only
    // when the source atlas is large - hence the biggest one as the default, and why the
    // radio offers only large antialiased faces. Also the renderer's fallback before
    // LunaLib has loaded the choice. Kept in step with the CSV row's defaultValue and the
    // font list the radio offers.
    private static final String DEFAULT_FACTION_NAME_FONT = "insignia42LTaa";
    // Full names by default: a cluster label spells the owner's long-form name, the
    // richer reading. The short form is opt-in for tighter clusters. Mirrors the CSV
    // row's defaultValue and the label list the radio offers.
    private static final FactionNameFormatChoice DEFAULT_FACTION_NAME_FORMAT =
            FactionNameFormatChoice.FULL;
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
    // The identity weight by default: raw colony size counts toward dominance as it
    // does without the rule. Below 1 flattens the gap between large and small
    // colonies, above 1 sharpens it; it scales a hidden base's fixed token too.
    private static final double DEFAULT_COLONY_SIZE_WEIGHT = 1.0;
    // On by default: a destabilised colony should hold less of its system than a
    // functioning one; the toggle exists to opt back into raw-size dominance.
    private static final boolean DEFAULT_STABILITY_WEIGHS_DOMINANCE = true;
    // On by default: an attached defensive station is real military presence, so it
    // lifts a stationed colony's hold on its system by the station weight in size
    // points; off ranks markets by stability-weighted size alone.
    private static final boolean DEFAULT_STATION_WEIGHS_DOMINANCE = true;
    // One size point by default: an attached station is worth a single colony size
    // point toward its system's dominance. Below 1 softens the bonus, above 1
    // sharpens it.
    private static final double DEFAULT_STATION_WEIGHT = 1.0;
    // A hidden base earns half the station weight, so a fortified secret base reads
    // as more than a bare concealed outpost without matching an open stationed colony.
    private static final double DEFAULT_STATION_HIDDEN_MARKET_RATE = 0.5;
    // Mirrors both the CSV defaultValue and VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
    // the geometric default this setting overrides; kept a literal like the other
    // fallbacks so this class stays decoupled from the geometry library.
    private static final int DEFAULT_CELL_BOUND_SEGMENTS = 48;
    // The default cell reach into empty space, world units - the constant the geometry
    // used before this became a knob. Mirrors the CSV defaultValue.
    private static final double DEFAULT_CELL_RADIUS = 4000.0;
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
    private static final double DEFAULT_ANCHOR_MAX_SLANT_DEGREES = 22.0;
    private static final double DEFAULT_ANCHOR_END_INSET_MULTIPLE = 4.0;
    private static final double DEFAULT_ANCHOR_ICON_CLEARANCE = 750.0;
    // Name-fit defaults, in world units where a distance. The font-size clamp brackets
    // a readable line against the map's scale (the border inset channel is 150); three
    // lines is the HOI4-style ceiling; 1.15 leads the lines with a little air.
    private static final double DEFAULT_NAME_MIN_FONT_SIZE = 200.0;
    private static final double DEFAULT_NAME_MAX_FONT_SIZE = 1200.0;
    private static final int DEFAULT_NAME_MAX_LINES = 3;
    private static final double DEFAULT_NAME_LINE_SPACING = 1.15;
    private static final double DEFAULT_ANCHOR_BAND_OPACITY = 0.35;
    private static final double DEFAULT_ANCHOR_BAND_LINE_OPACITY = 0.9;
    // Both reveal overrides off by default: the map draws exactly what the normal
    // gates admit until the player opts into a wider view.
    private static final boolean DEFAULT_SHOW_ALL_FACTIONS = false;
    private static final boolean DEFAULT_FORCE_ALL_SYSTEMS_ON_MAP = false;
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
     * @return the multiplier on each colony's base size rating - a visible market's
     *         own size or a hidden base's fixed presence token - before the station
     *         bonus and stability fold in; 1.0 by default (raw size), below 1 flattens
     *         the gap between large and small colonies and above 1 sharpens it
     */
    public static double getColonySizeWeight() {
        return LunaSettingsReader.getDouble(MOD_ID, COLONY_SIZE_WEIGHT_FIELD,
                DEFAULT_COLONY_SIZE_WEIGHT);
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
     * @return whether a market with an attached defensive station gains one size
     *         point toward its system's dominance, added before stability scales the
     *         sum; on by default, off ranks markets by stability-weighted size alone
     */
    public static boolean shouldWeighDominanceByStation() {
        return LunaSettingsReader.getBoolean(MOD_ID, STATION_WEIGHS_DOMINANCE_FIELD,
                DEFAULT_STATION_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points an attached defensive station adds to a visible colony's
     *         dominance contribution, before stability scales the sum; 1.0 by default,
     *         below 1 softens the station bonus and above 1 sharpens it. A hidden base
     *         earns this scaled by the station hidden-base rate
     */
    public static double getStationWeight() {
        return LunaSettingsReader.getDouble(MOD_ID, STATION_WEIGHT_FIELD,
                DEFAULT_STATION_WEIGHT);
    }

    /**
     * @return the fraction of the station weight a hidden (concealed) base earns
     *         on its token presence rating, 0..1 - so a fortified secret base reads
     *         above a bare outpost without matching an openly held stationed colony;
     *         half by default
     */
    public static double getStationHiddenMarketRate() {
        return LunaSettingsReader.getDouble(MOD_ID, STATION_HIDDEN_MARKET_RATE_FIELD,
                DEFAULT_STATION_HIDDEN_MARKET_RATE);
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
     * @return how far each system's territory reaches into empty space before its
     *         frontier bound closes it off, in world units; higher lets each system
     *         colour more open space (so distant systems' territories meet and merge),
     *         lower pulls every territory in tight around its own systems
     */
    public static double getPoliticalMapCellRadius() {
        return LunaSettingsReader.getDouble(MOD_ID, CELL_RADIUS_FIELD, DEFAULT_CELL_RADIUS);
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
     *         at a higher search cost. Pure horizontal, the cluster's own principal
     *         axis, and the preferred slant are always searched on top of the fan
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
     * @return how far a label may lean off level to follow its cluster's long axis, in
     *         degrees. The anchor search prefers this cluster-specific slant over
     *         screen-horizontal, capped here so a tall cluster never stands its name
     *         vertical and faded toward level as a cluster gets rounder; 0 forces
     *         dead-horizontal labels, 90 lets a label follow its axis to vertical
     */
    public static double getPoliticalMapAnchorMaxSlantDegrees() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_MAX_SLANT_DEGREES_FIELD,
                DEFAULT_ANCHOR_MAX_SLANT_DEGREES);
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
     * @return the smallest per-line font height (world units) a faction name may render
     *         at - the readability floor. A placement that cannot hold even one line
     *         this tall anywhere collapses to the dot and shows no name
     */
    public static double getPoliticalMapNameMinFontSize() {
        return LunaSettingsReader.getDouble(MOD_ID, NAME_MIN_FONT_SIZE_FIELD,
                DEFAULT_NAME_MIN_FONT_SIZE);
    }

    /**
     * @return the largest per-line font height (world units) the name fit will grow to,
     *         so a roomy cluster does not mint an oversized label; the upper bound of
     *         the font-height search
     */
    public static double getPoliticalMapNameMaxFontSize() {
        return LunaSettingsReader.getDouble(MOD_ID, NAME_MAX_FONT_SIZE_FIELD,
                DEFAULT_NAME_MAX_FONT_SIZE);
    }

    /**
     * @return the most lines the fit may wrap a name into: a length-poor but girth-rich
     *         cluster wraps the name to shorten its widest line and spends the spare
     *         girth, chosen only when that renders a strictly larger font than fewer
     *         lines would. 1 forces single-line names
     */
    public static int getPoliticalMapNameMaxLines() {
        return LunaSettingsReader.getInt(MOD_ID, NAME_MAX_LINES_FIELD,
                DEFAULT_NAME_MAX_LINES);
    }

    /**
     * @return the line-height multiple between a multi-line name's stacked lines, so a
     *         two- or three-line block is that much taller than the raw line heights;
     *         at least 1 (lines flush)
     */
    public static double getPoliticalMapNameLineSpacing() {
        return LunaSettingsReader.getDouble(MOD_ID, NAME_LINE_SPACING_FIELD,
                DEFAULT_NAME_LINE_SPACING);
    }

    /**
     * @return the fill opacity of the debug band quad, 0..1 - low enough that the
     *         national border reads through the band so an overflow is visible, since
     *         the whole point of drawing the band is to see it kiss or clear the border
     */
    public static double getPoliticalMapAnchorBandOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_BAND_OPACITY_FIELD,
                DEFAULT_ANCHOR_BAND_OPACITY);
    }

    /**
     * @return the opacity of the debug band box's strokes - its outline, the line-count
     *         divider rules, the centreline, and the anchor dot - 0..1, kept separate
     *         from the fill alpha so the outline stays legible over a faint band wash
     */
    public static double getPoliticalMapAnchorBandLineOpacity() {
        return LunaSettingsReader.getDouble(MOD_ID, ANCHOR_BAND_LINE_OPACITY_FIELD,
                DEFAULT_ANCHOR_BAND_LINE_OPACITY);
    }

    /**
     * @return whether each contiguous faction cluster draws its owner's name across it,
     *         HOI4-style; on by default. Independent of the debug anchor overlay - the
     *         two share the placement search but draw under their own toggles
     */
    public static boolean getPoliticalMapShowFactionNames() {
        return LunaSettingsReader.getBoolean(MOD_ID, SHOW_FACTION_NAMES_FIELD,
                DEFAULT_SHOW_FACTION_NAMES);
    }

    /**
     * @return the basename of the {@code graphics/fonts} face the faction names render
     *         in (e.g. {@code insignia42LTaa}); the label renderer resolves it to the
     *         {@code .fnt} path. Falls back to the default face before LunaLib has loaded
     *         the choice
     */
    public static String getPoliticalMapFactionNameFont() {
        return LunaSettingsReader.getString(MOD_ID, FACTION_NAME_FONT_FIELD,
                DEFAULT_FACTION_NAME_FONT);
    }

    /**
     * @return which screen corner or edge midpoint the overlay sidebar box anchors to;
     *         the top-left corner by default
     */
    public static SidebarAnchorChoice getPoliticalMapSidebarAnchor() {
        return SidebarAnchorChoice.fromLabel(
                LunaSettingsReader.getString(MOD_ID, SIDEBAR_ANCHOR_FIELD,
                        DEFAULT_SIDEBAR_ANCHOR.getLabel()),
                DEFAULT_SIDEBAR_ANCHOR);
    }

    /**
     * @return the overlay sidebar box's background opacity as a 0..1 fraction (the CSV
     *         stores it as a 0..100 percentage); 0.8 by default
     */
    public static float getPoliticalMapSidebarBackgroundOpacity() {
        var percent = LunaSettingsReader.getInt(MOD_ID, SIDEBAR_OPACITY_FIELD,
                DEFAULT_SIDEBAR_OPACITY_PERCENT);
        var clamped = Math.max(MIN_SIDEBAR_OPACITY_PERCENT,
                Math.min(MAX_SIDEBAR_OPACITY_PERCENT, percent));
        return clamped / (float) MAX_SIDEBAR_OPACITY_PERCENT;
    }

    /**
     * @return whether each cluster label spells its owner's full name or its short
     *         name; the full (long-form) name by default
     */
    public static FactionNameFormatChoice getPoliticalMapFactionNameFormat() {
        return FactionNameFormatChoice.fromLabel(
                LunaSettingsReader.getString(MOD_ID, FACTION_NAME_FORMAT_FIELD,
                        DEFAULT_FACTION_NAME_FORMAT.getLabel()),
                DEFAULT_FACTION_NAME_FORMAT);
    }

    /**
     * @return whether the political map draws every faction's colonies, including ones
     *         the player has not discovered yet - bypasses the known-to-player gate so
     *         an undiscovered colony still folds into its system's dominance,
     *         inhabitation, and cell geometry; off by default, a reveal aid for
     *         inspecting the whole sector's politics
     */
    public static boolean getPoliticalMapShowAllFactions() {
        return LunaSettingsReader.getBoolean(MOD_ID, SHOW_ALL_FACTIONS_FIELD,
                DEFAULT_SHOW_ALL_FACTIONS);
    }

    /**
     * @return whether the political map seeds a cell for every star system, not just the
     *         reachable, visible, or inhabited ones - bypasses the visibility rule so a
     *         system the map would otherwise omit still gets geometry; off by default, a
     *         reveal aid for inspecting the full cell partition
     */
    public static boolean shouldForceAllSystemsOnMap() {
        return LunaSettingsReader.getBoolean(MOD_ID, FORCE_ALL_SYSTEMS_ON_MAP_FIELD,
                DEFAULT_FORCE_ALL_SYSTEMS_ON_MAP);
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
