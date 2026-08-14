package kmu.settings;

/**
 * The political map layer's own LunaLib knobs: how this one layer paints the sector and
 * how it decides who holds what.
 *
 * <p>One of the knob classes {@link KmuLunaSettings} splits by reader. Everything here is
 * read by {@code kmu.maplayers.politicalmap} and nothing else, which is what lets the
 * framework's own settings class stay clear of a feature's vocabulary.
 *
 * <p>Several knobs here shape geometry the framework owns - the cell partition, the
 * border smoothing passes, the hatch fill. They are the political map's all the same,
 * because the framework code takes those values as parameters and never reads a setting:
 * this layer resolves them and hands them down. A knob is filed by who reads it, not by
 * whose geometry it moves.
 *
 * <p>The value fields, matching data/config/LunaSettings.csv, style each category of system
 * on the sector map. Owned categories - core factions and independent space - each get a
 * fill, an outer (national) border, and an inner (province seam) border, every one with a
 * palette-colour choice, an opacity, and (for the borders) a line width. Factionless
 * categories - decivilised and uninhabited systems - have no faction palette to choose
 * from, so they carry no colour field at all and always paint in the shared neutral colour:
 * decivilised systems get an outline opacity and width plus a fill opacity, and uninhabited
 * systems just the outline pair. Each opacity doubles as that element's on/off, since zero
 * opacity is the only way to hide a shade with no alternative. Whether the uninhabited
 * outline draws at all is the on-map sidebar's checkbox rather than a field here, so the
 * setting screen never duplicates that control. All are tuned under the LunaLib
 * "Map - Politics - Visuals" tab, which also carries the presence bands and this layer's own
 * hover pair with the halo and wash those switch.
 *
 * <p>The presence-band fields there are the one group whose knobs are world sizes rather than
 * opacities and pixel widths: a band is baked into a cell's own geometry at rebuild, so its
 * thickness, its clearance from the border, and the two run lengths those are multiples of are
 * all stated in the units the cells are cut in, with nothing about a band left for a frame to
 * measure. Its on/off is a real switch rather than a zeroed opacity, since a band paints in the
 * palette colours of whichever blocs are present and has no shade of its own to take away.
 *
 * <p>The "Map - Politics - Domination" tab holds the dominance rules - fields that change the
 * map's political verdicts rather than its styling, which is why they do not sit under
 * "Map - Politics - Visuals". They set how heavily a colony's raw size weighs on its
 * system's dominant faction, how a hidden market's size is counted (its real size or a
 * fixed weight), whether (and by how many size points) an attached defensive station lifts
 * a colony (paired with the fraction a hidden market earns), and whether the patrols a
 * colony fields lift it too, weighted by patrol size. Stability weighting is a master
 * toggle over three per-factor low-stability penalties (colony size, station, patrols) that
 * each set how deeply their factor collapses at zero stability. The tab groups the fields
 * under Colony size, Hidden markets, Stability, Orbital stations, and Patrols headers.
 *
 * <p>The rest sit under the two dev tabs, which are tuning surfaces rather than player-facing
 * appearance. Most land on "Map - Dev", because a tab answers to whose map a knob shapes and
 * what these shape is a map's geometry rather than a political verdict - their getters stay
 * here for the reason above, which is why a framework tab and this class are no contradiction.
 * Only the reveal overrides take "Map - Politics - Dev", since what they widen is who the map
 * draws colonies for.
 *
 * <p>The cell reach and frontier resolution seed the partition itself, so they feed
 * the geometry rebuild rather than the drawables restyle. The border shaping is split into
 * the operations that produce a border, in pipeline order after the framework's tracing -
 * spike sanding, then corner rounding - each its own section led by a boolean master switch
 * that turns the pass off whole without zeroing the knobs beneath it. The corner-rounding
 * gate also covers the factionless (decivilised and uninhabited) cell outlines, which reuse
 * the same rounding. The hatch fill and its joining tolerance shape the contested cluster's
 * texture. Two reveal overrides widen what the map draws for inspection - show-all-factions,
 * which draws undiscovered colonies too, and force-all-systems, which seeds a cell for every
 * star system - and the two diagnostics draw the layer's own workings: the cluster label
 * anchors, and the border-tracing overlay that layers the smoothing stages in distinct
 * colours.
 */
public final class KmuPoliticalMapSettings {

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
    // a factionless cell has no faction palette to pick from, so it always paints in the
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
    // filter's faded backdrop and the alliances view's non-allied blocs). The muted-opacity
    // modifier is how far Mute dims a receded bloc's borders, fills, and name as a fraction of
    // normal opacity; the desaturation darkening is how far a desaturated bloc's uniform
    // Independent-based grey is sunk toward black, so the receded fills read behind genuine
    // independent-held space. The toggles themselves are sidebar-only per-save choices (sector
    // memory), not LunaLib fields, since every LunaLib field would render on a settings tab.
    private static final String ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD =
        "kmu_politicalMapAllianceMutedOpacityModifier";
    private static final String DESATURATION_DARKENING_FIELD =
        "kmu_politicalMapDesaturationDarkening";
    private static final String PRESENCE_LIGHTENING_FIELD =
        "kmu_politicalMapPresenceLightening";

