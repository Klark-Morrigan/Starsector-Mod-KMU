package kmu.settings;

import kmlib.logging.KmLogging;
import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;
import kmlib.settings.LunaSettingsReader;

import kmu.KmuMod;

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
 * <p>Many field ids read as the political map's - {@code kmu_politicalMapSidebar*},
 * {@code kmu_politicalMapAnchor*}, {@code kmu_politicalMapBorderWeldTolerance} - while
 * the getters over them are named for the map-layer framework that reads them
 * ({@code getMapSidebar*}, {@code getMapAnchor*}, {@code getMapBorderWeldTolerance}).
 * The ids predate the framework and cannot follow it: a LunaLib field id is the key its
 * value is stored under, so renaming one resets that setting for every existing player,
 * exactly as a persisted class or memory key cannot be renamed. A getter is a Java name
 * and costs nothing to change, so the mismatch is parked where it does no harm - the
 * Java side says which half of the map code owns a knob, the stored key stays put.
 *
 * <p>The tabs the fields are laid out into follow the same split as the packages that
 * read them, so a player hunting a knob is asked the one question the code already
 * answers: is this the map framework's chrome or one layer's paint? {@code Map - Visuals}
 * carries what every map layer shares - the overlay sidebar, the map labels, the hover
 * switches every layer answers to - and {@code Map - Politics - Visuals} carries the
 * political map's own palette, its own hover switches included.
 * {@code Map - Politics - Domination} and {@code Map - Keybinds} follow the same reading.
 * The prefix is what makes the grouping legible, so a tab that is not a map feature (the
 * condition picker's) deliberately does not take it.
 *
 * <p>The political-map fields, matching data/config/LunaSettings.csv, style each
 * category of system on the sector map. Owned categories - core factions and
 * independent space - each get a fill, an outer (national) border, and an inner
 * (province seam) border, every one with a palette-colour choice, an opacity, and
 * (for the borders) a line width. Factionless categories - decivilised and
 * uninhabited systems - have no faction palette to choose from, so they carry no
 * colour field at all and always paint in the shared neutral colour: decivilised
 * systems get an outline opacity and width plus a fill opacity, and uninhabited
 * systems just the outline pair. Each opacity doubles as that element's on/off,
 * since zero opacity is the only way to hide a shade with no alternative.
 * Whether the uninhabited outline draws at all is the on-map sidebar's checkbox
 * rather than a field here, so the setting screen never duplicates that control.
 * All are tuned under the LunaLib "Map - Politics - Visuals" tab.
 *
 * <p>The national-border geometry is exposed separately under the "Dev" tab: it
 * shapes the frontier rather than recolouring it, so it is a tuning surface for
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
 * <p>The "Map - Politics - Domination" tab holds the dominance rules - fields that change the
 * map's political verdicts rather than its styling, which is why they do not sit
 * under "Map - Politics - Visuals". They set how heavily a colony's raw size weighs on
 * its system's dominant faction, how a hidden market's size is counted (its real
 * size or a fixed weight), whether (and by how many size points) an attached
 * defensive station lifts a colony (paired with the fraction a hidden market earns),
 * and whether the patrols a colony fields lift it too, weighted by patrol size.
 * Stability weighting is a master toggle over three per-factor low-stability penalties
 * (colony size, station, patrols) that each set how deeply their factor collapses at
 * zero stability. The tab groups the fields under Colony size, Hidden markets,
 * Stability, Orbital stations, and Garrison patrols headers.
 *
 * <p>The "Market Condition Manager (MCM)" tab holds the condition-picker toggles. Its one field
 * chooses whether the picker offers every market condition or only the planetary
 * ones vanilla treats as hand-placeable; it is on by default, so non-planetary
 * conditions (such as decivilisation) are offered too.
 *
 * <p>The "Map - Keybinds" tab holds the overlay's shortcut keys, grouped under a "Map Sidebar"
 * header: the keycodes that jump the layer bar straight to the No Layer and Factions
 * views. Each is a LunaLib Keycode control, so the player rebinds it in place or clears
 * it with Escape (stored as keycode 0, which the input handler and tab caption read as
 * unbound). They sit apart from the visuals because they are controls, not appearance.
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

    // Overlay sidebar fields (Map - Visuals tab): the small on-map box carrying
    // the overlay's tabs and controls. Padding places the box from the screen's top-left
    // corner; border width frames it (0 = no border); opacity is its background
    // translucency.
    private static final String SIDEBAR_PADDING_TOP_FIELD =
        "kmu_politicalMapSidebarPaddingTop";
    private static final String SIDEBAR_PADDING_LEFT_FIELD =
        "kmu_politicalMapSidebarPaddingLeft";
    private static final String SIDEBAR_PADDING_BOTTOM_FIELD =
        "kmu_politicalMapSidebarPaddingBottom";
    private static final String SIDEBAR_BORDER_WIDTH_FIELD =
        "kmu_politicalMapSidebarBorderWidth";
    private static final String SIDEBAR_OPACITY_FIELD =
        "kmu_politicalMapSidebarOpacity";

    // How long the sidebar's collapse handle takes to fold the body to its docked rail (and
    // unfold it), in seconds; 0 snaps it instantly. Player-facing animation pace, so it sits
    // in the Overlay sidebar section of the Map - Visuals tab beside the box's other appearance knobs.
    private static final String SIDEBAR_COLLAPSE_SECONDS_FIELD =
        "kmu_politicalMapSidebarCollapseSeconds";

    // Which colour the collapse handle's chevron draws in: the vanilla highlight gold, so the cue
    // stands off the frame it sits on, or the panel's own accents so the handle reads as chrome.
    // Appearance only, so it sits with the box's other looks in the Overlay sidebar section.
    private static final String SIDEBAR_CHEVRON_COLOR_FIELD =
        "kmu_politicalMapSidebarChevronColor";

    // Intel-screen overlay field (Map - Visuals tab): the same sidebar box drawn on the intel
    // screen sits flush against the left edge of that screen's map preview (the "visor") and hangs from
    // the visor top; this top padding pushes it down to clear the vanilla starscape / fuel-range toggles
    // at the top of the intel map. The visor's height caps the box, so no left or bottom knob is needed.
    private static final String INTEL_SIDEBAR_PADDING_TOP_FIELD =
        "kmu_politicalMapIntelSidebarPaddingTop";

    // Faction (core-faction cluster) style fields. Name opacity fades this group's cluster
    // names, applied where the name colour is resolved so the faction group can recede its
    // own names without touching independent space's. A fade, not a fit knob - it dims the
    // name colour without touching the box the fit sized.
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
    private static final String FACTION_NAME_OPACITY_FIELD = "kmu_politicalMapFactionNameOpacity";

    // Independent (independent-held cluster) style fields, the same shape as faction (its
    // name opacity fades only the independent group's names).
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
    private static final String INDEPENDENT_NAME_OPACITY_FIELD =
        "kmu_politicalMapIndependentNameOpacity";

    // Decivilised and uninhabited (factionless) style fields. Neither has a colour choice:
    // factionless ground has no faction palette to pick from, so it always paints in the
    // shared neutral colour and the opacity knobs alone decide what shows.
    private static final String DECIVILISED_BORDER_OPACITY_FIELD =
        "kmu_politicalMapDecivilisedBorderOpacity";
    private static final String DECIVILISED_BORDER_WIDTH_FIELD =
        "kmu_politicalMapDecivilisedBorderWidth";
    private static final String DECIVILISED_FILL_OPACITY_FIELD =
        "kmu_politicalMapDecivilisedFillOpacity";
    private static final String UNINHABITED_BORDER_OPACITY_FIELD =
        "kmu_politicalMapUninhabitedBorderOpacity";
    private static final String UNINHABITED_BORDER_WIDTH_FIELD =
        "kmu_politicalMapUninhabitedBorderWidth";

    // Recede styling settings (Map - Politics - Visuals tab): the two settings-screen knobs
    // supplementary to the sidebar recede toggles, shared across both recede sets (the spotlight
    // filter's faded background and the alliances view's non-allied ground). The muted-opacity
    // modifier is how far Mute dims a receded bloc's borders, fills, and name as a fraction of
    // normal opacity; the desaturation darkening is how far a desaturated bloc's uniform
    // Independent-based grey is sunk toward black, so the receded ground reads behind genuine
    // independent-held space. The toggles themselves are sidebar-only per-save choices (sector
    // memory), not LunaLib fields, since every LunaLib field would render on a settings tab.
    private static final String ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD =
        "kmu_politicalMapAllianceMutedOpacityModifier";
    private static final String DESATURATION_DARKENING_FIELD =
        "kmu_politicalMapDesaturationDarkening";

    // Hover tiers, the top two (Map - Visuals tab): whether the map answers the cursor at all,
    // and then whether each kind of answer does - both across every map layer. The master gates
    // both kinds, so with it off no layer reads the cursor and nothing hover-driven is drawn or
    // paid for. Under it sits one switch per kind of feedback: effects covers the halo and cell
    // wash a layer paints, the tooltip covers the info box naming what is under the cursor. Every
    // layer carries its own pair below these, so switching one layer's feedback off does not take
    // the other layers' with it. The lower two ids read as the political map's because they
    // predate the framework - the mismatch this class's note above explains.
    private static final String HOVERING_ENABLED_FIELD =
        "kmu_mapVisualsHoveringEnabled";
    private static final String HOVER_EFFECTS_ENABLED_FIELD =
        "kmu_politicalMapHoverEnabled";
    private static final String HOVER_TOOLTIP_ENABLED_FIELD =
        "kmu_politicalMapHoverTooltipEnabled";

    // Hover tiers, the political map's own pair (Map - Politics - Visuals tab): the same two kinds
    // of feedback scoped to this one layer's paint. Each ANDs with the global switch of its kind,
    // so a layer switch only takes away feedback the tiers above already allow - which is what
    // lets one layer keep its box while another's is off.
    private static final String POLITICAL_HOVER_EFFECTS_ENABLED_FIELD =
        "kmu_mapPoliticsVisualsHoverEffectsEnabled";
    private static final String POLITICAL_HOVER_TOOLTIP_ENABLED_FIELD =
        "kmu_mapPoliticsVisualsHoverTooltipEnabled";

    // Hover highlight styling (Map - Politics - Visuals tab): what the halo around the hovered
    // territory's frontier and the wash over the one hovered cell look like, both drawn in the
    // hovered ground's own palette colour. Whether they draw at all is the hover tiers above; these
    // shape them. The halo is a stack of strokes, so it takes a widest-layer width, an
    // innermost-layer opacity, a layer count, and a pulse (strength plus period); the wash
    // takes a fill opacity and its own outline opacity and width, since an interior cell reads
    // only by its trace. All feed the drawables rebuild, so a change repaints the highlight
    // live on the open map - which is the point of exposing them: the look is dialed in-engine
    // against real territory rather than guessed at build time.
    private static final String HOVER_HIGHLIGHT_COLOR_FIELD =
        "kmu_politicalMapHoverHighlightColor";
    private static final String HOVER_GLOW_OPACITY_FIELD =
        "kmu_politicalMapHoverGlowOpacity";
    private static final String HOVER_GLOW_WIDTH_FIELD =
        "kmu_politicalMapHoverGlowWidth";
    private static final String HOVER_GLOW_LAYERS_FIELD =
        "kmu_politicalMapHoverGlowLayers";
    private static final String HOVER_GLOW_PULSE_STRENGTH_FIELD =
        "kmu_politicalMapHoverGlowPulseStrength";
    private static final String HOVER_GLOW_PULSE_PERIOD_FIELD =
        "kmu_politicalMapHoverGlowPulsePeriod";
    private static final String HOVER_WASH_OPACITY_FIELD =
        "kmu_politicalMapHoverWashOpacity";
    private static final String HOVER_WASH_OUTLINE_OPACITY_FIELD =
        "kmu_politicalMapHoverWashOutlineOpacity";
    private static final String HOVER_WASH_OUTLINE_WIDTH_FIELD =
        "kmu_politicalMapHoverWashOutlineWidth";

    // Dominance rules (Map - Politics - Domination tab): how the map decides a system's
    // dominant faction. Not styling fields - they change the political verdicts
    // themselves. The colony-size weight multiplies each market's base size rating; a
    // hidden market takes its base rating from the hidden-market scaling choice
    // (its real size, or the fixed weight) rather than a hardcoded token; the station
    // toggle adds the station weight in size points for an attached defensive station,
    // paired with the fraction of that weight a hidden market earns; the patrol toggle
    // adds a size-point bonus for the small, medium, and large patrols a colony fields.
    // Stability weighting is a master toggle over three per-factor low-stability
    // penalties (colony size, station, patrols), each setting how deeply its factor
    // collapses at zero stability. All fold into one DominanceRules read once per
    // resolution pass.
    private static final String COLONY_SIZE_WEIGHT_FIELD =
        "kmu_politicalMapColonySizeWeight";
    private static final String HIDDEN_MARKET_SCALING_FIELD =
        "kmu_politicalMapHiddenMarketScaling";
    private static final String HIDDEN_MARKET_FIXED_WEIGHT_FIELD =
        "kmu_politicalMapHiddenMarketFixedWeight";
    private static final String STABILITY_WEIGHS_DOMINANCE_FIELD =
        "kmu_politicalMapStabilityWeighsDominance";
    private static final String NORMAL_LOW_STABILITY_PENALTY_FIELD =
        "kmu_politicalMapNormalLowStabilityPenalty";
    private static final String STATION_WEIGHS_DOMINANCE_FIELD =
        "kmu_politicalMapStationWeighsDominance";
    private static final String STATION_WEIGHT_FIELD =
        "kmu_politicalMapStationWeight";
    private static final String STATION_HIDDEN_MARKET_RATE_FIELD =
        "kmu_politicalMapStationHiddenMarketRate";
    private static final String STATION_LOW_STABILITY_PENALTY_FIELD =
        "kmu_politicalMapStationLowStabilityPenalty";
    private static final String PATROL_WEIGHS_DOMINANCE_FIELD =
        "kmu_politicalMapPatrolWeighsDominance";
    private static final String PATROL_SMALL_WEIGHT_FIELD =
        "kmu_politicalMapPatrolSmallWeight";
    private static final String PATROL_MEDIUM_WEIGHT_FIELD =
        "kmu_politicalMapPatrolMediumWeight";
    private static final String PATROL_LARGE_WEIGHT_FIELD =
        "kmu_politicalMapPatrolLargeWeight";
    private static final String PATROL_LOW_STABILITY_PENALTY_FIELD =
        "kmu_politicalMapPatrolLowStabilityPenalty";

    // Cell geometry (Dev tab): the resolution of the raw Voronoi cells, upstream of
    // any border shaping. Unlike the border fields below - which restyle fixed
    // geometry through the drawables rebuild - this reseeds the cells themselves, so
    // it feeds the geometry rebuild. Every segment is a vertex on each frontier cell,
    // so it is the lever for trading map FPS against frontier smoothness.
    private static final String CELL_BOUND_SEGMENTS_FIELD =
        "kmu_politicalMapCellBoundSegments";

    // Cell reach (Dev tab, Cell geometry): how far each system's territory extends into empty
    // space before the frontier bound closes it off. Sits beside the resolution knob above
    // rather than with the palette because the two answer the same question - the shape of the
    // cells the map is partitioned into - and both reseed the cells, so it feeds the geometry
    // rebuild rather than the drawables restyle.
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

    // Hatch fill (Dev tab): the diagonal line pattern that fills the filter's contested
    // cluster - the spotlighted bloc's present-but-dominated systems - so it reads as
    // "mine, but contested" against the solid cluster it holds outright. Only that one
    // territory hatches; spacing is the perpendicular gap between lines in world units,
    // angle their direction in degrees off horizontal, width the pixel stroke of each
    // line, and joining how many drawn segments one line's crossings of that ground are
    // packed into. All four feed the drawables rebuild, so a change repaints the hatch live.
    private static final String HATCH_SPACING_FIELD =
        "kmu_politicalMapHatchSpacing";
    private static final String HATCH_ANGLE_FIELD =
        "kmu_politicalMapHatchAngle";
    private static final String HATCH_WIDTH_FIELD =
        "kmu_politicalMapHatchWidth";
    private static final String HATCH_JOINING_FIELD =
        "kmu_politicalMapHatchJoining";

    // Hatch fill - coalesced (Dev tab): the one knob only the merging joining reads. Sits in
    // its own section because a knob that is inert under the other joining should not read as
    // part of the pattern every hatch has.
    private static final String HATCH_JOIN_TOLERANCE_FIELD =
        "kmu_politicalMapHatchJoinTolerance";

    // Label anchors (Dev tab): the modifiers of the per-cluster label-anchor search -
    // the straight line a cluster's name will sit on, chosen by scoring many candidate
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

    // Name-fit knobs (Map - Visuals tab, Map labels): a label is a box with
    // girth, sized to the space it sits in and to the owner's actual name - player-facing
    // appearance, so they live beside the name toggle and format, not among the Dev
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
    // accepted label line in green) so the clustering and axis fit behind the map
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
    // layers the smoothing pipeline's stages (base, despiked, rounded) in distinct colours,
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
    // Placement from the screen's top-left corner, pixels. The top padding clears the
    // sector map's own tab strip; the left padding gives a small margin. Mirror the CSV
    // rows' defaultValues.
    private static final int DEFAULT_SIDEBAR_PADDING_TOP = 46;
    private static final int DEFAULT_SIDEBAR_PADDING_LEFT = 12;

    // Kept clear at the screen bottom, pixels: the panel body caps its height so the box
    // never runs past this margin, and the bloc list scrolls within what is left. A small
    // margin like the left padding. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_PADDING_BOTTOM = 12;

    // Intel overlay top padding from the visor's top edge, pixels: clears the vanilla starscape /
    // fuel-range toggles at the top of the intel map by default. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_INTEL_SIDEBAR_PADDING_TOP = 40;

    // A one-pixel outer border by default; 0 hides it. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_BORDER_WIDTH = 1;

    // 80% opaque by default: readable over the map without fully masking what is behind it.
    // Stored 0..100 in the CSV, exposed 0..1. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_OPACITY_PERCENT = 80;
    private static final int MIN_SIDEBAR_OPACITY_PERCENT = 0;
    private static final int MAX_SIDEBAR_OPACITY_PERCENT = 100;

    // A quarter-second collapse by default. Kept a literal mirroring the CSV defaultValue and
    // TabPanelCollapse.DEFAULT_DURATION_SECONDS - the KMLib holder's own default pace - like the
    // other fallbacks, so this class stays decoupled from the widget library.
    private static final float DEFAULT_SIDEBAR_COLLAPSE_SECONDS = 0.25f;

    // The highlight gold by default: the collapse handle hangs off the frame over the map, so its
    // chevron reads clearest pitched against the panel's accents rather than painted in them.
    // Mirrors the CSV row's defaultValue.
    private static final NotchChevronColourChoice DEFAULT_SIDEBAR_CHEVRON_COLOUR =
        NotchChevronColourChoice.GOLD;
    private static final FactionPaletteChoice DEFAULT_FACTION_OUTER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_OUTER_BORDER_OPACITY = 1.0;
    private static final double DEFAULT_FACTION_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_INNER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_FACTION_INNER_BORDER_WIDTH = 5.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_FILL_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_FILL_OPACITY = 0.4;
    
    // Fully opaque by default: faction names draw at full colour strength unless faded.
    private static final double DEFAULT_FACTION_NAME_OPACITY = 1.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY = 0.5;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH = 5.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_FILL_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_FILL_OPACITY = 0.2;

    // Fully opaque by default: independent names draw at full colour strength unless faded.
    private static final double DEFAULT_INDEPENDENT_NAME_OPACITY = 1.0;
    private static final double DEFAULT_DECIVILISED_BORDER_OPACITY = 0.35;
    private static final double DEFAULT_DECIVILISED_BORDER_WIDTH = 3.0;

    // A faint wash by default: a dead colony is real ground, so it fills rather than reading
    // as a bare ring, but stays well behind a living faction's fill (0.4) and independent
    // space's (0.2 at full colour) since nothing holds it. Mirrors the CSV row's defaultValue.
    private static final double DEFAULT_DECIVILISED_FILL_OPACITY = 0.2;
    private static final double DEFAULT_UNINHABITED_BORDER_OPACITY = 0.15;
    private static final double DEFAULT_UNINHABITED_BORDER_WIDTH = 3.0;

    // Half strength by default: Mute dims a non-allied bloc to half its normal opacity.
    private static final double DEFAULT_ALLIANCE_MUTED_OPACITY_MODIFIER = 0.5;

    // 30% darker by default: a desaturated bloc paints the Independent grey sunk to 70% brightness,
    // a clear step behind genuine independent-held space without going so dark it reads as unowned.
    // Mirrors the CSV row's defaultValue.
    private static final double DEFAULT_DESATURATION_DARKENING = 0.3;

    // The identity weight by default: raw colony size counts toward dominance as it
    // does without the rule. Below 1 flattens the gap between large and small
    // colonies, above 1 sharpens it; it scales a hidden market's chosen base size too.
    private static final double DEFAULT_COLONY_SIZE_WEIGHT = 1.0;

    // Fixed by default: a hidden market folds in at its fixed weight (below) rather
    // than its real size, so a large secret base does not outweigh the open colonies
    // around it. Mirrors the CSV row's defaultValue and the labels the radio offers.
    private static final HiddenMarketScalingChoice DEFAULT_HIDDEN_MARKET_SCALING =
        HiddenMarketScalingChoice.FIXED;

    // One size point by default: a hidden market marks presence as a single size
    // point while its scaling is Fixed, matching the token the rule used before this
    // became a knob.
    private static final double DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT = 1.0;

    // On by default: a destabilised colony should hold less of its system than a
    // functioning one; the master toggle exists to opt back into raw-size dominance.
    private static final boolean DEFAULT_STABILITY_WEIGHS_DOMINANCE = true;

    // Full collapse by default: a colony's own size weight falls to nothing at zero
    // stability (half at 5, full at 10), the whole-size stability scaling the rule
    // applied before the penalty was per-factor.
    private static final double DEFAULT_NORMAL_LOW_STABILITY_PENALTY = 1.0;

    // On by default: an attached defensive station is real military presence, so it
    // lifts a stationed colony's hold on its system by the station weight in size
    // points; off ranks markets without any station bonus.
    private static final boolean DEFAULT_STATION_WEIGHS_DOMINANCE = true;

    // One size point by default: an attached station is worth a single colony size
    // point toward its system's dominance. Below 1 softens the bonus, above 1
    // sharpens it.
    private static final double DEFAULT_STATION_WEIGHT = 1.0;

    // A hidden market earns half the station weight, so a fortified secret base reads
    // as more than a bare unstationed hidden market without matching an open stationed
    // colony.
    private static final double DEFAULT_STATION_HIDDEN_MARKET_RATE = 0.5;

    // Half collapse by default: the station bonus falls to half its worth at zero
    // stability, so a destabilised fortress keeps some military weight rather than
    // losing it entirely with the colony's economy.
    private static final double DEFAULT_STATION_LOW_STABILITY_PENALTY = 0.5;

    // Off by default: patrol strength does not sway dominance until the player opts in.
    private static final boolean DEFAULT_PATROL_WEIGHS_DOMINANCE = false;

    // Modest per-tier defaults, tuned so a mid-size military colony adds roughly a
    // colony size point of patrol weight when the factor is enabled; freely tunable.
    private static final double DEFAULT_PATROL_SMALL_WEIGHT = 0.25;
    private static final double DEFAULT_PATROL_MEDIUM_WEIGHT = 0.5;
    private static final double DEFAULT_PATROL_LARGE_WEIGHT = 1.0;

    // Half collapse by default, like the station bonus: a garrison keeps half its
    // weight at zero stability rather than vanishing with the colony's economy.
    private static final double DEFAULT_PATROL_LOW_STABILITY_PENALTY = 0.5;

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

    // Hatch-fill knobs, mirroring the CSV defaults: ~10 lines across a default-reach cell,
    // laid on a 45-degree diagonal at a hairline stroke.
    private static final double DEFAULT_HATCH_SPACING = 400.0;
    private static final double DEFAULT_HATCH_ANGLE_DEGREES = 45.0;
    private static final double DEFAULT_HATCH_WIDTH = 1.0;

    // Coalesced by default: a stroke that never leaves the cluster comes back as one primitive,
    // which is what stops a wide aliased line restarting its stair-step phase mid-stroke. Per
    // triangle stays selectable as the reference the merge is measured against.
    private static final HatchJoiningChoice DEFAULT_HATCH_JOINING =
        HatchJoiningChoice.COALESCED;

    // A tenth of a percent of the spacing by default, which sits between two measured bounds
    // rather than between two guessed ones. On a real sector the crossings the merge has to close
    // disagree by around 1e-14 of the spacing - double rounding, not geometry - while the narrowest
    // gap it must leave open, where a territory genuinely stops, is around 0.4 of it. Anywhere in
    // that range works; this is far enough above the rounding to survive a coarser tessellation and
    // still some four hundred times below the nearest real break.
    //
    // Stored 0..100 in the CSV as a percentage, exposed as the fraction the hatch merge works in:
    // the slider rounds a Double to two decimals, so a fraction authored directly would collapse to
    // zero the moment it was dragged.
    private static final double DEFAULT_HATCH_JOIN_TOLERANCE_PERCENT = 0.1;
    private static final double HATCH_JOIN_TOLERANCE_PERCENT_PER_UNIT = 100.0;

    // Hover-highlight knobs, mirroring the CSV defaults: the hovered ground's own bright
    // shade, a four-layer halo peaking at half alpha and fading out by 14 pixels with a slow
    // quarter-depth breath, and a cell wash of a little over a third alpha under a crisp
    // near-opaque trace. Tuned to sit over the fills without swamping them - the fills
    // themselves paint at 0.4 - and expected to move once playtested.
    private static final FactionPaletteChoice DEFAULT_HOVER_HIGHLIGHT_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_HOVER_GLOW_OPACITY = 0.5;
    private static final double DEFAULT_HOVER_GLOW_WIDTH = 14.0;
    private static final int DEFAULT_HOVER_GLOW_LAYERS = 4;
    private static final double DEFAULT_HOVER_GLOW_PULSE_STRENGTH = 0.25;
    private static final double DEFAULT_HOVER_GLOW_PULSE_PERIOD_SECONDS = 1.5;
    private static final double DEFAULT_HOVER_WASH_OPACITY = 0.35;
    private static final double DEFAULT_HOVER_WASH_OUTLINE_OPACITY = 0.8;
    private static final double DEFAULT_HOVER_WASH_OUTLINE_WIDTH = 2.0;

    // Every hover tier is on by default, at all three levels. A tiered gate that shipped with any
    // level off would read to a player as a feature that is broken rather than switched off, and
    // defaulting the new levels on is also what keeps the tiering invisible to an existing player:
    // the two levels they may already have switched off still switch the same feedback off.
    private static final boolean DEFAULT_HOVERING_ENABLED = true;
    private static final boolean DEFAULT_HOVER_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_HOVER_TOOLTIP_ENABLED = true;
    private static final boolean DEFAULT_POLITICAL_HOVER_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_POLITICAL_HOVER_TOOLTIP_ENABLED = true;

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
     * @return which faction palette colour the outer (national) border draws in,
     *         or NONE to hide it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionOuterBorderColour() {
        return readChoice(FACTION_OUTER_BORDER_COLOR_FIELD, DEFAULT_FACTION_OUTER_BORDER_COLOUR);
    }

    /**
     * @return the outer (national) border opacity for faction systems, 0..1
     */
    public static double getFactionOuterBorderOpacity() {
        return readDouble(FACTION_OUTER_BORDER_OPACITY_FIELD, DEFAULT_FACTION_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for faction systems, pixels
     */
    public static double getFactionOuterBorderWidth() {
        return readDouble(FACTION_OUTER_BORDER_WIDTH_FIELD, DEFAULT_FACTION_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the inner (province seam) borders draw
     *         in, or NONE to hide them; the secondary (dark) colour by default
     */
    public static FactionPaletteChoice getFactionInnerBorderColour() {
        return readChoice(FACTION_INNER_BORDER_COLOR_FIELD, DEFAULT_FACTION_INNER_BORDER_COLOUR);
    }

    /**
     * @return the inner (province seam) border opacity for faction systems, 0..1
     */
    public static double getFactionInnerBorderOpacity() {
        return readDouble(FACTION_INNER_BORDER_OPACITY_FIELD, DEFAULT_FACTION_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for faction systems,
     *         pixels
     */
    public static double getFactionInnerBorderWidth() {
        return readDouble(FACTION_INNER_BORDER_WIDTH_FIELD, DEFAULT_FACTION_INNER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the territory fill draws in, or NONE to
     *         leave it unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionFillColour() {
        return readChoice(FACTION_FILL_COLOR_FIELD, DEFAULT_FACTION_FILL_COLOUR);
    }

    /**
     * @return the fill opacity for faction systems, 0..1
     */
    public static double getFactionFillOpacity() {
        return readDouble(FACTION_FILL_OPACITY_FIELD, DEFAULT_FACTION_FILL_OPACITY);
    }

    /**
     * @return the opacity a faction cluster's name draws at, 0..1, a fraction of its
     *         owner's colour: 1 draws faction names at full strength, lower fades only the
     *         faction group's names, leaving independent space's names untouched. The
     *         per-frame map-zoom fade the renderer applies still composes on top of this
     */
    public static double getFactionNameOpacity() {
        return readDouble(FACTION_NAME_OPACITY_FIELD, DEFAULT_FACTION_NAME_OPACITY);
    }

    /**
     * @return which independent palette colour the outer (national) border draws
     *         in, or NONE to hide it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentOuterBorderColour() {
        return readChoice(
            INDEPENDENT_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR);
    }

    /**
     * @return the outer (national) border opacity for independent-held systems,
     *         0..1
     */
    public static double getIndependentOuterBorderOpacity() {
        return readDouble(
            INDEPENDENT_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for independent-held systems,
     *         pixels
     */
    public static double getIndependentOuterBorderWidth() {
        return readDouble(
            INDEPENDENT_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the inner (province seam) borders
     *         draw in, or NONE to hide them; the secondary (dark) colour by default
     */
    public static FactionPaletteChoice getIndependentInnerBorderColour() {
        return readChoice(
            INDEPENDENT_INNER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR);
    }

    /**
     * @return the inner (province seam) border opacity for independent-held
     *         systems, 0..1
     */
    public static double getIndependentInnerBorderOpacity() {
        return readDouble(
            INDEPENDENT_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for independent-held
     *         systems, pixels
     */
    public static double getIndependentInnerBorderWidth() {
        return readDouble(
            INDEPENDENT_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the territory fill draws in, or NONE
     *         to leave it unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentFillColour() {
        return readChoice(INDEPENDENT_FILL_COLOR_FIELD, DEFAULT_INDEPENDENT_FILL_COLOUR);
    }

    /**
     * @return the fill opacity for independent-held systems, 0..1
     */
    public static double getIndependentFillOpacity() {
        return readDouble(INDEPENDENT_FILL_OPACITY_FIELD, DEFAULT_INDEPENDENT_FILL_OPACITY);
    }

    /**
     * @return the opacity an independent-held cluster's name draws at, 0..1, a fraction of
     *         its owner's colour: 1 draws independent names at full strength, lower fades
     *         only the independent group's names, leaving faction names untouched. The
     *         per-frame map-zoom fade the renderer applies still composes on top of this
     */
    public static double getIndependentNameOpacity() {
        return readDouble(INDEPENDENT_NAME_OPACITY_FIELD, DEFAULT_INDEPENDENT_NAME_OPACITY);
    }

    /**
     * @return the outline opacity for decivilised systems, 0..1; 0 hides the outline,
     *         since the neutral colour is the only shade factionless ground has
     */
    public static double getDecivilisedBorderOpacity() {
        return readDouble(DECIVILISED_BORDER_OPACITY_FIELD, DEFAULT_DECIVILISED_BORDER_OPACITY);
    }

    /**
     * @return the outline line width for decivilised systems, pixels
     */
    public static double getDecivilisedBorderWidth() {
        return readDouble(DECIVILISED_BORDER_WIDTH_FIELD, DEFAULT_DECIVILISED_BORDER_WIDTH);
    }

    /**
     * @return the neutral-colour fill opacity for decivilised systems, 0..1; 0 leaves
     *         them unfilled so only the outline draws
     */
    public static double getDecivilisedFillOpacity() {
        return readDouble(DECIVILISED_FILL_OPACITY_FIELD, DEFAULT_DECIVILISED_FILL_OPACITY);
    }

    /**
     * @return the outline opacity for uninhabited systems, 0..1
     */
    public static double getUninhabitedBorderOpacity() {
        return readDouble(UNINHABITED_BORDER_OPACITY_FIELD, DEFAULT_UNINHABITED_BORDER_OPACITY);
    }

    /**
     * @return the outline line width for uninhabited systems, pixels
     */
    public static double getUninhabitedBorderWidth() {
        return readDouble(UNINHABITED_BORDER_WIDTH_FIELD, DEFAULT_UNINHABITED_BORDER_WIDTH);
    }

    /**
     * @return the fraction of its normal opacity a non-allied bloc's borders, fills, and name
     *         draw at while Mute non-allied factions is on in the alliances view, 0..1: 0.5
     *         halves them, 0 hides them, 1 leaves them unchanged; 0.5 by default. Unread while
     *         the sidebar Mute toggle is off. Supplementary to that sidebar-only toggle
     */
    public static double getPoliticalMapAllianceMutedOpacityModifier() {
        return readDouble(
            ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD,
            DEFAULT_ALLIANCE_MUTED_OPACITY_MODIFIER);
    }

    /**
     * @return how far a desaturated bloc's uniform Independent-based grey is sunk toward black
     *         while the sidebar Desaturate toggle is on, as the fraction of brightness removed:
     *         0 leaves the Independent shades untouched, 0.3 draws them 30% darker (the default),
     *         1 goes to black. The render pipeline resolves the palette once per pass; the view
     *         flags only whether a bloc desaturates, never how dark
     */
    public static double getPoliticalMapDesaturationDarkening() {
        return readDouble(DESATURATION_DARKENING_FIELD, DEFAULT_DESATURATION_DARKENING);
    }

    /**
     * @return the multiplier on each colony's base size rating - a visible market's
     *         own size, or a hidden market's chosen base size (its real size or the
     *         fixed weight) - before the station and patrol bonuses and stability fold
     *         in; 1.0 by default (raw size), below 1 flattens the gap between large and
     *         small colonies and above 1 sharpens it
     */
    public static double getColonySizeWeight() {
        return readDouble(COLONY_SIZE_WEIGHT_FIELD, DEFAULT_COLONY_SIZE_WEIGHT);
    }

    /**
     * @return how a hidden market's base size rating is chosen: NORMAL by its real
     *         colony size like any colony, or FIXED at the hidden-market fixed weight
     *         regardless of size; FIXED by default so a large secret base does not
     *         outweigh the open colonies around it
     */
    public static HiddenMarketScalingChoice getHiddenMarketScaling() {
        return readChoice(HIDDEN_MARKET_SCALING_FIELD, DEFAULT_HIDDEN_MARKET_SCALING);
    }

    /**
     * @return the fixed size rating a hidden market folds in at while its scaling is
     *         FIXED, in place of its real size, before the colony size weight and
     *         stability apply; 1.0 by default. Unread while the scaling is NORMAL
     */
    public static double getHiddenMarketFixedWeight() {
        return readDouble(HIDDEN_MARKET_FIXED_WEIGHT_FIELD, DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT);
    }

    /**
     * @return the master switch for stability scaling: when on, each dominance factor
     *         is cut at low stability by its own low-stability penalty; on by default,
     *         off ranks colonies by their raw weighted size, station, and patrol sum
     */
    public static boolean shouldWeighDominanceByStability() {
        return readBoolean(STABILITY_WEIGHS_DOMINANCE_FIELD, DEFAULT_STABILITY_WEIGHS_DOMINANCE);
    }

    /**
     * @return how much a colony's own size weight is cut at zero stability, 0..1: 1
     *         removes it entirely (half at 5, full at 10), 0.5 leaves half, 0 ignores
     *         stability for colony size; 1.0 by default. Applied only while stability
     *         weighting is on
     */
    public static double getNormalLowStabilityPenalty() {
        return readDouble(NORMAL_LOW_STABILITY_PENALTY_FIELD, DEFAULT_NORMAL_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a market with an attached defensive station gains the station
     *         weight in size points toward its system's dominance; on by default, off
     *         ranks markets without any station bonus
     */
    public static boolean shouldWeighDominanceByStation() {
        return readBoolean(STATION_WEIGHS_DOMINANCE_FIELD, DEFAULT_STATION_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points an attached defensive station adds to a visible colony's
     *         dominance contribution, before its low-stability penalty applies; 1.0 by
     *         default, below 1 softens the station bonus and above 1 sharpens it. A
     *         hidden market earns this scaled by the hidden-market station fraction
     */
    public static double getStationWeight() {
        return readDouble(STATION_WEIGHT_FIELD, DEFAULT_STATION_WEIGHT);
    }

    /**
     * @return the fraction of the station weight a station on a hidden market earns,
     *         0..1 - so a fortified secret base reads above a bare unstationed hidden
     *         market without matching an openly held stationed colony; half by default
     */
    public static double getStationHiddenMarketRate() {
        return readDouble(STATION_HIDDEN_MARKET_RATE_FIELD, DEFAULT_STATION_HIDDEN_MARKET_RATE);
    }

    /**
     * @return how much the station bonus is cut at zero stability, 0..1: 1 removes it
     *         entirely, 0.5 leaves half, 0 keeps the full bonus regardless of
     *         stability; 0.5 by default. Applied only while stability weighting is on
     */
    public static double getStationLowStabilityPenalty() {
        return readDouble(
            STATION_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_STATION_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a colony gains size points toward its system's dominance for the
     *         patrols it fields, weighted by patrol size; off by default, so patrol
     *         strength does not sway dominance until the player opts in
     */
    public static boolean shouldWeighDominanceByPatrols() {
        return readBoolean(PATROL_WEIGHS_DOMINANCE_FIELD, DEFAULT_PATROL_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points each small (light) patrol a colony fields adds to its
     *         dominance contribution, before the patrol low-stability penalty applies;
     *         0.25 by default. Unread while patrol weighting is off
     */
    public static double getPatrolSmallWeight() {
        return readDouble(PATROL_SMALL_WEIGHT_FIELD, DEFAULT_PATROL_SMALL_WEIGHT);
    }

    /**
     * @return the size points each medium patrol a colony fields adds to its dominance
     *         contribution, before the patrol low-stability penalty applies; 0.5 by
     *         default. Unread while patrol weighting is off
     */
    public static double getPatrolMediumWeight() {
        return readDouble(PATROL_MEDIUM_WEIGHT_FIELD, DEFAULT_PATROL_MEDIUM_WEIGHT);
    }

    /**
     * @return the size points each large (heavy) patrol a colony fields adds to its
     *         dominance contribution, before the patrol low-stability penalty applies;
     *         1.0 by default. Unread while patrol weighting is off
     */
    public static double getPatrolLargeWeight() {
        return readDouble(PATROL_LARGE_WEIGHT_FIELD, DEFAULT_PATROL_LARGE_WEIGHT);
    }

    /**
     * @return how much a colony's patrol bonus is cut at zero stability, 0..1: 1
     *         removes it entirely, 0.5 leaves half, 0 keeps the full bonus regardless
     *         of stability; 0.5 by default. Applied only while stability weighting is on
     */
    public static double getPatrolLowStabilityPenalty() {
        return readDouble(
            PATROL_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_PATROL_LOW_STABILITY_PENALTY);
    }

    /**
     * @return the sides of the polygon that rounds each system cell's outer
     *         frontier - its reach into empty space; higher is smoother but adds a
     *         vertex per segment to every frontier cell (a map-FPS cost), lower
     *         trades a faceted frontier for fewer vertices
     */
    public static int getPoliticalMapCellBoundSegments() {
        return readInt(CELL_BOUND_SEGMENTS_FIELD, DEFAULT_CELL_BOUND_SEGMENTS);
    }

    /**
     * @return how far each system's territory reaches into empty space before its
     *         frontier bound closes it off, in world units; higher lets each system
     *         colour more open space (so distant systems' territories meet and merge),
     *         lower pulls every territory in tight around its own systems
     */
    public static double getPoliticalMapCellRadius() {
        return readDouble(CELL_RADIUS_FIELD, DEFAULT_CELL_RADIUS);
    }

    /**
     * @return the corner-rounding radius of the national border, in world
     *         units; higher rounds the cluster outline more
     */
    public static double getPoliticalMapBorderCornerRadius() {
        return readDouble(BORDER_CORNER_RADIUS_FIELD, DEFAULT_BORDER_CORNER_RADIUS);
    }

    /**
     * @return the arc segments per rounded corner of the national border; higher is
     *         smoother
     */
    public static int getPoliticalMapBorderCornerSegments() {
        return readInt(BORDER_CORNER_SEGMENTS_FIELD, DEFAULT_BORDER_CORNER_SEGMENTS);
    }

    /**
     * @return the interior angle below which a national-border corner is chamfered
     *         flat rather than rounded, in radians (the setting is authored in
     *         degrees and converted here, since the rounding math works in radians)
     */
    public static double getPoliticalMapBorderChamferAngleRadians() {
        return Math.toRadians(readDouble(BORDER_CHAMFER_ANGLE_FIELD,
            DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES));
    }

    /**
     * @return the perpendicular gap between the diagonal hatch lines filling the filter's
     *         contested cluster, in world units; lower packs the hatch denser, higher opens
     *         it up
     */
    public static double getPoliticalMapHatchSpacing() {
        return readDouble(HATCH_SPACING_FIELD, DEFAULT_HATCH_SPACING);
    }

    /**
     * @return the direction the contested-cluster hatch lines run, in radians (the setting
     *         is authored in degrees off horizontal and converted here, since the hatch math
     *         works in radians)
     */
    public static double getPoliticalMapHatchAngleRadians() {
        return Math.toRadians(readDouble(HATCH_ANGLE_FIELD, DEFAULT_HATCH_ANGLE_DEGREES));
    }

    /**
     * @return the line width the contested-cluster hatch strokes at, in pixels; higher makes the
     *         contested texture read heavier without touching the solid fill or the line spacing
     */
    public static double getPoliticalMapHatchWidth() {
        return readDouble(HATCH_WIDTH_FIELD, DEFAULT_HATCH_WIDTH);
    }

    /**
     * @return how one contested-cluster hatch line's several crossings of that ground are cut
     *         into drawn segments - one per triangle crossed, or merged so a stroke that never
     *         leaves the cluster is a single primitive
     */
    public static HatchJoiningChoice getPoliticalMapHatchJoining() {
        return readChoice(HATCH_JOINING_FIELD, DEFAULT_HATCH_JOINING);
    }

    /**
     * @return how far apart two of one hatch line's crossings may sit and still merge into one
     *         segment, as a fraction of the hatch spacing (the setting is authored as a
     *         percentage of it and converted here). Read only by the merging joining
     */
    public static double getPoliticalMapHatchJoinToleranceFraction() {
        return readDouble(HATCH_JOIN_TOLERANCE_FIELD, DEFAULT_HATCH_JOIN_TOLERANCE_PERCENT)
            / HATCH_JOIN_TOLERANCE_PERCENT_PER_UNIT;
    }

    /**
     * @return whether the map answers the cursor at all - the master over both kinds of hover
     *         feedback on every layer, so with it off no cursor read runs and nothing hover-driven
     *         is drawn; on by default
     */
    public static boolean getMapHoveringEnabled() {
        return readBoolean(HOVERING_ENABLED_FIELD, DEFAULT_HOVERING_ENABLED);
    }

    /**
     * @return whether hover effects - the halo over the hovered ground and the wash on its cell -
     *         are on across every map layer; on by default. Under the hovering master, and over
     *         each layer's own effects switch
     */
    public static boolean getMapHoverEffectsEnabled() {
        return readBoolean(HOVER_EFFECTS_ENABLED_FIELD, DEFAULT_HOVER_EFFECTS_ENABLED);
    }

    /**
     * @return whether the hover tooltip - the box naming what the cursor is over - is on across
     *         every map layer; on by default. Under the hovering master, and over each layer's own
     *         tooltip switch. Read live each frame, so toggling it needs no rebuild
     */
    public static boolean getMapHoverTooltipEnabled() {
        return readBoolean(HOVER_TOOLTIP_ENABLED_FIELD, DEFAULT_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return whether the political map paints its own hover halo and cell wash; on by default.
     *         The bottom tier, so it can only withhold effects the two global tiers already allow
     */
    public static boolean getPoliticalMapHoverEffectsEnabled() {
        return readBoolean(
            POLITICAL_HOVER_EFFECTS_ENABLED_FIELD,
            DEFAULT_POLITICAL_HOVER_EFFECTS_ENABLED);
    }

    /**
     * @return whether the political map shows its own hover box - the hovered system's standings;
     *         on by default. The bottom tier, so it can only withhold the box the two global tiers
     *         already allow, and it leaves another layer's box alone
     */
    public static boolean getPoliticalMapHoverTooltipEnabled() {
        return readBoolean(
            POLITICAL_HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_POLITICAL_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return which palette colour of the ground under the cursor the hover halo and cell wash
     *         both draw in; the primary (bright) colour by default. Turning the highlight off is
     *         the enable toggle's job, not a colour choice
     */
    public static FactionPaletteChoice getPoliticalMapHoverHighlightColour() {
        return readChoice(HOVER_HIGHLIGHT_COLOR_FIELD, DEFAULT_HOVER_HIGHLIGHT_COLOUR);
    }

    /**
     * @return the alpha of the hover halo's innermost stroke, 0..1 - its brightest layer,
     *         which the outer layers fade away from
     */
    public static double getPoliticalMapHoverGlowOpacity() {
        return readDouble(HOVER_GLOW_OPACITY_FIELD, DEFAULT_HOVER_GLOW_OPACITY);
    }

    /**
     * @return how far the hover halo reaches off the hovered frontier, in pixels - the width
     *         of its widest, faintest stroke
     */
    public static double getPoliticalMapHoverGlowWidth() {
        return readDouble(HOVER_GLOW_WIDTH_FIELD, DEFAULT_HOVER_GLOW_WIDTH);
    }

    /**
     * @return how many strokes the hover halo accumulates from; more buys a smoother falloff
     *         at a stroke of the whole frontier apiece
     */
    public static int getPoliticalMapHoverGlowLayers() {
        return readInt(HOVER_GLOW_LAYERS_FIELD, DEFAULT_HOVER_GLOW_LAYERS);
    }

    /**
     * @return how much of its alpha the hover halo gives up at the bottom of a breath, 0..1;
     *         0 holds it steady
     */
    public static double getPoliticalMapHoverGlowPulseStrength() {
        return readDouble(HOVER_GLOW_PULSE_STRENGTH_FIELD, DEFAULT_HOVER_GLOW_PULSE_STRENGTH);
    }

    /**
     * @return how long one breath of the hover halo's pulse takes, in seconds; ignored while
     *         the pulse strength is 0
     */
    public static double getPoliticalMapHoverGlowPulsePeriod() {
        return readDouble(HOVER_GLOW_PULSE_PERIOD_FIELD, DEFAULT_HOVER_GLOW_PULSE_PERIOD_SECONDS);
    }

    /**
     * @return the alpha the hovered cell's wash brightens its painted extent by, 0..1
     */
    public static double getPoliticalMapHoverWashOpacity() {
        return readDouble(HOVER_WASH_OPACITY_FIELD, DEFAULT_HOVER_WASH_OPACITY);
    }

    /**
     * @return the alpha the hovered cell's own outline traces at, 0..1 - the only cue a cell
     *         surrounded by its own faction has, so it reads apart from the wash
     */
    public static double getPoliticalMapHoverWashOutlineOpacity() {
        return readDouble(HOVER_WASH_OUTLINE_OPACITY_FIELD, DEFAULT_HOVER_WASH_OUTLINE_OPACITY);
    }

    /**
     * @return the line width the hovered cell's outline traces at, in pixels
     */
    public static double getPoliticalMapHoverWashOutlineWidth() {
        return readDouble(HOVER_WASH_OUTLINE_WIDTH_FIELD, DEFAULT_HOVER_WASH_OUTLINE_WIDTH);
    }

    /**
     * @return how far apart two outline points may be and still weld into one corner
     *         when chaining the national border, in world units; raised if borders
     *         go missing, lowered if distinct corners merge
     */
    public static double getMapBorderWeldTolerance() {
        return readDouble(BORDER_WELD_TOLERANCE_FIELD, DEFAULT_BORDER_WELD_TOLERANCE);
    }

    /**
     * @return the multiple of the border inset past which a sharp corner's miter is
     *         bevelled instead of pointed; lower bevels sooner (rounder corners,
     *         no inward spikes), higher keeps crisper points
     */
    public static double getMapBorderMiterLimit() {
        return readDouble(BORDER_MITER_LIMIT_FIELD, DEFAULT_BORDER_MITER_LIMIT);
    }

    /**
     * @return the depth (world units) up to which a sharp corner counts as a spike
     *         to sand off the border before rounding; a sharp corner that juts farther
     *         than this is real shape and is kept. Zero disables the spike pass
     */
    public static double getPoliticalMapBorderSpikeHeight() {
        return readDouble(BORDER_SPIKE_HEIGHT_FIELD, DEFAULT_BORDER_SPIKE_HEIGHT);
    }

    /**
     * @return the interior angle (radians) below which a shallow corner counts as a
     *         spike to sand off the border before rounding; a gentler corner is kept.
     *         Zero disables the spike pass
     */
    public static double getPoliticalMapBorderSpikeAngleRadians() {
        return Math.toRadians(readDouble(
            BORDER_SPIKE_ANGLE_FIELD,
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
        return readBoolean(ROUND_CORNERS_FIELD, DEFAULT_ROUND_CORNERS);
    }

    /**
     * @return whether the spike-sanding pass runs before corner rounding; on by
     *         default. When off the spike height and angle knobs go unread and needle
     *         or cusp protrusions are left in the border for the rounding to meet
     */
    public static boolean shouldSandBorderSpikes() {
        return readBoolean(SAND_SPIKES_FIELD, DEFAULT_SAND_SPIKES);
    }

    /**
     * @return how many directions the label-anchor search fans over the half-circle
     *         (0..180 degrees, since a label line is undirected); more directions
     *         let the accepted line align more closely with the cluster's open space
     *         at a higher search cost. Pure horizontal, the cluster's own principal
     *         axis, and the preferred slant are always searched on top of the fan
     */
    public static int getMapAnchorDirectionCount() {
        return readInt(ANCHOR_DIRECTION_COUNT_FIELD, DEFAULT_ANCHOR_DIRECTION_COUNT);
    }

    /**
     * @return how many parallel lines the search sweeps across the cluster per
     *         direction, spaced evenly over the cluster's extent perpendicular to
     *         that direction; this is what lets the accepted line slide off the
     *         centroid into a roomier part of the cluster. 1 degenerates to a single
     *         centred line per direction
     */
    public static int getMapAnchorOffsetCount() {
        return readInt(
            ANCHOR_OFFSET_COUNT_FIELD,
            DEFAULT_ANCHOR_OFFSET_COUNT);
    }

    /**
     * @return how much length a shallower (more horizontal) candidate line may give
     *         up and still win the search, 0..1: a candidate's clear length is scaled
     *         by {@code 1 - strength * sin(angle)^exponent}, so 0 picks the pure
     *         longest line regardless of slope and 1 scores a vertical line zero
     */
    public static double getMapAnchorVerticalPenaltyStrength() {
        return readDouble(
            ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH);
    }

    /**
     * @return how sharply the vertical penalty concentrates toward vertical: the
     *         exponent on {@code sin(angle)} in the score, at least 1. A higher
     *         exponent leaves already-shallow lines almost unpenalised and bites only
     *         as a line approaches vertical, the falloff a flat vertical-component
     *         scale got backwards
     */
    public static double getMapAnchorVerticalPenaltyExponent() {
        return readDouble(
            ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT);
    }

    /**
     * @return how far a label may lean off level to follow its cluster's long axis, in
     *         degrees. The anchor search prefers this cluster-specific slant over
     *         screen-horizontal, capped here so a tall cluster never stands its name
     *         vertical and faded toward level as a cluster gets rounder; 0 forces
     *         dead-horizontal labels, 90 lets a label follow its axis to vertical
     */
    public static double getMapAnchorMaxSlantDegrees() {
        return readDouble(ANCHOR_MAX_SLANT_DEGREES_FIELD, DEFAULT_ANCHOR_MAX_SLANT_DEGREES);
    }

    /**
     * @return how far short of the national border each end of a label anchor stops,
     *         in multiples of the border inset channel - the gap a cluster's name
     *         needs so it does not touch the border; a clear line shorter than twice
     *         this collapses to the dot
     */
    public static double getMapAnchorEndInsetMultiple() {
        return readDouble(ANCHOR_END_INSET_MULTIPLE_FIELD, DEFAULT_ANCHOR_END_INSET_MULTIPLE);
    }

    /**
     * @return the keep-out radius around each system icon that a label anchor must
     *         not cross, in world units - a tuned approximation of the icon's on-map
     *         footprint, since icons draw at a fixed pixel size while the anchor is
     *         fitted once in world space
     */
    public static double getMapAnchorIconClearance() {
        return readDouble(ANCHOR_ICON_CLEARANCE_FIELD, DEFAULT_ANCHOR_ICON_CLEARANCE);
    }

    /**
     * @return the smallest per-line font height (world units) a cluster's name may
     *         render at - the readability floor. A placement that cannot hold even one
     *         line this tall anywhere collapses to the dot and shows no name
     */
    public static double getMapNameMinFontSize() {
        return readDouble(NAME_MIN_FONT_SIZE_FIELD, DEFAULT_NAME_MIN_FONT_SIZE);
    }

    /**
     * @return the largest per-line font height (world units) the name fit will grow to,
     *         so a roomy cluster does not mint an oversized label; the upper bound of
     *         the font-height search
     */
    public static double getMapNameMaxFontSize() {
        return readDouble(NAME_MAX_FONT_SIZE_FIELD, DEFAULT_NAME_MAX_FONT_SIZE);
    }

    /**
     * @return the most lines the fit may wrap a name into: a length-poor but girth-rich
     *         cluster wraps the name to shorten its widest line and spends the spare
     *         girth, chosen only when that renders a strictly larger font than fewer
     *         lines would. 1 forces single-line names
     */
    public static int getMapNameMaxLines() {
        return readInt(NAME_MAX_LINES_FIELD, DEFAULT_NAME_MAX_LINES);
    }

    /**
     * @return the line-height multiple between a multi-line name's stacked lines, so a
     *         two- or three-line block is that much taller than the raw line heights;
     *         at least 1 (lines flush)
     */
    public static double getMapNameLineSpacing() {
        return readDouble(NAME_LINE_SPACING_FIELD, DEFAULT_NAME_LINE_SPACING);
    }

    /**
     * @return the fill opacity of the debug band quad, 0..1 - low enough that the
     *         national border reads through the band so an overflow is visible, since
     *         the whole point of drawing the band is to see it kiss or clear the border
     */
    public static double getMapAnchorBandOpacity() {
        return readDouble(ANCHOR_BAND_OPACITY_FIELD, DEFAULT_ANCHOR_BAND_OPACITY);
    }

    /**
     * @return the opacity of the debug band box's strokes - its outline, the line-count
     *         divider rules, the centreline, and the anchor dot - 0..1, kept separate
     *         from the fill alpha so the outline stays legible over a faint band wash
     */
    public static double getMapAnchorBandLineOpacity() {
        return readDouble(ANCHOR_BAND_LINE_OPACITY_FIELD, DEFAULT_ANCHOR_BAND_LINE_OPACITY);
    }

    /**
     * @return how far down from the top edge of the screen the overlay sidebar box sits,
     *         in pixels; 46 by default (clearing the sector map's own tab strip)
     */
    public static int getMapSidebarPaddingTop() {
        return readInt(SIDEBAR_PADDING_TOP_FIELD, DEFAULT_SIDEBAR_PADDING_TOP);
    }

    /**
     * @return how far in from the left edge of the screen the overlay sidebar box sits,
     *         in pixels; 12 by default
     */
    public static int getMapSidebarPaddingLeft() {
        return readInt(SIDEBAR_PADDING_LEFT_FIELD, DEFAULT_SIDEBAR_PADDING_LEFT);
    }

    /**
     * @return how far above the bottom edge of the screen the overlay sidebar box must stay,
     *         in pixels; the panel body caps its height to this margin and the bloc list
     *         scrolls within the room left; 12 by default
     */
    public static int getMapSidebarPaddingBottom() {
        return readInt(SIDEBAR_PADDING_BOTTOM_FIELD, DEFAULT_SIDEBAR_PADDING_BOTTOM);
    }

    /**
     * @return how far down from the top edge of the intel screen's map preview (the "visor") the
     *         overlay sidebar box sits, in pixels, clearing the vanilla starscape and fuel-range
     *         toggles at the top of that map; 40 by default. The box sits flush against the visor's
     *         left edge and its height caps to the visor's bottom, so only this top offset is exposed
     */
    public static int getMapIntelSidebarPaddingTop() {
        return readInt(INTEL_SIDEBAR_PADDING_TOP_FIELD, DEFAULT_INTEL_SIDEBAR_PADDING_TOP);
    }

    /**
     * @return the line width of the outer border framing the overlay sidebar box, in
     *         pixels; 1 by default, 0 draws no border
     */
    public static int getMapSidebarBorderWidth() {
        return readInt(SIDEBAR_BORDER_WIDTH_FIELD, DEFAULT_SIDEBAR_BORDER_WIDTH);
    }

    /**
     * @return the overlay sidebar box's background opacity as a 0..1 fraction (the CSV
     *         stores it as a 0..100 percentage); 0.8 by default
     */
    public static float getMapSidebarBackgroundOpacity() {

        var percent = readInt(
            SIDEBAR_OPACITY_FIELD,
            DEFAULT_SIDEBAR_OPACITY_PERCENT);

        var clamped = Math.max(
            MIN_SIDEBAR_OPACITY_PERCENT,
            Math.min(MAX_SIDEBAR_OPACITY_PERCENT, percent));

        return clamped / (float) MAX_SIDEBAR_OPACITY_PERCENT;
    }

    /**
     * @return how long the overlay sidebar's collapse handle takes to fold the body to its
     *         docked rail (and to unfold it), in seconds; 0 snaps it instantly with no
     *         animation, up to 2 seconds; 0.25 by default. Fed to the collapse holder's
     *         per-frame advance so the player sets the animation pace
     */
    public static float getMapSidebarCollapseSeconds() {
        return (float) readDouble(
            SIDEBAR_COLLAPSE_SECONDS_FIELD,
            DEFAULT_SIDEBAR_COLLAPSE_SECONDS);
    }

    /**
     * @return which colour the overlay sidebar's collapse-handle chevron draws in: the vanilla
     *         highlight gold, or the panel's own player-faction accents (brightening under the
     *         pointer); the gold by default
     */
    public static NotchChevronColourChoice getMapSidebarChevronColour() {
        return readChoice(
            SIDEBAR_CHEVRON_COLOR_FIELD,
            DEFAULT_SIDEBAR_CHEVRON_COLOUR);
    }

    /**
     * Resolves a layer tab's shortcut keycode from its LunaLib Keycode field, so the player
     * can rebind which key jumps to that layer.
     *
     * <p>A keycode of 0 (LWJGL's {@code KEY_NONE}) means the player cleared the binding
     * with Escape, so the layer has no shortcut; callers treat that as unbound rather
     * than a real key.
     *
     * @param settingKey     the LunaLib field id holding the rebound keycode
     * @param defaultKeycode the LWJGL keycode used when the field is unset or unreadable
     * @return the LWJGL keycode the layer's tab jumps to, or 0 when the shortcut is unbound
     */
    public static int getMapLayerShortcut(String settingKey, int defaultKeycode) {
        return readInt(settingKey, defaultKeycode);
    }

    /**
     * @return whether the political map draws every faction's colonies, including ones
     *         the player has not discovered yet - bypasses the known-to-player gate so
     *         an undiscovered colony still folds into its system's dominance,
     *         inhabitation, and cell geometry; off by default, a reveal aid for
     *         inspecting the whole sector's politics
     */
    public static boolean getPoliticalMapShowAllFactions() {
        return readBoolean(SHOW_ALL_FACTIONS_FIELD, DEFAULT_SHOW_ALL_FACTIONS);
    }

    /**
     * @return whether the political map seeds a cell for every star system, not just the
     *         reachable, visible, or inhabited ones - bypasses the visibility rule so a
     *         system the map would otherwise omit still gets geometry; off by default, a
     *         reveal aid for inspecting the full cell partition
     */
    public static boolean shouldForceAllSystemsOnMap() {
        return readBoolean(FORCE_ALL_SYSTEMS_ON_MAP_FIELD, DEFAULT_FORCE_ALL_SYSTEMS_ON_MAP);
    }

    /**
     * @return whether the political map draws the per-cluster label anchors - a dot at
     *         each contiguous cluster's centre and, in green, the accepted label line
     *         its fit produced; off by default, a diagnostic for the map labels
     *         themselves. Master switch for the anchor overlay: the rejected- and
     *         unbiased-axis toggles below only add lines while this is on
     */
    public static boolean getPoliticalMapShowClusterAnchors() {
        return readBoolean(SHOW_CLUSTER_ANCHORS_FIELD, DEFAULT_SHOW_CLUSTER_ANCHORS);
    }

    /**
     * @return whether a cluster whose accepted label line collapsed to the dot also
     *         draws, in red, the best candidate line its fit found before the border,
     *         icon, or end-margin trim discarded it - how close the cluster came to
     *         carrying a line; off by default, meaningful only while the cluster
     *         anchors themselves draw
     */
    public static boolean getMapShowRejectedAxes() {
        return readBoolean(SHOW_REJECTED_AXES_FIELD, DEFAULT_SHOW_REJECTED_AXES);
    }

    /**
     * @return whether each cluster also draws, in yellow, the label line its fit would
     *         accept with no horizontal bias applied - the anchor bias knob's effect
     *         made visible by contrast. Drawn only when the unbiased direction actually
     *         differs from the accepted line's (with the bias at 1, or an axis already
     *         horizontal, the accepted line is the unbiased line); off by default,
     *         meaningful only while the cluster anchors themselves draw
     */
    public static boolean getMapShowUnbiasedAxes() {
        return readBoolean(SHOW_UNBIASED_AXES_FIELD, DEFAULT_SHOW_UNBIASED_AXES);
    }

    /**
     * @return whether to replace the normal political-map render with the border-tracing
     *         diagnostic that layers the smoothing pipeline's stages (base, despiked,
     *         rounded) in distinct colours; off by default. Respects the two smoothing
     *         gates, so a stage draws only when its pass ran
     */
    public static boolean shouldTraceBordersForDebug() {
        return readBoolean(DEBUG_BORDER_TRACING_FIELD, DEFAULT_DEBUG_BORDER_TRACING);
    }

    /**
     * @return whether the market-condition picker offers every condition, or only
     *         the planetary ones vanilla treats as hand-placeable; on by default,
     *         so non-planetary conditions (such as decivilisation) are offered too
     */
    public static boolean shouldOfferAllConditions() {
        return readBoolean(OFFER_ALL_CONDITIONS_FIELD, DEFAULT_OFFER_ALL_CONDITIONS);
    }

    // Reads any Radio field and maps its stored label back to a choice, falling back on that
    // field's default when unset, unreadable, or left over from an option that no longer exists.
    // A Radio stores the selected option's label whatever the enum behind it, so one read serves
    // every choice-backed setting; the constants to match against come off the fallback itself, so
    // a caller names the field and its default and nothing else.
    private static <T extends Enum<T> & LabeledChoice> T readChoice(String fieldId, T fallback) {
        return LabeledChoices.fromLabel(
            fallback.getDeclaringClass().getEnumConstants(),
            readString(fieldId, fallback.getLabel()),
            fallback);
    }

    // The typed reads, each binding this mod's LunaLib settings id once. Every getter above names
    // only its own field and default, so the mod id appears here rather than at each of them, and
    // the LunaLib coupling narrows to these four lines.
    private static boolean readBoolean(String fieldId, boolean fallback) {
        return LunaSettingsReader.getBoolean(MOD_ID, fieldId, fallback);
    }

    private static double readDouble(String fieldId, double fallback) {
        return LunaSettingsReader.getDouble(MOD_ID, fieldId, fallback);
    }

    private static int readInt(String fieldId, int fallback) {
        return LunaSettingsReader.getInt(MOD_ID, fieldId, fallback);
    }

    private static String readString(String fieldId, String fallback) {
        return LunaSettingsReader.getString(MOD_ID, fieldId, fallback);
    }
}
