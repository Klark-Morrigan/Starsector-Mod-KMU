package kmu.settings;

/**
 * The political map layer's own LunaLib knobs: how this one layer paints the sector and
 * how it decides who holds what.
 *
 * <p>One of the knob classes {@link KmuLunaSettings} splits by reader. Everything here is
 * read by {@code kmu.maplayers.politicalmap} and nothing else, which is what lets the
 * framework's own settings class stay clear of a feature's vocabulary.
 *
 * <p>Several knobs here shape geometry the framework owns - the cell partition, the border
 * smoothing passes, the hatch fill - and are filed here all the same, because the framework
 * takes those values as parameters and never reads a setting: this layer resolves them and
 * hands them down. A knob is filed by who reads it, not by whose geometry it moves, which is
 * why some of them sit on the framework's own {@code Map - Dev} tab.
 *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The
 * prose here answers only what that column cannot: why a default is the number it is, and
 * what a caller has to know to use the value.
 */
public final class KmuPoliticalMapSettings {

    private static final String FACTION_OUTER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_faction_outerBorder_colour";
    private static final String FACTION_OUTER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_faction_outerBorder_opacity";
    private static final String FACTION_OUTER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_faction_outerBorder_width";
    private static final String FACTION_INNER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_faction_innerBorder_colour";
    private static final String FACTION_INNER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_faction_innerBorder_opacity";
    private static final String FACTION_INNER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_faction_innerBorder_width";
    private static final String FACTION_FILL_COLOR_FIELD = "kmu_map_politics_visuals_faction_fill_colour";
    private static final String FACTION_FILL_OPACITY_FIELD = "kmu_map_politics_visuals_faction_fill_opacity";
    private static final String FACTION_NAME_OPACITY_FIELD = "kmu_map_politics_visuals_faction_name_opacity";

    private static final String INDEPENDENT_OUTER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_independent_outerBorder_colour";
    private static final String INDEPENDENT_OUTER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_outerBorder_opacity";
    private static final String INDEPENDENT_OUTER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_independent_outerBorder_width";
    private static final String INDEPENDENT_INNER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_independent_innerBorder_colour";
    private static final String INDEPENDENT_INNER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_innerBorder_opacity";
    private static final String INDEPENDENT_INNER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_independent_innerBorder_width";
    private static final String INDEPENDENT_FILL_COLOR_FIELD =
        "kmu_map_politics_visuals_independent_fill_colour";
    private static final String INDEPENDENT_FILL_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_fill_opacity";
    private static final String INDEPENDENT_NAME_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_name_opacity";

    // A factionless category carries no colour field: it has no faction palette to pick from, so
    // it always paints in the shared neutral colour and the opacity knobs alone decide what shows.
    private static final String DECIVILISED_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_decivilised_border_opacity";
    private static final String DECIVILISED_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_decivilised_border_width";
    private static final String DECIVILISED_FILL_OPACITY_FIELD =
        "kmu_map_politics_visuals_decivilised_fill_opacity";
    private static final String UNINHABITED_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_uninhabited_border_opacity";
    private static final String UNINHABITED_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_uninhabited_border_width";

    // Only the depth of each recede treatment is a field. The toggles that switch them on are
    // sidebar-only per-save choices held in sector memory, because every LunaLib field renders on
    // a settings tab and a per-save choice cannot.
    private static final String ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD =
        "kmu_map_politics_visuals_styleMutators_mutedOpacityModifier";
    private static final String DESATURATION_DARKENING_FIELD =
        "kmu_map_politics_visuals_styleMutators_desaturationDarkening";
    private static final String PRESENCE_LIGHTENING_FIELD =
        "kmu_map_politics_visuals_styleMutators_presenceLightening";

    // The band's on/off is a real switch rather than a zeroed opacity: a band paints in the palette
    // colours of whoever is present and so has no shade of its own to take away.
    private static final String RIBBON_ENABLED_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_areEnabled";
    private static final String RIBBON_UNCONTESTED_SHORT_RUNS_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_shouldShortenUncontestedRuns";
    private static final String RIBBON_KEEP_CLEAR_OF_NAMES_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_shouldKeepClearOfNames";
    private static final String RIBBON_NAME_CLEARANCE_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_nameClearance";
    private static final String RIBBON_ALWAYS_DRAWN_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_areAlwaysDrawn";
    private static final String RIBBON_WIDTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_width";
    private static final String RIBBON_INSET_PAD_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_insetPad";
    private static final String RIBBON_SEGMENT_LENGTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_segmentLength";
    private static final String RIBBON_INTERJECTION_LENGTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_interjectionLength";

    private static final String LAYER_HOVER_EFFECTS_ENABLED_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_areEffectsEnabled";
    private static final String LAYER_HOVER_TOOLTIP_ENABLED_FIELD =
        "kmu_map_politics_visuals_hoverTooltip_isEnabled";

    private static final String HOVER_HIGHLIGHT_COLOR_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_colour";
    private static final String HOVER_GLOW_OPACITY_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_glow_opacity";
    private static final String HOVER_GLOW_WIDTH_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_glow_width";
    private static final String HOVER_GLOW_LAYERS_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_glow_layers";
    private static final String HOVER_GLOW_PULSE_STRENGTH_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_glow_pulseStrength";
    private static final String HOVER_GLOW_PULSE_PERIOD_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_glow_pulsePeriod";
    private static final String HOVER_WASH_OPACITY_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_wash_opacity";
    private static final String HOVER_WASH_OUTLINE_OPACITY_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_wash_outlineOpacity";
    private static final String HOVER_WASH_OUTLINE_WIDTH_FIELD =
        "kmu_map_politics_visuals_hoverHighlight_wash_outlineWidth";

    private static final String PREVIEW_HIGHLIGHT_COLOUR_FIELD =
        "kmu_map_politics_visuals_previewHighlight_colour";
    private static final String PREVIEW_GLOW_OPACITY_FIELD =
        "kmu_map_politics_visuals_previewHighlight_glow_opacity";
    private static final String PREVIEW_GLOW_WIDTH_FIELD =
        "kmu_map_politics_visuals_previewHighlight_glow_width";
    private static final String PREVIEW_GLOW_LAYERS_FIELD =
        "kmu_map_politics_visuals_previewHighlight_glow_layers";
    private static final String PREVIEW_GLOW_PULSE_STRENGTH_FIELD =
        "kmu_map_politics_visuals_previewHighlight_glow_pulseStrength";
    private static final String PREVIEW_GLOW_PULSE_PERIOD_FIELD =
        "kmu_map_politics_visuals_previewHighlight_glow_pulsePeriod";
    private static final String PREVIEW_WASH_OPACITY_FIELD =
        "kmu_map_politics_visuals_previewHighlight_wash_opacity";
    private static final String PREVIEW_WASH_OUTLINE_OPACITY_FIELD =
        "kmu_map_politics_visuals_previewHighlight_wash_outlineOpacity";
    private static final String PREVIEW_WASH_OUTLINE_WIDTH_FIELD =
        "kmu_map_politics_visuals_previewHighlight_wash_outlineWidth";