    // Presence band fields (Map - Politics - Visuals tab): the banded stroke a cell draws inside
    // its own border to say which factions hold colonies in that system and how many. The switch
    // is a real on/off rather than an opacity, since a band has no shade of its own to zero - it
    // paints in the palette colours of whoever is present. The four sizes are the whole of the
    // design's proportions: the width every other size is stated against, the gap that keeps the
    // band clear of the border, and the two run lengths whose ratio is what makes a faction's
    // stretch read as several colonies rather than one.
    private static final String RIBBON_ENABLED_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_enabled";
    private static final String RIBBON_WIDTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_width";
    private static final String RIBBON_INSET_PAD_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_insetPad";
    private static final String RIBBON_SEGMENT_LENGTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_segmentLength";
    private static final String RIBBON_INTERJECTION_LENGTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_interjectionLength";

    // Hover tiers, this layer's own pair (Map - Politics - Visuals tab): the same two kinds
    // of feedback the framework switches globally, scoped to this one layer's paint. Each ANDs
    // with the global switch of its kind, so a layer switch only takes away feedback the tiers
    // above already allow - which is what lets one layer keep its box while another's is off.
    private static final String LAYER_HOVER_EFFECTS_ENABLED_FIELD =
        "kmu_mapPoliticsVisualsHoverEffectsEnabled";
    private static final String LAYER_HOVER_TOOLTIP_ENABLED_FIELD =
        "kmu_mapPoliticsVisualsHoverTooltipEnabled";

    // Hover highlight styling (Map - Politics - Visuals tab): what the halo around the hovered
    // territory's frontier and the wash over the one hovered cell look like, both drawn in the
    // hovered cell's own palette colour. Whether they draw at all is the hover tiers above; these
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

    // Cell geometry (Map - Dev tab): the resolution of the raw Voronoi cells, upstream of
    // any border shaping. Unlike the border fields below - which restyle fixed
    // geometry through the drawables rebuild - this reseeds the cells themselves, so
    // it feeds the geometry rebuild. Every segment is a vertex on each frontier cell,
    // so it is the lever for trading map FPS against frontier smoothness.
    private static final String CELL_BOUND_SEGMENTS_FIELD =
        "kmu_politicalMapCellBoundSegments";

    // Cell reach (Map - Dev tab, Cell geometry): how far each system's territory extends into empty
    // space before the frontier bound closes it off. Sits beside the resolution knob above
    // rather than with the palette because the two answer the same question - the shape of the
    // cells the map is partitioned into - and both reseed the cells, so it feeds the geometry
    // rebuild rather than the drawables restyle.
    private static final String CELL_RADIUS_FIELD =
        "kmu_politicalMapCellRadius";

    // National-border geometry (Map - Dev tab): the shape of the frontier stroked and filled
    // per cluster, exposed for live tuning rather than baked as constants. All feed the
    // drawables rebuild, so a change takes effect the moment it is applied. They follow the
    // framework's own tracing in pipeline order, and each gated operation leads with its
    // master switch so the switch reads as the gate for the knobs beneath it.

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

    // Hatch fill (Map - Dev tab): the diagonal line pattern that fills the filter's contested
    // cluster - the spotlighted bloc's present-but-dominated systems - so it reads as
    // "mine, but contested" against the solid cluster it holds outright. Only that one
    // territory hatches; spacing is the perpendicular gap between lines in world units,
    // angle their direction in degrees off horizontal, and width the pixel stroke of each
    // line. All four feed the drawables rebuild, so a change repaints the hatch live.
    //
    // Smoothing is the odd one out in kind rather than in placement: the other three lay the
    // pattern out, while it picks how a laid-out line is rasterised. It sits here because a
    // player tuning how the hatch reads reaches for it beside the width it trades against -
    // a smoothed line reads wider and lighter than the same width stroked hard-edged.
    private static final String HATCH_SPACING_FIELD =
        "kmu_politicalMapHatchSpacing";
    private static final String HATCH_ANGLE_FIELD =
        "kmu_politicalMapHatchAngle";
    private static final String HATCH_WIDTH_FIELD =
        "kmu_politicalMapHatchWidth";
    private static final String HATCH_SMOOTHING_FIELD =
        "kmu_politicalMapHatchSmoothing";

    // Hatch fill - joining (Map - Dev tab): how near two of one hatch line's crossings must be to
    // count as the same stroke. Its own section because it tunes the merge that packs the
    // clipped crossings into segments, not the pattern those lines are laid out in.
    private static final String HATCH_JOIN_TOLERANCE_FIELD =
        "kmu_politicalMapHatchJoinTolerance";

    // Reveal overrides (Map - Politics - Dev tab): two toggles that widen what the map draws for
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