    // One field per visible sub-layer, because the sub-layers are what a player sees as separate
    // things. A treatment that cannot stand apart from one of them - the contested hatch, the hover
    // highlight, the diagnostics - follows the choice its own picture depends on rather than
    // carrying a knob nobody could answer independently.
    private static final String NEBULA_DRAW_ORDER_FILLS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_fills";
    private static final String NEBULA_DRAW_ORDER_BORDERS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_borders";
    private static final String NEBULA_DRAW_ORDER_RIBBONS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_presenceRibbons";
    private static final String NEBULA_DRAW_ORDER_LABELS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_labels";

    // The dominance rules, which change the map's political verdicts rather than its styling. All
    // fold into one DominanceRules read once per resolution pass.
    private static final String COLONY_SIZE_WEIGHT_FIELD =
        "kmu_map_politics_domination_colonySize_weight_base";
    private static final String HIDDEN_MARKET_SCALING_FIELD =
        "kmu_map_politics_domination_hiddenMarkets_weight_scaling";
    private static final String HIDDEN_MARKET_FIXED_WEIGHT_FIELD =
        "kmu_map_politics_domination_hiddenMarkets_weight_fixed";
    private static final String STABILITY_WEIGHS_DOMINANCE_FIELD =
        "kmu_map_politics_domination_stability_isEnabled";
    private static final String NORMAL_LOW_STABILITY_PENALTY_FIELD =
        "kmu_map_politics_domination_colonySize_weight_penalty_lowStability";
    private static final String STATION_WEIGHS_DOMINANCE_FIELD =
        "kmu_map_politics_domination_stations_weight_isEnabled";
    private static final String STATION_WEIGHT_FIELD =
        "kmu_map_politics_domination_stations_weight_base";
    private static final String STATION_HIDDEN_MARKET_RATE_FIELD =
        "kmu_map_politics_domination_stations_weight_hiddenMarketRate";
    private static final String STATION_LOW_STABILITY_PENALTY_FIELD =
        "kmu_map_politics_domination_stations_weight_penalty_lowStability";
    private static final String PATROL_WEIGHS_DOMINANCE_FIELD =
        "kmu_map_politics_domination_patrols_weight_isEnabled";
    private static final String PATROL_SMALL_WEIGHT_FIELD =
        "kmu_map_politics_domination_patrols_weight_small";
    private static final String PATROL_MEDIUM_WEIGHT_FIELD =
        "kmu_map_politics_domination_patrols_weight_medium";
    private static final String PATROL_LARGE_WEIGHT_FIELD =
        "kmu_map_politics_domination_patrols_weight_large";
    private static final String PATROL_LOW_STABILITY_PENALTY_FIELD =
        "kmu_map_politics_domination_patrols_weight_penalty_lowStability";

    // The cell knobs reseed the partition itself, so they feed the geometry rebuild; every border,
    // hatch and diagnostic knob below restyles fixed geometry through the drawables rebuild. Which
    // of the two a knob triggers is the whole of what its cost at runtime depends on.
    private static final String CELL_BOUND_SEGMENTS_FIELD =
        "kmu_map_dev_cellGeometry_boundSegments";
    private static final String CELL_RADIUS_FIELD =
        "kmu_map_dev_cellGeometry_radius";

    // The border shaping in pipeline order after the framework's own tracing, each pass led by the
    // gate that turns it off whole without zeroing the knobs beneath it.
    private static final String SAND_SPIKES_FIELD =
        "kmu_map_dev_spikeSanding_isEnabled";
    private static final String BORDER_SPIKE_HEIGHT_FIELD =
        "kmu_map_dev_spikeSanding_height";
    private static final String BORDER_SPIKE_ANGLE_FIELD =
        "kmu_map_dev_spikeSanding_angle";

    private static final String ROUND_CORNERS_FIELD =
        "kmu_map_dev_cornerRounding_isEnabled";
    private static final String BORDER_CORNER_RADIUS_FIELD =
        "kmu_map_dev_cornerRounding_radius";
    private static final String BORDER_CORNER_SEGMENTS_FIELD =
        "kmu_map_dev_cornerRounding_segments";
    private static final String BORDER_CHAMFER_ANGLE_FIELD =
        "kmu_map_dev_cornerRounding_chamferAngle";
    private static final String BORDER_ROUND_BELOW_ANGLE_FIELD =
        "kmu_map_dev_cornerRounding_angleThreshold";

    private static final String HATCH_SPACING_FIELD =
        "kmu_map_dev_hatchFill_spacing";
    private static final String HATCH_ANGLE_FIELD =
        "kmu_map_dev_hatchFill_angle";
    private static final String HATCH_WIDTH_FIELD =
        "kmu_map_dev_hatchFill_width";
    private static final String HATCH_SMOOTHING_FIELD =
        "kmu_map_dev_hatchFill_isSmoothed";
    private static final String HATCH_JOIN_TOLERANCE_FIELD =
        "kmu_map_dev_hatchJoining_tolerance";

    private static final String SHOW_CLUSTER_ANCHORS_FIELD =
        "kmu_map_dev_diagnostics_labels_boxes_areShown";
    private static final String SHOW_RIBBON_PATHS_FIELD =
        "kmu_map_dev_diagnostics_cells_ribbons_arePathsShown";
    private static final String DEBUG_BORDER_TRACING_FIELD =
        "kmu_map_dev_diagnostics_cells_borders_isDebugTracingShown";

    // Fallbacks answering only while a setting is read before LunaLib has loaded it; the live
    // values come from LunaLib. Each mirrors the defaultValue column in
    // data/config/LunaSettings.csv, which is the number a fresh player is actually given, and is
    // held against it by the settings suite. Only the numbers whose reasoning is not in that
    // column carry a note.
    private static final FactionPaletteChoice DEFAULT_FACTION_OUTER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_OUTER_BORDER_OPACITY = 1.0;
    private static final double DEFAULT_FACTION_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_INNER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_FACTION_INNER_BORDER_WIDTH = 10.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_FILL_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_FILL_OPACITY = 0.4;
    private static final double DEFAULT_FACTION_NAME_OPACITY = 1.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY = 0.5;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH = 10.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_FILL_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_FILL_OPACITY = 0.2;
    private static final double DEFAULT_INDEPENDENT_NAME_OPACITY = 1.0;
    private static final double DEFAULT_DECIVILISED_BORDER_OPACITY = 0.45;
    private static final double DEFAULT_DECIVILISED_BORDER_WIDTH = 3.0;

    // Set against the two fills it has to stay behind: a living faction's 0.4 and independent
    // space's 0.2, a dead colony having really been settled but held by nobody.
    private static final double DEFAULT_DECIVILISED_FILL_OPACITY = 0.15;
    private static final double DEFAULT_UNINHABITED_BORDER_OPACITY = 0.15;
    private static final double DEFAULT_UNINHABITED_BORDER_WIDTH = 3.0;
    private static final double DEFAULT_ALLIANCE_MUTED_OPACITY_MODIFIER = 0.5;
    private static final double DEFAULT_DESATURATION_DARKENING = 0.5;

    // All the way to white, because the neutral and the Independent grey the backdrop sinks from
    // are the same shade: a partial lift would leave a spared cell reading against the value the
    // receded fills started at, where the point of sparing it is to be unmistakable at a glance.
    private static final double DEFAULT_PRESENCE_LIGHTENING = 1.0;
    private static final boolean DEFAULT_RIBBON_ENABLED = true;
    private static final boolean DEFAULT_RIBBON_UNCONTESTED_SHORT_RUNS = true;
    private static final boolean DEFAULT_RIBBON_KEEP_CLEAR_OF_NAMES = true;

    // A cell that says nothing and a cell refused the room to speak look identical on the map, so
    // the default takes the reading that shows the sector: the room a band wants is this design's
    // own business, and no player asked for a system's holdings to go unreported because its name
    // landed across the ring.
    private static final boolean DEFAULT_RIBBON_ALWAYS_DRAWN = true;

    // The words rather than the box they were fitted into: a fitted box is the chord the search
    // accepted, and a chord is accepted as soon as it beats the widest line, so it reserves room at
    // both ends that nothing is ever drawn in.
    private static final RibbonNameClearanceChoice DEFAULT_RIBBON_NAME_CLEARANCE =
        RibbonNameClearanceChoice.WORDS;

    // Settled by eye on the shipped sector rather than derived: a band as thick as its own clearance
    // reads as a stripe laid inside the border at the zooms the map is used at, where a finer one
    // disappeared into the border it follows.
    private static final double DEFAULT_RIBBON_WIDTH = 200.0;
    private static final double DEFAULT_RIBBON_INSET_PAD = 200.0;

    // Whole widths rather than fractions: a run is a count of holdings expressed as a length, and
    // how large a width is in the world is the width knob's business.
    private static final int DEFAULT_RIBBON_SEGMENT_LENGTH = 3;
    private static final int DEFAULT_RIBBON_INTERJECTION_LENGTH = 1;
    private static final boolean DEFAULT_LAYER_HOVER_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_LAYER_HOVER_TOOLTIP_ENABLED = true;

    // Tuned to sit over the fills without swamping them, the fills themselves painting at 0.4.
    private static final FactionPaletteChoice DEFAULT_HOVER_HIGHLIGHT_COLOUR =
        FactionPaletteChoice.SECONDARY;
    private static final double DEFAULT_HOVER_GLOW_OPACITY = 0.5;
    private static final double DEFAULT_HOVER_GLOW_WIDTH = 14.0;
    private static final int DEFAULT_HOVER_GLOW_LAYERS = 4;
    private static final double DEFAULT_HOVER_GLOW_PULSE_STRENGTH = 0.0;
    private static final double DEFAULT_HOVER_GLOW_PULSE_PERIOD_SECONDS = 0.2;
    private static final double DEFAULT_HOVER_WASH_OPACITY = 0.35;
    private static final double DEFAULT_HOVER_WASH_OUTLINE_OPACITY = 0.8;
    private static final double DEFAULT_HOVER_WASH_OUTLINE_WIDTH = 2.0;

    private static final FactionPaletteChoice DEFAULT_PREVIEW_HIGHLIGHT_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_PREVIEW_GLOW_OPACITY = 0.6;
    private static final double DEFAULT_PREVIEW_GLOW_WIDTH = 6.0;
    private static final int DEFAULT_PREVIEW_GLOW_LAYERS = 3;
    private static final double DEFAULT_PREVIEW_GLOW_PULSE_STRENGTH = 0.5;
    private static final double DEFAULT_PREVIEW_GLOW_PULSE_PERIOD_SECONDS = 1.5;
    private static final double DEFAULT_PREVIEW_WASH_OPACITY = 0.5;
    private static final double DEFAULT_PREVIEW_WASH_OUTLINE_OPACITY = 0.9;
    private static final double DEFAULT_PREVIEW_WASH_OUTLINE_WIDTH = 2.0;

    // The cell geometry and the bands laid on it sink beneath the nebulae, and only the names rise
    // clear of them - words being the one thing the haze decides whether a reader gets at all,
    // where a shape it dims is merely quieter.
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_FILLS =
        NebulaDrawOrderChoice.BELOW;
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_BORDERS =
        NebulaDrawOrderChoice.BELOW;
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_RIBBONS =
        NebulaDrawOrderChoice.BELOW;
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_LABELS =
        NebulaDrawOrderChoice.ABOVE;

    private static final double DEFAULT_COLONY_SIZE_WEIGHT = 1.0;
    private static final HiddenMarketScalingChoice DEFAULT_HIDDEN_MARKET_SCALING =
        HiddenMarketScalingChoice.FIXED;
    private static final double DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT = 1.0;
    private static final boolean DEFAULT_STABILITY_WEIGHS_DOMINANCE = true;
    private static final double DEFAULT_NORMAL_LOW_STABILITY_PENALTY = 1.0;
    private static final boolean DEFAULT_STATION_WEIGHS_DOMINANCE = true;
    private static final double DEFAULT_STATION_WEIGHT = 1.0;
    private static final double DEFAULT_STATION_HIDDEN_MARKET_RATE = 0.5;
    private static final double DEFAULT_STATION_LOW_STABILITY_PENALTY = 0.5;
    private static final boolean DEFAULT_PATROL_WEIGHS_DOMINANCE = true;
    private static final double DEFAULT_PATROL_SMALL_WEIGHT = 0.3;
    private static final double DEFAULT_PATROL_MEDIUM_WEIGHT = 0.6;
    private static final double DEFAULT_PATROL_LARGE_WEIGHT = 1.2;
    private static final double DEFAULT_PATROL_LOW_STABILITY_PENALTY = 0.5;

    // Mirrors VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS, the geometric default this setting
    // overrides; restated as a literal like every fallback here so this class stays decoupled from
    // the geometry library.
    private static final int DEFAULT_CELL_BOUND_SEGMENTS = 48;
    private static final double DEFAULT_CELL_RADIUS = 4000.0;
    private static final boolean DEFAULT_SAND_SPIKES = false;
    private static final double DEFAULT_BORDER_SPIKE_HEIGHT = 150.0;
    private static final double DEFAULT_BORDER_SPIKE_ANGLE_DEGREES = 60.0;
    private static final boolean DEFAULT_ROUND_CORNERS = true;
    private static final double DEFAULT_BORDER_CORNER_RADIUS = 300.0;
    private static final int DEFAULT_BORDER_CORNER_SEGMENTS = 3;
    private static final double DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES = 35.0;