    // Diagnostics (Map - Dev tab): draws the per-cluster label anchors (a centre dot and the
    // accepted label line in green) so the clustering and axis fit behind the map
    // labels can be eyeballed on the map. Off by default.
    private static final String SHOW_CLUSTER_ANCHORS_FIELD =
        "kmu_politicalMapShowClusterAnchors";

    // Diagnostics (Map - Dev tab): replaces the normal render with a border-tracing overlay that
    // layers the smoothing pipeline's stages (base, despiked, rounded) in distinct colours,
    // honouring the two smoothing gates. Off by default.
    private static final String DEBUG_BORDER_TRACING_FIELD =
        "kmu_politicalMapDebugBorderTracing";

    // Fallbacks used only when a setting is read before LunaLib has loaded it;
    // the live values come from LunaLib. These mirror the defaultValue column in
    // data/config/LunaSettings.csv and must be kept in step with it.
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

    // A faint wash by default: a dead colony was really settled, so it fills rather than reading
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

    // All the way to white by default: the neutral and the Independent grey the background sinks
    // from are the same shade, so a partial lift leaves a spared cell reading against the value the
    // receded fills started at. Taking it to the end of the range is what makes the cell
    // unmistakable at a glance, which is the whole of what sparing it is for. Mirrors the CSV row's
    // defaultValue.
    private static final double DEFAULT_PRESENCE_LIGHTENING = 1.0;

    // On by default: the bands are a readout of what the fills leave out, and a sector where most
    // cells stay bare is what the gate already guarantees, so shipping them off would hide the
    // feature rather than spare the map.
    private static final boolean DEFAULT_RIBBON_ENABLED = true;

    // The band's thickness in world units, and the gap between the border and the band's near
    // edge. Settled by eye on the shipped sector rather than derived: a band as thick as its own
    // clearance reads as a stripe laid inside the border at the zooms the map is actually used at,
    // where a finer one disappeared into the border it follows.
    private static final double DEFAULT_RIBBON_WIDTH = 200.0;
    private static final double DEFAULT_RIBBON_INSET_PAD = 200.0;

    // The design's own proportions: a colony runs three widths, and two colonies of one bloc are
    // parted by one. Whole widths rather than fractions - a run is a count of holdings expressed as
    // a length, and how large a width is in the world is the width knob's business.
    private static final int DEFAULT_RIBBON_SEGMENT_LENGTH = 3;
    private static final int DEFAULT_RIBBON_INTERJECTION_LENGTH = 1;

    // Both of this layer's hover switches on by default, like the two tiers above them: a tiered
    // gate that shipped with any level off would read to a player as a feature that is broken
    // rather than switched off.
    private static final boolean DEFAULT_LAYER_HOVER_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_LAYER_HOVER_TOOLTIP_ENABLED = true;

    // Hover-highlight knobs, mirroring the CSV defaults: the hovered cell's own bright
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

    // Half collapse by default, like the station bonus: a colony's patrols keep half
    // their weight at zero stability rather than vanishing with its economy.
    private static final double DEFAULT_PATROL_LOW_STABILITY_PENALTY = 0.5;

    // Mirrors both the CSV defaultValue and VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
    // the geometric default this setting overrides; kept a literal like the other
    // fallbacks so this class stays decoupled from the geometry library.
    private static final int DEFAULT_CELL_BOUND_SEGMENTS = 48;

    // The default cell reach into empty space, world units - the constant the geometry
    // used before this became a knob. Mirrors the CSV defaultValue.
    private static final double DEFAULT_CELL_RADIUS = 4000.0;

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

    // Hard-edged by default, which is the state the hatch was tuned and verified at; what
    // smoothing costs a dense field of short strokes is GlLineQuality's own doc to state. A knob
    // rather than a fixed value because the trade turns on taste once the stroke is wide, and
    // because whether a GL bridge honours the smoothing hint at all varies.
    private static final boolean DEFAULT_HATCH_SMOOTHING = false;

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

    // Both reveal overrides off by default: the map draws exactly what the normal
    // gates admit until the player opts into a wider view. The two diagnostics likewise,
    // so the map draws its result rather than its workings until asked.
    private static final boolean DEFAULT_SHOW_ALL_FACTIONS = false;
    private static final boolean DEFAULT_FORCE_ALL_SYSTEMS_ON_MAP = false;
    private static final boolean DEFAULT_SHOW_CLUSTER_ANCHORS = false;
    private static final boolean DEFAULT_DEBUG_BORDER_TRACING = false;

    private KmuPoliticalMapSettings() {
    }

    /**
     * @return which faction palette colour the outer (national) border draws in,
     *         or NONE to hide it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionOuterBorderColour() {
        return KmuLunaSettings.readChoice(
            FACTION_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_COLOUR);
    }

    /**
     * @return the outer (national) border opacity for faction systems, 0..1
     */
    public static double getFactionOuterBorderOpacity() {
        return KmuLunaSettings.readDouble(
            FACTION_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for faction systems, pixels
     */
    public static double getFactionOuterBorderWidth() {
        return KmuLunaSettings.readDouble(
            FACTION_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the inner (province seam) borders draw
     *         in, or NONE to hide them; the secondary (dark) colour by default
     */
    public static FactionPaletteChoice getFactionInnerBorderColour() {
        return KmuLunaSettings.readChoice(
            FACTION_INNER_BORDER_COLOR_FIELD,
            DEFAULT_FACTION_INNER_BORDER_COLOUR);
    }

    /**
     * @return the inner (province seam) border opacity for faction systems, 0..1
     */
    public static double getFactionInnerBorderOpacity() {
        return KmuLunaSettings.readDouble(
            FACTION_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_FACTION_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for faction systems,
     *         pixels
     */
    public static double getFactionInnerBorderWidth() {
        return KmuLunaSettings.readDouble(
            FACTION_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_FACTION_INNER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the territory fill draws in, or NONE to
     *         leave it unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionFillColour() {
        return KmuLunaSettings.readChoice(FACTION_FILL_COLOR_FIELD, DEFAULT_FACTION_FILL_COLOUR);
    }

    /**
     * @return the fill opacity for faction systems, 0..1
     */
    public static double getFactionFillOpacity() {
        return KmuLunaSettings.readDouble(FACTION_FILL_OPACITY_FIELD, DEFAULT_FACTION_FILL_OPACITY);
    }

    /**
     * @return the opacity a faction cluster's name draws at, 0..1, a fraction of its
     *         owner's colour: 1 draws faction names at full strength, lower fades only the
     *         faction group's names, leaving independent space's names untouched. The
     *         per-frame map-zoom fade the renderer applies still composes on top of this
     */
    public static double getFactionNameOpacity() {
        return KmuLunaSettings.readDouble(FACTION_NAME_OPACITY_FIELD, DEFAULT_FACTION_NAME_OPACITY);
    }

    /**
     * @return which independent palette colour the outer (national) border draws
     *         in, or NONE to hide it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentOuterBorderColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR);
    }

    /**
     * @return the outer (national) border opacity for independent-held systems,
     *         0..1
     */
    public static double getIndependentOuterBorderOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for independent-held systems,
     *         pixels
     */
    public static double getIndependentOuterBorderWidth() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the inner (province seam) borders
     *         draw in, or NONE to hide them; the secondary (dark) colour by default
     */
    public static FactionPaletteChoice getIndependentInnerBorderColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_INNER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR);
    }

    /**
     * @return the inner (province seam) border opacity for independent-held
     *         systems, 0..1
     */
    public static double getIndependentInnerBorderOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for independent-held
     *         systems, pixels
     */
    public static double getIndependentInnerBorderWidth() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the territory fill draws in, or NONE
     *         to leave it unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentFillColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_FILL_COLOR_FIELD,
            DEFAULT_INDEPENDENT_FILL_COLOUR);
    }

    /**
     * @return the fill opacity for independent-held systems, 0..1
     */
    public static double getIndependentFillOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_FILL_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_FILL_OPACITY);
    }

    /**
     * @return the opacity an independent-held cluster's name draws at, 0..1, a fraction of
     *         its owner's colour: 1 draws independent names at full strength, lower fades
     *         only the independent group's names, leaving faction names untouched. The
     *         per-frame map-zoom fade the renderer applies still composes on top of this
     */
    public static double getIndependentNameOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_NAME_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_NAME_OPACITY);
    }