    // Under where a cluster border's own arc samples meet, which is about 173 degrees at the
    // shipped cell-bound sampling, with a couple of degrees to spare rather than right at the edge:
    // the exact joint angle is a fact about the sampling, and a default flush against it would flip
    // to rounding every sample the moment that sampling coarsens. Above the joints the pass arcs the
    // samples along one run as well as the joins between runs - about twice the border vertices for
    // a line that moves some ten world units, a tenth of a pixel at the zoom a sector is read at.
    private static final double DEFAULT_BORDER_ROUND_BELOW_ANGLE_DEGREES = 170.0;

    // About ten lines across a default-reach cell, on a 45-degree diagonal at a hairline stroke.
    private static final double DEFAULT_HATCH_SPACING = 1000.0;
    private static final double DEFAULT_HATCH_ANGLE_DEGREES = 45.0;
    private static final double DEFAULT_HATCH_WIDTH = 50.0;

    // Hard-edged, which is the state the hatch was tuned and verified at.
    private static final boolean DEFAULT_HATCH_SMOOTHING = false;

    // A tenth of a percent of the spacing, which sits between two measured bounds rather than two
    // guessed ones. On a real sector the crossings the merge has to close disagree by around 1e-14
    // of the spacing - double rounding, not geometry - while the narrowest gap it must leave open,
    // where a territory genuinely stops, is around 0.4 of it. This is far enough above the rounding
    // to survive a coarser tessellation and still some four hundred times below the nearest real
    // break.
    //
    // Stored 0..100 in the CSV as a percentage and exposed as the fraction the merge works in,
    // because the slider rounds a Double to two decimals: a fraction authored directly would
    // collapse to zero the moment it was dragged.
    private static final double DEFAULT_HATCH_JOIN_TOLERANCE_PERCENT = 0.2;
    private static final double HATCH_JOIN_TOLERANCE_PERCENT_PER_UNIT = 100.0;

    private static final boolean DEFAULT_SHOW_CLUSTER_ANCHORS = false;
    private static final boolean DEFAULT_SHOW_RIBBON_PATHS = false;
    private static final boolean DEFAULT_DEBUG_BORDER_TRACING = false;

    private KmuPoliticalMapSettings() {
    }

    /**
     * @return which faction palette colour the outer (national) border draws in, or NONE to hide
     *         it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionOuterBorderColour() {
        return KmuLunaSettings.readChoice(
            FACTION_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_COLOUR);
    }

    /** @return the outer (national) border opacity for faction systems, 0..1; 1.0 by default */
    public static double getFactionOuterBorderOpacity() {
        return KmuLunaSettings.readDouble(
            FACTION_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_OPACITY);
    }

    /** @return the outer (national) border line width for faction systems, pixels; 3.0 by default */
    public static double getFactionOuterBorderWidth() {
        return KmuLunaSettings.readDouble(
            FACTION_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the inner (province seam) borders draw in, or NONE to
     *         hide them; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionInnerBorderColour() {
        return KmuLunaSettings.readChoice(
            FACTION_INNER_BORDER_COLOR_FIELD,
            DEFAULT_FACTION_INNER_BORDER_COLOUR);
    }

    /** @return the inner (province seam) border opacity for faction systems, 0..1; 0.1 by default */
    public static double getFactionInnerBorderOpacity() {
        return KmuLunaSettings.readDouble(
            FACTION_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_FACTION_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for faction systems, pixels; 10.0 by
     *         default
     */
    public static double getFactionInnerBorderWidth() {
        return KmuLunaSettings.readDouble(
            FACTION_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_FACTION_INNER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the territory fill draws in, or NONE to leave it
     *         unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionFillColour() {
        return KmuLunaSettings.readChoice(FACTION_FILL_COLOR_FIELD, DEFAULT_FACTION_FILL_COLOUR);
    }

    /** @return the fill opacity for faction systems, 0..1; 0.4 by default */
    public static double getFactionFillOpacity() {
        return KmuLunaSettings.readDouble(FACTION_FILL_OPACITY_FIELD, DEFAULT_FACTION_FILL_OPACITY);
    }

    /**
     * @return the opacity a faction cluster's name draws at, 0..1 of its owner's colour; 1.0 by
     *         default. The per-frame map-zoom fade the renderer applies composes on top of this
     */
    public static double getFactionNameOpacity() {
        return KmuLunaSettings.readDouble(FACTION_NAME_OPACITY_FIELD, DEFAULT_FACTION_NAME_OPACITY);
    }

    /**
     * @return which independent palette colour the outer (national) border draws in, or NONE to
     *         hide it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentOuterBorderColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR);
    }

    /**
     * @return the outer (national) border opacity for independent-held systems, 0..1; 0.5 by
     *         default
     */
    public static double getIndependentOuterBorderOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for independent-held systems, pixels; 3.0 by
     *         default
     */
    public static double getIndependentOuterBorderWidth() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the inner (province seam) borders draw in, or NONE
     *         to hide them; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentInnerBorderColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_INNER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR);
    }

    /**
     * @return the inner (province seam) border opacity for independent-held systems, 0..1; 0.1 by
     *         default
     */
    public static double getIndependentInnerBorderOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for independent-held systems, pixels;
     *         10.0 by default
     */
    public static double getIndependentInnerBorderWidth() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the territory fill draws in, or NONE to leave it
     *         unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentFillColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_FILL_COLOR_FIELD,
            DEFAULT_INDEPENDENT_FILL_COLOUR);
    }

    /** @return the fill opacity for independent-held systems, 0..1; 0.2 by default */
    public static double getIndependentFillOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_FILL_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_FILL_OPACITY);
    }