    /**
     * @return the outline opacity for decivilised systems, 0..1; 0 hides the outline,
     *         since the neutral colour is the only shade a factionless cell has
     */
    public static double getDecivilisedBorderOpacity() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_BORDER_OPACITY_FIELD,
            DEFAULT_DECIVILISED_BORDER_OPACITY);
    }

    /**
     * @return the outline line width for decivilised systems, pixels
     */
    public static double getDecivilisedBorderWidth() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_BORDER_WIDTH_FIELD,
            DEFAULT_DECIVILISED_BORDER_WIDTH);
    }

    /**
     * @return the neutral-colour fill opacity for decivilised systems, 0..1; 0 leaves
     *         them unfilled so only the outline draws
     */
    public static double getDecivilisedFillOpacity() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_FILL_OPACITY_FIELD,
            DEFAULT_DECIVILISED_FILL_OPACITY);
    }

    /**
     * @return the outline opacity for uninhabited systems, 0..1
     */
    public static double getUninhabitedBorderOpacity() {
        return KmuLunaSettings.readDouble(
            UNINHABITED_BORDER_OPACITY_FIELD,
            DEFAULT_UNINHABITED_BORDER_OPACITY);
    }

    /**
     * @return the outline line width for uninhabited systems, pixels
     */
    public static double getUninhabitedBorderWidth() {
        return KmuLunaSettings.readDouble(
            UNINHABITED_BORDER_WIDTH_FIELD,
            DEFAULT_UNINHABITED_BORDER_WIDTH);
    }

    /**
     * @return the fraction of its normal opacity a non-allied bloc's borders, fills, and name
     *         draw at while Mute non-allied factions is on in the alliances view, 0..1: 0.5
     *         halves them, 0 hides them, 1 leaves them unchanged; 0.5 by default. Unread while
     *         the sidebar Mute toggle is off. Supplementary to that sidebar-only toggle
     */
    public static double getPoliticalMapAllianceMutedOpacityModifier() {
        return KmuLunaSettings.readDouble(
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
        return KmuLunaSettings.readDouble(
            DESATURATION_DARKENING_FIELD,
            DEFAULT_DESATURATION_DARKENING);
    }

    /**
     * @return how far a factionless cell the spotlight spares is washed toward white, as the
     *         fraction of the way there: 0 leaves it at the plain neutral, 0.5 lifts it halfway,
     *         1 goes to white (the default). Only the value moves - the wash is applied to RGB
     *         alone - so a grey stays the same grey and the cell cannot drift into reading as a
     *         faction colour
     */
    public static double getPoliticalMapPresenceLightening() {
        return KmuLunaSettings.readDouble(
            PRESENCE_LIGHTENING_FIELD,
            DEFAULT_PRESENCE_LIGHTENING);
    }

    /**
     * @return whether a cell draws the banded stroke inside its own border that says which blocs
     *         hold colonies in that system and how many; on by default. Off skips the counting
     *         as well as the drawing, so a rebuild pays nothing for the bands at all
     */
    public static boolean shouldDrawPoliticalMapRibbons() {
        return KmuLunaSettings.readBoolean(RIBBON_ENABLED_FIELD, DEFAULT_RIBBON_ENABLED);
    }

    /**
     * @return how thick the presence band is drawn, in world units - the size every other size
     *         in the band is a multiple of, and a world quantity so a band holds the same share
     *         of its cell's outline at every zoom
     */
    public static double getPoliticalMapRibbonWidth() {
        return KmuLunaSettings.readDouble(RIBBON_WIDTH_FIELD, DEFAULT_RIBBON_WIDTH);
    }

    /**
     * @return how far clear of the cell's own border the presence band's near edge runs, in world
     *         units; a cell with no room for this gap and the band's width together draws no band
     */
    public static double getPoliticalMapRibbonInsetPad() {
        return KmuLunaSettings.readDouble(RIBBON_INSET_PAD_FIELD, DEFAULT_RIBBON_INSET_PAD);
    }

    /**
     * @return how far the run one colony draws reaches, in band widths
     */
    public static int getPoliticalMapRibbonSegmentLength() {
        return KmuLunaSettings.readInt(
            RIBBON_SEGMENT_LENGTH_FIELD,
            DEFAULT_RIBBON_SEGMENT_LENGTH);
    }

    /**
     * @return how far a parting in the band reaches, in band widths - both the one between two
     *         colonies of one bloc and the one closing that bloc's run where another bloc's
     *         follows, since two parting lengths in one band would read as a claim about the
     *         blocs they part. The setting screen calls it a separator, the counting rule an
     *         interjection: the same run under a name a player reads and a name the design
     *         states it by
     */
    public static int getPoliticalMapRibbonInterjectionLength() {
        return KmuLunaSettings.readInt(
            RIBBON_INTERJECTION_LENGTH_FIELD,
            DEFAULT_RIBBON_INTERJECTION_LENGTH);
    }

    /**
     * @return the multiplier on each colony's base size rating - a visible market's
     *         own size, or a hidden market's chosen base size (its real size or the
     *         fixed weight) - before the station and patrol bonuses and stability fold
     *         in; 1.0 by default (raw size), below 1 flattens the gap between large and
     *         small colonies and above 1 sharpens it
     */
    public static double getColonySizeWeight() {
        return KmuLunaSettings.readDouble(COLONY_SIZE_WEIGHT_FIELD, DEFAULT_COLONY_SIZE_WEIGHT);
    }

    /**
     * @return how a hidden market's base size rating is chosen: NORMAL by its real
     *         colony size like any colony, or FIXED at the hidden-market fixed weight
     *         regardless of size; FIXED by default so a large secret base does not
     *         outweigh the open colonies around it
     */
    public static HiddenMarketScalingChoice getHiddenMarketScaling() {
        return KmuLunaSettings.readChoice(
            HIDDEN_MARKET_SCALING_FIELD,
            DEFAULT_HIDDEN_MARKET_SCALING);
    }

    /**
     * @return the fixed size rating a hidden market folds in at while its scaling is
     *         FIXED, in place of its real size, before the colony size weight and
     *         stability apply; 1.0 by default. Unread while the scaling is NORMAL
     */
    public static double getHiddenMarketFixedWeight() {
        return KmuLunaSettings.readDouble(
            HIDDEN_MARKET_FIXED_WEIGHT_FIELD,
            DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT);
    }

    /**
     * @return the master switch for stability scaling: when on, each dominance factor
     *         is cut at low stability by its own low-stability penalty; on by default,
     *         off ranks colonies by their raw weighted size, station, and patrol sum
     */
    public static boolean shouldWeighDominanceByStability() {
        return KmuLunaSettings.readBoolean(
            STABILITY_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_STABILITY_WEIGHS_DOMINANCE);
    }

    /**
     * @return how much a colony's own size weight is cut at zero stability, 0..1: 1
     *         removes it entirely (half at 5, full at 10), 0.5 leaves half, 0 ignores
     *         stability for colony size; 1.0 by default. Applied only while stability
     *         weighting is on
     */
    public static double getNormalLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            NORMAL_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_NORMAL_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a market with an attached defensive station gains the station
     *         weight in size points toward its system's dominance; on by default, off
     *         ranks markets without any station bonus
     */
    public static boolean shouldWeighDominanceByStation() {
        return KmuLunaSettings.readBoolean(
            STATION_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_STATION_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points an attached defensive station adds to a visible colony's
     *         dominance contribution, before its low-stability penalty applies; 1.0 by
     *         default, below 1 softens the station bonus and above 1 sharpens it. A
     *         hidden market earns this scaled by the hidden-market station fraction
     */
    public static double getStationWeight() {
        return KmuLunaSettings.readDouble(STATION_WEIGHT_FIELD, DEFAULT_STATION_WEIGHT);
    }

    /**
     * @return the fraction of the station weight a station on a hidden market earns,
     *         0..1 - so a fortified secret base reads above a bare unstationed hidden
     *         market without matching an openly held stationed colony; half by default
     */
    public static double getStationHiddenMarketRate() {
        return KmuLunaSettings.readDouble(
            STATION_HIDDEN_MARKET_RATE_FIELD,
            DEFAULT_STATION_HIDDEN_MARKET_RATE);
    }

    /**
     * @return how much the station bonus is cut at zero stability, 0..1: 1 removes it
     *         entirely, 0.5 leaves half, 0 keeps the full bonus regardless of
     *         stability; 0.5 by default. Applied only while stability weighting is on
     */
    public static double getStationLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            STATION_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_STATION_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a colony gains size points toward its system's dominance for the
     *         patrols it fields, weighted by patrol size; off by default, so patrol
     *         strength does not sway dominance until the player opts in
     */
    public static boolean shouldWeighDominanceByPatrols() {
        return KmuLunaSettings.readBoolean(
            PATROL_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_PATROL_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points each small (light) patrol a colony fields adds to its
     *         dominance contribution, before the patrol low-stability penalty applies;
     *         0.25 by default. Unread while patrol weighting is off
     */
    public static double getPatrolSmallWeight() {
        return KmuLunaSettings.readDouble(PATROL_SMALL_WEIGHT_FIELD, DEFAULT_PATROL_SMALL_WEIGHT);
    }

    /**
     * @return the size points each medium patrol a colony fields adds to its dominance
     *         contribution, before the patrol low-stability penalty applies; 0.5 by
     *         default. Unread while patrol weighting is off
     */
    public static double getPatrolMediumWeight() {
        return KmuLunaSettings.readDouble(PATROL_MEDIUM_WEIGHT_FIELD, DEFAULT_PATROL_MEDIUM_WEIGHT);
    }

    /**
     * @return the size points each large (heavy) patrol a colony fields adds to its
     *         dominance contribution, before the patrol low-stability penalty applies;
     *         1.0 by default. Unread while patrol weighting is off
     */
    public static double getPatrolLargeWeight() {
        return KmuLunaSettings.readDouble(PATROL_LARGE_WEIGHT_FIELD, DEFAULT_PATROL_LARGE_WEIGHT);
    }

    /**
     * @return how much a colony's patrol bonus is cut at zero stability, 0..1: 1
     *         removes it entirely, 0.5 leaves half, 0 keeps the full bonus regardless
     *         of stability; 0.5 by default. Applied only while stability weighting is on
     */
    public static double getPatrolLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
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
        return KmuLunaSettings.readInt(CELL_BOUND_SEGMENTS_FIELD, DEFAULT_CELL_BOUND_SEGMENTS);
    }

    /**
     * @return how far each system's territory reaches into empty space before its
     *         frontier bound closes it off, in world units; higher lets each system
     *         colour more open space (so distant systems' territories meet and merge),
     *         lower pulls every territory in tight around its own systems
     */
    public static double getPoliticalMapCellRadius() {
        return KmuLunaSettings.readDouble(CELL_RADIUS_FIELD, DEFAULT_CELL_RADIUS);
    }

    /**
     * @return the corner-rounding radius of the national border, in world
     *         units; higher rounds the cluster outline more
     */
    public static double getPoliticalMapBorderCornerRadius() {
        return KmuLunaSettings.readDouble(
            BORDER_CORNER_RADIUS_FIELD,
            DEFAULT_BORDER_CORNER_RADIUS);
    }

    /**
     * @return the arc segments per rounded corner of the national border; higher is
     *         smoother
     */
    public static int getPoliticalMapBorderCornerSegments() {
        return KmuLunaSettings.readInt(
            BORDER_CORNER_SEGMENTS_FIELD,
            DEFAULT_BORDER_CORNER_SEGMENTS);
    }

    /**
     * @return the interior angle below which a national-border corner is chamfered
     *         flat rather than rounded, in radians (the setting is authored in
     *         degrees and converted here, since the rounding math works in radians)
     */
    public static double getPoliticalMapBorderChamferAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_CHAMFER_ANGLE_FIELD,
            DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES));
    }

    /**
     * @return the depth (world units) up to which a sharp corner counts as a spike
     *         to sand off the border before rounding; a sharp corner that juts farther
     *         than this is real shape and is kept. Zero disables the spike pass
     */
    public static double getPoliticalMapBorderSpikeHeight() {
        return KmuLunaSettings.readDouble(BORDER_SPIKE_HEIGHT_FIELD, DEFAULT_BORDER_SPIKE_HEIGHT);
    }

    /**
     * @return the interior angle (radians) below which a shallow corner counts as a
     *         spike to sand off the border before rounding; a gentler corner is kept.
     *         Zero disables the spike pass
     */
    public static double getPoliticalMapBorderSpikeAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
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
        return KmuLunaSettings.readBoolean(ROUND_CORNERS_FIELD, DEFAULT_ROUND_CORNERS);
    }

    /**
     * @return whether the spike-sanding pass runs before corner rounding; on by
     *         default. When off the spike height and angle knobs go unread and needle
     *         or cusp protrusions are left in the border for the rounding to meet
     */
    public static boolean shouldSandBorderSpikes() {
        return KmuLunaSettings.readBoolean(SAND_SPIKES_FIELD, DEFAULT_SAND_SPIKES);
    }

    /**
     * @return the perpendicular gap between the diagonal hatch lines filling the filter's
     *         contested cluster, in world units; lower packs the hatch denser, higher opens
     *         it up
     */
    public static double getPoliticalMapHatchSpacing() {
        return KmuLunaSettings.readDouble(HATCH_SPACING_FIELD, DEFAULT_HATCH_SPACING);
    }

    /**
     * @return the direction the contested-cluster hatch lines run, in radians (the setting
     *         is authored in degrees off horizontal and converted here, since the hatch math
     *         works in radians)
     */
    public static double getPoliticalMapHatchAngleRadians() {
        return Math.toRadians(
            KmuLunaSettings.readDouble(HATCH_ANGLE_FIELD, DEFAULT_HATCH_ANGLE_DEGREES));
    }

    /**
     * @return the line width the contested-cluster hatch strokes at, in pixels; higher makes the
     *         contested texture read heavier without touching the solid fill or the line spacing
     */
    public static double getPoliticalMapHatchWidth() {
        return KmuLunaSettings.readDouble(HATCH_WIDTH_FIELD, DEFAULT_HATCH_WIDTH);
    }

    /**
     * @return whether the contested-cluster hatch is antialiased rather than stroked hard-edged;
     *         off by default. Covers the hatch alone - the fills and borders around it pick their
     *         own line quality - and a GL bridge that ignores the smoothing hint leaves it inert
     */
    public static boolean shouldSmoothHatchLines() {
        return KmuLunaSettings.readBoolean(HATCH_SMOOTHING_FIELD, DEFAULT_HATCH_SMOOTHING);
    }

    /**
     * @return how far apart two of one hatch line's crossings may sit and still merge into one
     *         segment, as a fraction of the hatch spacing (the setting is authored as a
     *         percentage of it and converted here). Read only by the merging joining
     */
    public static double getPoliticalMapHatchJoinToleranceFraction() {
        return KmuLunaSettings.readDouble(
            HATCH_JOIN_TOLERANCE_FIELD,
            DEFAULT_HATCH_JOIN_TOLERANCE_PERCENT)
            / HATCH_JOIN_TOLERANCE_PERCENT_PER_UNIT;
    }

    /**
     * @return whether the political map paints its own hover halo and cell wash; on by default.
     *         The bottom tier, so it can only withhold effects the two global tiers already allow
     */
    public static boolean getPoliticalMapHoverEffectsEnabled() {
        return KmuLunaSettings.readBoolean(
            LAYER_HOVER_EFFECTS_ENABLED_FIELD,
            DEFAULT_LAYER_HOVER_EFFECTS_ENABLED);
    }

    /**
     * @return whether the political map shows its own hover box - the hovered system's standings;
     *         on by default. The bottom tier, so it can only withhold the box the two global tiers
     *         already allow, and it leaves another layer's box alone
     */
    public static boolean getPoliticalMapHoverTooltipEnabled() {
        return KmuLunaSettings.readBoolean(
            LAYER_HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_LAYER_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return which palette colour of the cell under the cursor the hover halo and cell wash
     *         both draw in; the primary (bright) colour by default. Turning the highlight off is
     *         the enable toggle's job, not a colour choice
     */
    public static FactionPaletteChoice getPoliticalMapHoverHighlightColour() {
        return KmuLunaSettings.readChoice(
            HOVER_HIGHLIGHT_COLOR_FIELD,
            DEFAULT_HOVER_HIGHLIGHT_COLOUR);
    }

    /**
     * @return the alpha of the hover halo's innermost stroke, 0..1 - its brightest layer,
     *         which the outer layers fade away from
     */
    public static double getPoliticalMapHoverGlowOpacity() {
        return KmuLunaSettings.readDouble(HOVER_GLOW_OPACITY_FIELD, DEFAULT_HOVER_GLOW_OPACITY);
    }

    /**
     * @return how far the hover halo reaches off the hovered frontier, in pixels - the width
     *         of its widest, faintest stroke
     */
    public static double getPoliticalMapHoverGlowWidth() {
        return KmuLunaSettings.readDouble(HOVER_GLOW_WIDTH_FIELD, DEFAULT_HOVER_GLOW_WIDTH);
    }

    /**
     * @return how many strokes the hover halo accumulates from; more buys a smoother falloff
     *         at a stroke of the whole frontier apiece
     */
    public static int getPoliticalMapHoverGlowLayers() {
        return KmuLunaSettings.readInt(HOVER_GLOW_LAYERS_FIELD, DEFAULT_HOVER_GLOW_LAYERS);
    }

    /**
     * @return how much of its alpha the hover halo gives up at the bottom of a breath, 0..1;
     *         0 holds it steady
     */
    public static double getPoliticalMapHoverGlowPulseStrength() {
        return KmuLunaSettings.readDouble(
            HOVER_GLOW_PULSE_STRENGTH_FIELD,
            DEFAULT_HOVER_GLOW_PULSE_STRENGTH);
    }

    /**
     * @return how long one breath of the hover halo's pulse takes, in seconds; ignored while
     *         the pulse strength is 0
     */
    public static double getPoliticalMapHoverGlowPulsePeriod() {
        return KmuLunaSettings.readDouble(
            HOVER_GLOW_PULSE_PERIOD_FIELD,
            DEFAULT_HOVER_GLOW_PULSE_PERIOD_SECONDS);
    }

    /**
     * @return the alpha the hovered cell's wash brightens its painted extent by, 0..1
     */
    public static double getPoliticalMapHoverWashOpacity() {
        return KmuLunaSettings.readDouble(HOVER_WASH_OPACITY_FIELD, DEFAULT_HOVER_WASH_OPACITY);
    }

    /**
     * @return the alpha the hovered cell's own outline traces at, 0..1 - the only cue a cell
     *         surrounded by its own faction has, so it reads apart from the wash
     */
    public static double getPoliticalMapHoverWashOutlineOpacity() {
        return KmuLunaSettings.readDouble(
            HOVER_WASH_OUTLINE_OPACITY_FIELD,
            DEFAULT_HOVER_WASH_OUTLINE_OPACITY);
    }

    /**
     * @return the line width the hovered cell's outline traces at, in pixels
     */
    public static double getPoliticalMapHoverWashOutlineWidth() {
        return KmuLunaSettings.readDouble(
            HOVER_WASH_OUTLINE_WIDTH_FIELD,
            DEFAULT_HOVER_WASH_OUTLINE_WIDTH);
    }

    /**
     * @return whether the political map draws every faction's colonies, including ones
     *         the player has not discovered yet - bypasses the known-to-player gate so
     *         an undiscovered colony still folds into its system's dominance,
     *         inhabitation, and cell geometry; off by default, a reveal aid for
     *         inspecting the whole sector's politics
     */
    public static boolean getPoliticalMapShowAllFactions() {
        return KmuLunaSettings.readBoolean(SHOW_ALL_FACTIONS_FIELD, DEFAULT_SHOW_ALL_FACTIONS);
    }

    /**
     * @return whether the political map seeds a cell for every star system, not just the
     *         reachable, visible, or inhabited ones - bypasses the visibility rule so a
     *         system the map would otherwise omit still gets geometry; off by default, a
     *         reveal aid for inspecting the full cell partition
     */
    public static boolean shouldForceAllSystemsOnMap() {
        return KmuLunaSettings.readBoolean(
            FORCE_ALL_SYSTEMS_ON_MAP_FIELD,
            DEFAULT_FORCE_ALL_SYSTEMS_ON_MAP);
    }

    /**
     * @return whether the political map draws the per-cluster label anchors - a dot at
     *         each contiguous cluster's centre and, in green, the accepted label line
     *         its fit produced; off by default, a diagnostic for the map labels
     *         themselves. Master switch for the anchor overlay: the framework's rejected-
     *         and unbiased-axis toggles only add lines while this is on
     */
    public static boolean getPoliticalMapShowClusterAnchors() {
        return KmuLunaSettings.readBoolean(
            SHOW_CLUSTER_ANCHORS_FIELD,
            DEFAULT_SHOW_CLUSTER_ANCHORS);
    }

    /**
     * @return whether to replace the normal political-map render with the border-tracing
     *         diagnostic that layers the smoothing pipeline's stages (base, despiked,
     *         rounded) in distinct colours; off by default. Respects the two smoothing
     *         gates, so a stage draws only when its pass ran
     */
    public static boolean shouldTraceBordersForDebug() {
        return KmuLunaSettings.readBoolean(
            DEBUG_BORDER_TRACING_FIELD,
            DEFAULT_DEBUG_BORDER_TRACING);
    }

}