    /**
     * @return the opacity an independent-held cluster's name draws at, 0..1 of its owner's colour;
     *         1.0 by default. The per-frame map-zoom fade the renderer applies composes on top of
     *         this
     */
    public static double getIndependentNameOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_NAME_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_NAME_OPACITY);
    }

    /**
     * @return the outline opacity for decivilised systems, 0..1; 0.45 by default, 0 hiding the
     *         outline, the neutral colour being the only shade a factionless cell has
     */
    public static double getDecivilisedBorderOpacity() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_BORDER_OPACITY_FIELD,
            DEFAULT_DECIVILISED_BORDER_OPACITY);
    }

    /** @return the outline line width for decivilised systems, pixels; 3.0 by default */
    public static double getDecivilisedBorderWidth() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_BORDER_WIDTH_FIELD,
            DEFAULT_DECIVILISED_BORDER_WIDTH);
    }

    /**
     * @return the neutral-colour fill opacity for decivilised systems, 0..1; 0.15 by default, 0
     *         leaving them unfilled so only the outline draws
     */
    public static double getDecivilisedFillOpacity() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_FILL_OPACITY_FIELD,
            DEFAULT_DECIVILISED_FILL_OPACITY);
    }

    /** @return the outline opacity for uninhabited systems, 0..1; 0.15 by default */
    public static double getUninhabitedBorderOpacity() {
        return KmuLunaSettings.readDouble(
            UNINHABITED_BORDER_OPACITY_FIELD,
            DEFAULT_UNINHABITED_BORDER_OPACITY);
    }

    /** @return the outline line width for uninhabited systems, pixels; 3.0 by default */
    public static double getUninhabitedBorderWidth() {
        return KmuLunaSettings.readDouble(
            UNINHABITED_BORDER_WIDTH_FIELD,
            DEFAULT_UNINHABITED_BORDER_WIDTH);
    }

    /**
     * @return the fraction of its normal opacity a non-allied bloc's borders, fills, and name draw
     *         at while the alliances view mutes it, 0..1; 0.5 by default. Unread while the sidebar
     *         Mute toggle is off
     */
    public static double getPoliticalMapAllianceMutedOpacityModifier() {
        return KmuLunaSettings.readDouble(
            ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD,
            DEFAULT_ALLIANCE_MUTED_OPACITY_MODIFIER);
    }

    /**
     * @return how far a desaturated bloc's uniform Independent-based grey is sunk toward black, as
     *         the fraction of brightness removed, 0..1; 0.5 by default. The render pipeline
     *         resolves the palette once per pass, the view flagging only whether a bloc
     *         desaturates and never how dark
     */
    public static double getPoliticalMapDesaturationDarkening() {
        return KmuLunaSettings.readDouble(
            DESATURATION_DARKENING_FIELD,
            DEFAULT_DESATURATION_DARKENING);
    }

    /**
     * @return how far a factionless cell the spotlight spares is washed toward white, 0..1; 1.0 by
     *         default. Applied to RGB alone, so a grey stays the same grey and the cell cannot
     *         drift into reading as a faction colour
     */
    public static double getPoliticalMapPresenceLightening() {
        return KmuLunaSettings.readDouble(
            PRESENCE_LIGHTENING_FIELD,
            DEFAULT_PRESENCE_LIGHTENING);
    }

    /**
     * @return whether a cell draws the banded stroke naming the blocs that hold colonies in that
     *         system; on by default. Off skips the counting as well as the drawing, so a rebuild
     *         pays nothing for the bands at all
     */
    public static boolean shouldDrawPoliticalMapRibbons() {
        return KmuLunaSettings.readBoolean(RIBBON_ENABLED_FIELD, DEFAULT_RIBBON_ENABLED);
    }

    /**
     * @return whether the band on an uncontested cell draws each colony at a single width rather
     *         than at the authored run length; on by default. A cell some rival holds something in
     *         draws at the authored length whatever this says
     */
    public static boolean shouldShortenPoliticalMapUncontestedRibbonRuns() {
        return KmuLunaSettings.readBoolean(
            RIBBON_UNCONTESTED_SHORT_RUNS_FIELD,
            DEFAULT_RIBBON_UNCONTESTED_SHORT_RUNS);
    }

    /**
     * @return whether a band breaks around each drawn cluster name lying over its cell; on by
     *         default. Off lays every band along its whole ring and lets a name draw across it
     */
    public static boolean shouldKeepPoliticalMapRibbonsClearOfNames() {
        return KmuLunaSettings.readBoolean(
            RIBBON_KEEP_CLEAR_OF_NAMES_FIELD,
            DEFAULT_RIBBON_KEEP_CLEAR_OF_NAMES);
    }

    /**
     * @return how much of its ring a band gives up to a cluster name lying over it: FITTED_BOX the
     *         whole box the name's placement reserved, WORDS each drawn line's own measured extent
     *         (the default). Unread while the bands do not keep clear of the names at all
     */
    public static RibbonNameClearanceChoice getPoliticalMapRibbonNameClearance() {
        return KmuLunaSettings.readChoice(
            RIBBON_NAME_CLEARANCE_FIELD,
            DEFAULT_RIBBON_NAME_CLEARANCE);
    }

    /**
     * @return whether a cell with a band to draw draws one even where its ring leaves no room; on
     *         by default, giving up the covering name and then the inset rather than the band. Off,
     *         such a cell draws nothing, which makes a bare cell ambiguous between having nothing
     *         to say and having been refused the room to say it
     */
    public static boolean shouldAlwaysDrawPoliticalMapRibbons() {
        return KmuLunaSettings.readBoolean(
            RIBBON_ALWAYS_DRAWN_FIELD,
            DEFAULT_RIBBON_ALWAYS_DRAWN);
    }

    /**
     * @return how thick the presence band is drawn, in world units; 200.0 by default. The size
     *         every other size in the band is a multiple of, and a world quantity so a band holds
     *         the same share of its cell's outline at every zoom
     */
    public static double getPoliticalMapRibbonWidth() {
        return KmuLunaSettings.readDouble(RIBBON_WIDTH_FIELD, DEFAULT_RIBBON_WIDTH);
    }

    /**
     * @return how far clear of the cell's own border the band's near edge runs, in world units;
     *         200.0 by default. A cell with room for neither this gap nor the band's width draws
     *         no band
     */
    public static double getPoliticalMapRibbonInsetPad() {
        return KmuLunaSettings.readDouble(RIBBON_INSET_PAD_FIELD, DEFAULT_RIBBON_INSET_PAD);
    }

    /** @return how far the run one colony draws reaches, in band widths; 3 by default */
    public static int getPoliticalMapRibbonSegmentLength() {
        return KmuLunaSettings.readInt(
            RIBBON_SEGMENT_LENGTH_FIELD,
            DEFAULT_RIBBON_SEGMENT_LENGTH);
    }

    /**
     * @return how far a parting in the band reaches, in band widths; 1 by default. One length
     *         serves both the parting between two colonies of one bloc and the one closing that
     *         bloc's run, since two parting lengths in one band would read as a claim about the
     *         blocs they part. The setting screen calls it a separator, the counting rule an
     *         interjection
     */
    public static int getPoliticalMapRibbonInterjectionLength() {
        return KmuLunaSettings.readInt(
            RIBBON_INTERJECTION_LENGTH_FIELD,
            DEFAULT_RIBBON_INTERJECTION_LENGTH);
    }

    /**
     * @return the multiplier on each colony's base size rating - a visible market's own size, or a
     *         hidden market's chosen base size - before the station and patrol bonuses and
     *         stability fold in; 1.0 by default
     */
    public static double getColonySizeWeight() {
        return KmuLunaSettings.readDouble(COLONY_SIZE_WEIGHT_FIELD, DEFAULT_COLONY_SIZE_WEIGHT);
    }

    /**
     * @return how a hidden market's base size rating is chosen: NORMAL by its real colony size,
     *         FIXED at the hidden-market fixed weight regardless of size; FIXED by default
     */
    public static HiddenMarketScalingChoice getHiddenMarketScaling() {
        return KmuLunaSettings.readChoice(
            HIDDEN_MARKET_SCALING_FIELD,
            DEFAULT_HIDDEN_MARKET_SCALING);
    }

    /**
     * @return the fixed size rating a hidden market folds in at, before the colony size weight and
     *         stability apply; 1.0 by default. Unread while the scaling is NORMAL
     */
    public static double getHiddenMarketFixedWeight() {
        return KmuLunaSettings.readDouble(
            HIDDEN_MARKET_FIXED_WEIGHT_FIELD,
            DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT);
    }

    /**
     * @return the master switch for stability scaling, each dominance factor being cut at low
     *         stability by its own penalty; on by default. Off ranks colonies by their raw
     *         weighted size, station, and patrol sum
     */
    public static boolean shouldWeighDominanceByStability() {
        return KmuLunaSettings.readBoolean(
            STABILITY_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_STABILITY_WEIGHS_DOMINANCE);
    }

    /**
     * @return how much a colony's own size weight is cut at zero stability, 0..1 (half at 5, full
     *         at 10); 1.0 by default. Applied only while stability weighting is on
     */
    public static double getNormalLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            NORMAL_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_NORMAL_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a market with an attached defensive station gains the station weight in size
     *         points toward its system's dominance; on by default
     */
    public static boolean shouldWeighDominanceByStation() {
        return KmuLunaSettings.readBoolean(
            STATION_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_STATION_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points an attached defensive station adds to a visible colony's dominance
     *         contribution, before its low-stability penalty applies; 1.0 by default. A hidden
     *         market earns this scaled by the hidden-market station rate
     */
    public static double getStationWeight() {
        return KmuLunaSettings.readDouble(STATION_WEIGHT_FIELD, DEFAULT_STATION_WEIGHT);
    }

    /**
     * @return the fraction of the station weight a station on a hidden market earns, 0..1; 0.5 by
     *         default
     */
    public static double getStationHiddenMarketRate() {
        return KmuLunaSettings.readDouble(
            STATION_HIDDEN_MARKET_RATE_FIELD,
            DEFAULT_STATION_HIDDEN_MARKET_RATE);
    }

    /**
     * @return how much the station bonus is cut at zero stability, 0..1; 0.5 by default. Applied
     *         only while stability weighting is on
     */
    public static double getStationLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            STATION_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_STATION_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a colony gains size points toward its system's dominance for the patrols it
     *         fields, weighted by patrol size; on by default
     */
    public static boolean shouldWeighDominanceByPatrols() {
        return KmuLunaSettings.readBoolean(
            PATROL_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_PATROL_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points each small (light) patrol adds to its colony's dominance
     *         contribution, before the patrol low-stability penalty applies; 0.3 by default.
     *         Unread while patrol weighting is off
     */
    public static double getPatrolSmallWeight() {
        return KmuLunaSettings.readDouble(PATROL_SMALL_WEIGHT_FIELD, DEFAULT_PATROL_SMALL_WEIGHT);
    }

    /**
     * @return the size points each medium patrol adds to its colony's dominance contribution,
     *         before the patrol low-stability penalty applies; 0.6 by default. Unread while patrol
     *         weighting is off
     */
    public static double getPatrolMediumWeight() {
        return KmuLunaSettings.readDouble(PATROL_MEDIUM_WEIGHT_FIELD, DEFAULT_PATROL_MEDIUM_WEIGHT);
    }

    /**
     * @return the size points each large (heavy) patrol adds to its colony's dominance
     *         contribution, before the patrol low-stability penalty applies; 1.2 by default.
     *         Unread while patrol weighting is off
     */
    public static double getPatrolLargeWeight() {
        return KmuLunaSettings.readDouble(PATROL_LARGE_WEIGHT_FIELD, DEFAULT_PATROL_LARGE_WEIGHT);
    }

    /**
     * @return how much a colony's patrol bonus is cut at zero stability, 0..1; 0.5 by default.
     *         Applied only while stability weighting is on
     */
    public static double getPatrolLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            PATROL_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_PATROL_LOW_STABILITY_PENALTY);
    }

    /**
     * @return the sides of the polygon that rounds each system cell's reach into empty space; 48
     *         by default. Every segment is a vertex on each frontier cell, so this is the lever
     *         for trading map FPS against frontier smoothness
     */
    public static int getPoliticalMapCellBoundSegments() {
        return KmuLunaSettings.readInt(CELL_BOUND_SEGMENTS_FIELD, DEFAULT_CELL_BOUND_SEGMENTS);
    }

    /**
     * @return how far each system's territory reaches into empty space before its frontier bound
     *         closes it off, in world units; 4000.0 by default. Higher lets distant systems'
     *         territories meet and merge
     */
    public static double getPoliticalMapCellRadius() {
        return KmuLunaSettings.readDouble(CELL_RADIUS_FIELD, DEFAULT_CELL_RADIUS);
    }

    /**
     * @return the corner-rounding radius of the national border, in world units; 300.0 by default
     */
    public static double getPoliticalMapBorderCornerRadius() {
        return KmuLunaSettings.readDouble(
            BORDER_CORNER_RADIUS_FIELD,
            DEFAULT_BORDER_CORNER_RADIUS);
    }

    /** @return the arc segments per rounded corner of the national border; 3 by default */
    public static int getPoliticalMapBorderCornerSegments() {
        return KmuLunaSettings.readInt(
            BORDER_CORNER_SEGMENTS_FIELD,
            DEFAULT_BORDER_CORNER_SEGMENTS);
    }

    /**
     * @return the interior angle below which a national-border corner is chamfered flat rather
     *         than rounded, in radians; 35 degrees by default. Authored in degrees and converted
     *         here, the rounding math working in radians
     */
    public static double getPoliticalMapBorderChamferAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_CHAMFER_ANGLE_FIELD,
            DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES));
    }

    /**
     * @return the interior angle above which a national-border corner keeps its vertex rather than
     *         being rounded, in radians; 170 degrees by default. Authored in degrees and converted
     *         here. A border is arcs sampled at fixed angles joined by straight runs, so a setting
     *         past where those samples meet rounds them too - twice the vertices for a line in all
     *         but the same place
     */
    public static double getPoliticalMapBorderRoundBelowAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_ROUND_BELOW_ANGLE_FIELD,
            DEFAULT_BORDER_ROUND_BELOW_ANGLE_DEGREES));
    }

    /**
     * @return the depth up to which a sharp corner counts as a spike to sand off the border before
     *         rounding, in world units; 150.0 by default. A sharp corner jutting farther than this
     *         is real shape and is kept; zero disables the pass
     */
    public static double getPoliticalMapBorderSpikeHeight() {
        return KmuLunaSettings.readDouble(BORDER_SPIKE_HEIGHT_FIELD, DEFAULT_BORDER_SPIKE_HEIGHT);
    }

    /**
     * @return the interior angle below which a shallow corner counts as a spike to sand off the
     *         border before rounding, in radians; 60 degrees by default. Authored in degrees and
     *         converted here; zero disables the pass
     */
    public static double getPoliticalMapBorderSpikeAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_SPIKE_ANGLE_FIELD,
            DEFAULT_BORDER_SPIKE_ANGLE_DEGREES));
    }

    /**
     * @return whether the national-border corner rounding runs; on by default. Off leaves the
     *         radius, segments, and chamfer knobs unread, and also covers the factionless
     *         (decivilised and uninhabited) cell outlines, which reuse this same pass, so the whole
     *         map's corners round or not together
     */
    public static boolean shouldRoundBorderCorners() {
        return KmuLunaSettings.readBoolean(ROUND_CORNERS_FIELD, DEFAULT_ROUND_CORNERS);
    }

    /**
     * @return whether the spike-sanding pass runs before corner rounding; off by default. Off
     *         leaves the spike height and angle knobs unread and leaves needle or cusp protrusions
     *         in the border for the rounding to meet
     */
    public static boolean shouldSandBorderSpikes() {
        return KmuLunaSettings.readBoolean(SAND_SPIKES_FIELD, DEFAULT_SAND_SPIKES);
    }

    /**
     * @return the perpendicular gap between the diagonal hatch lines filling the contested cluster,
     *         in world units; 1000.0 by default
     */
    public static double getPoliticalMapHatchSpacing() {
        return KmuLunaSettings.readDouble(HATCH_SPACING_FIELD, DEFAULT_HATCH_SPACING);
    }

    /**
     * @return the direction the contested-cluster hatch lines run, in radians; 45 degrees off
     *         horizontal by default. Authored in degrees and converted here, the hatch math working
     *         in radians
     */
    public static double getPoliticalMapHatchAngleRadians() {
        return Math.toRadians(
            KmuLunaSettings.readDouble(HATCH_ANGLE_FIELD, DEFAULT_HATCH_ANGLE_DEGREES));
    }

    /**
     * @return the line width the contested-cluster hatch strokes at, in pixels; 50.0 by default
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
     *         segment, as a fraction of the hatch spacing; 0.002 by default. Authored as a
     *         percentage of the spacing and converted here
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
     * @return whether the political map shows its own hover box; on by default. The bottom tier,
     *         so it can only withhold the box the two global tiers already allow, and it leaves
     *         another layer's box alone
     */
    public static boolean getPoliticalMapHoverTooltipEnabled() {
        return KmuLunaSettings.readBoolean(
            LAYER_HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_LAYER_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return which palette colour of the cell under the cursor the hover halo and cell wash both
     *         draw in; the secondary (dark) colour by default
     */
    public static FactionPaletteChoice getPoliticalMapHoverHighlightColour() {
        return KmuLunaSettings.readChoice(
            HOVER_HIGHLIGHT_COLOR_FIELD,
            DEFAULT_HOVER_HIGHLIGHT_COLOUR);
    }

    /**
     * @return the alpha of the hover halo's innermost stroke, 0..1; 0.5 by default. Its brightest
     *         layer, which the outer layers fade away from
     */
    public static double getPoliticalMapHoverGlowOpacity() {
        return KmuLunaSettings.readDouble(HOVER_GLOW_OPACITY_FIELD, DEFAULT_HOVER_GLOW_OPACITY);
    }

    /**
     * @return how far the hover halo reaches off the hovered frontier, in pixels; 14.0 by default.
     *         The width of its widest, faintest stroke
     */
    public static double getPoliticalMapHoverGlowWidth() {
        return KmuLunaSettings.readDouble(HOVER_GLOW_WIDTH_FIELD, DEFAULT_HOVER_GLOW_WIDTH);
    }

    /**
     * @return how many strokes the hover halo accumulates from; 4 by default. More buys a smoother
     *         falloff at a stroke of the whole frontier apiece
     */
    public static int getPoliticalMapHoverGlowLayers() {
        return KmuLunaSettings.readInt(HOVER_GLOW_LAYERS_FIELD, DEFAULT_HOVER_GLOW_LAYERS);
    }

    /**
     * @return how much of its alpha the hover halo gives up at the bottom of a breath, 0..1; 0 by
     *         default, holding it steady
     */
    public static double getPoliticalMapHoverGlowPulseStrength() {
        return KmuLunaSettings.readDouble(
            HOVER_GLOW_PULSE_STRENGTH_FIELD,
            DEFAULT_HOVER_GLOW_PULSE_STRENGTH);
    }

    /**
     * @return how long one breath of the hover halo's pulse takes, in seconds; 0.2 by default.
     *         Ignored while the pulse strength is 0
     */
    public static double getPoliticalMapHoverGlowPulsePeriod() {
        return KmuLunaSettings.readDouble(
            HOVER_GLOW_PULSE_PERIOD_FIELD,
            DEFAULT_HOVER_GLOW_PULSE_PERIOD_SECONDS);
    }

    /**
     * @return the alpha the hovered cell's wash brightens its painted extent by, 0..1; 0.35 by
     *         default
     */
    public static double getPoliticalMapHoverWashOpacity() {
        return KmuLunaSettings.readDouble(HOVER_WASH_OPACITY_FIELD, DEFAULT_HOVER_WASH_OPACITY);
    }

    /**
     * @return the alpha the hovered cell's own outline traces at, 0..1; 0.8 by default. The only
     *         cue a cell surrounded by its own faction has, so it reads apart from the wash
     */
    public static double getPoliticalMapHoverWashOutlineOpacity() {
        return KmuLunaSettings.readDouble(
            HOVER_WASH_OUTLINE_OPACITY_FIELD,
            DEFAULT_HOVER_WASH_OUTLINE_OPACITY);
    }

    /**
     * @return the line width the hovered cell's outline traces at, in pixels; 2.0 by default
     */
    public static double getPoliticalMapHoverWashOutlineWidth() {
        return KmuLunaSettings.readDouble(
            HOVER_WASH_OUTLINE_WIDTH_FIELD,
            DEFAULT_HOVER_WASH_OUTLINE_WIDTH);
    }

    /**
     * @return which palette colour of the previewed bloc the preview halo and cell wash both draw
     *         in; the primary (bright) colour by default. Keyed on the bloc rather than on each
     *         lit cell, so one shade covers the whole set
     */
    public static FactionPaletteChoice getPoliticalMapPreviewHighlightColour() {
        return KmuLunaSettings.readChoice(
            PREVIEW_HIGHLIGHT_COLOUR_FIELD,
            DEFAULT_PREVIEW_HIGHLIGHT_COLOUR);
    }

    /**
     * @return the alpha of the preview halo's innermost stroke, 0..1; 0.6 by default. Its brightest
     *         layer, which the outer layers fade away from
     */
    public static double getPoliticalMapPreviewGlowOpacity() {
        return KmuLunaSettings.readDouble(PREVIEW_GLOW_OPACITY_FIELD, DEFAULT_PREVIEW_GLOW_OPACITY);
    }

    /**
     * @return how far the preview halo reaches off a lit region's outline, in pixels; 6.0 by
     *         default. The width of its widest, faintest stroke
     */
    public static double getPoliticalMapPreviewGlowWidth() {
        return KmuLunaSettings.readDouble(PREVIEW_GLOW_WIDTH_FIELD, DEFAULT_PREVIEW_GLOW_WIDTH);
    }

    /**
     * @return how many strokes the preview halo accumulates from; 3 by default. More buys a
     *         smoother falloff at a stroke of every lit outline apiece
     */
    public static int getPoliticalMapPreviewGlowLayers() {
        return KmuLunaSettings.readInt(PREVIEW_GLOW_LAYERS_FIELD, DEFAULT_PREVIEW_GLOW_LAYERS);
    }

    /**
     * @return how much of its alpha the preview halo gives up at the bottom of a breath, 0..1; 0.5
     *         by default
     */
    public static double getPoliticalMapPreviewGlowPulseStrength() {
        return KmuLunaSettings.readDouble(
            PREVIEW_GLOW_PULSE_STRENGTH_FIELD,
            DEFAULT_PREVIEW_GLOW_PULSE_STRENGTH);
    }

    /**
     * @return how long one breath of the preview halo's pulse takes, in seconds; 1.5 by default.
     *         Ignored while the pulse strength is 0
     */
    public static double getPoliticalMapPreviewGlowPulsePeriod() {
        return KmuLunaSettings.readDouble(
            PREVIEW_GLOW_PULSE_PERIOD_FIELD,
            DEFAULT_PREVIEW_GLOW_PULSE_PERIOD_SECONDS);
    }

    /**
     * @return the alpha a lit cell's wash brightens its painted extent by, 0..1; 0.5 by default
     */
    public static double getPoliticalMapPreviewWashOpacity() {
        return KmuLunaSettings.readDouble(PREVIEW_WASH_OPACITY_FIELD, DEFAULT_PREVIEW_WASH_OPACITY);
    }

    /**
     * @return the alpha a lit region's outline traces at, 0..1; 0.9 by default. The outline is the
     *         joined edge of the lit cells rather than any cluster frontier, so cells lit inside
     *         one cluster show no seam between them
     */
    public static double getPoliticalMapPreviewWashOutlineOpacity() {
        return KmuLunaSettings.readDouble(
            PREVIEW_WASH_OUTLINE_OPACITY_FIELD,
            DEFAULT_PREVIEW_WASH_OUTLINE_OPACITY);
    }

    /**
     * @return the line width a lit region's outline traces at, in pixels; 2.0 by default
     */
    public static double getPoliticalMapPreviewWashOutlineWidth() {
        return KmuLunaSettings.readDouble(
            PREVIEW_WASH_OUTLINE_WIDTH_FIELD,
            DEFAULT_PREVIEW_WASH_OUTLINE_WIDTH);
    }

    /**
     * @return which side of the map's nebulae the territory fills paint on: BELOW dimmed by them
     *         (the default), ABOVE clear of them. The contested hatch is part of a cluster's fill
     *         and paints with it, and the hover highlight brightens a fill so it follows this too.
     *         Lifting the fills lifts the borders with them, since a fill painted over its own
     *         borders leaves a blank cell - a constraint resolved where the choices become a paint
     *         order, so this answers only what the player picked
     */
    public static NebulaDrawOrderChoice getPoliticalMapFillNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_FILLS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_FILLS);
    }

    /**
     * @return which side of the map's nebulae the cluster boundaries, province seams, and
     *         factionless outlines paint on: BELOW dimmed by them (the default), ABOVE clear of
     *         them. Borders may be lifted alone but never sink below the fills, so a BELOW here is
     *         honoured only while the fills are BELOW as well
     */
    public static NebulaDrawOrderChoice getPoliticalMapBorderNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_BORDERS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_BORDERS);
    }

    /**
     * @return which side of the map's nebulae the presence bands paint on: BELOW dimmed by them
     *         (the default), ABOVE clear of them
     */
    public static NebulaDrawOrderChoice getPoliticalMapRibbonNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_RIBBONS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_RIBBONS);
    }

    /**
     * @return which side of the map's nebulae the cluster names paint on: BELOW dimmed by them,
     *         ABOVE clear of them (the default, a haze over words costing legibility rather than
     *         strength of colour)
     */
    public static NebulaDrawOrderChoice getPoliticalMapLabelNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_LABELS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_LABELS);
    }

    /**
     * @return whether the political map draws the per-cluster label anchors - a dot at each
     *         contiguous cluster's centre and, in green, the accepted label line its fit produced;
     *         off by default. Master switch for the anchor overlay: the framework's rejected- and
     *         unbiased-axis toggles only add lines while this is on
     */
    public static boolean getPoliticalMapShowClusterAnchors() {
        return KmuLunaSettings.readBoolean(
            SHOW_CLUSTER_ANCHORS_FIELD,
            DEFAULT_SHOW_CLUSTER_ANCHORS);
    }

    /**
     * @return whether the political map draws the ring each cell's presence band would run along,
     *         coloured by what that cell's room lets a band do with it - green where the authored
     *         inset held, yellow where the pad had to be given up, red where no band is laid at
     *         all; off by default. Shows nothing while the bands themselves are switched off,
     *         there being no layout to report on
     */
    public static boolean shouldShowPoliticalMapRibbonPaths() {
        return KmuLunaSettings.readBoolean(
            SHOW_RIBBON_PATHS_FIELD,
            DEFAULT_SHOW_RIBBON_PATHS);
    }

    /**
     * @return whether to replace the normal political-map render with the border-tracing diagnostic
     *         that layers the smoothing pipeline's stages (base, despiked, rounded) in distinct
     *         colours; off by default. Respects the two smoothing gates, so a stage draws only when
     *         its pass ran
     */
    public static boolean shouldTraceBordersForDebug() {
        return KmuLunaSettings.readBoolean(
            DEBUG_BORDER_TRACING_FIELD,
            DEFAULT_DEBUG_BORDER_TRACING);
    }
}
