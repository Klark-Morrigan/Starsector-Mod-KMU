package kmu.settings;

/**
 * The geometry behind what the map draws over its cells: the border tracing every layer's fills
 * are outlined by, the per-cluster anchor search a name is placed on, how that name is fitted, and
 * the two diagnostic overlays for the search itself.
 *
 * <p>One home because they are one pipeline read in one pass - a border is traced, an anchor is
 * scored inside it, a name is fitted to the anchor - and a caller drawing any part of it reads
 * across the whole set. The diagnostics sit here for the same reason: what they draw is the
 * search's own rejected and unbiased candidates, which nothing else can describe. *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapLabelSettings {

    // Always applied - upstream of the smoothing passes a layer may gate - so the tracing has no
    // switch of its own.
    private static final String BORDER_WELD_TOLERANCE_FIELD =
        "kmu_map_dev_borderTracing_weldTolerance";

    private static final String BORDER_MITER_LIMIT_FIELD =
        "kmu_map_dev_borderTracing_miterLimit";

    // The per-cluster label-anchor search: the straight line a cluster's name sits on, chosen by
    // scoring candidate lines swept across the cluster (a fan of directions times a family of
    // parallel offsets), each fit inside the national border and clear of the system icons. Live
    // knobs rather than constants so the search can be tuned on the open map; all feed the
    // drawables rebuild, which re-runs the search for every anchor.
    private static final String ANCHOR_DIRECTION_COUNT_FIELD =
        "kmu_map_dev_labelAnchors_directionCount";

    private static final String ANCHOR_OFFSET_COUNT_FIELD =
        "kmu_map_dev_labelAnchors_offsetCount";

    private static final String ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD =
        "kmu_map_dev_labelAnchors_verticalPenalty_strength";

    private static final String ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD =
        "kmu_map_dev_labelAnchors_verticalPenalty_exponent";

    private static final String ANCHOR_MAX_SLANT_DEGREES_FIELD =
        "kmu_map_dev_labelAnchors_maxSlantDegrees";

    private static final String ANCHOR_END_INSET_MULTIPLE_FIELD =
        "kmu_map_dev_labelAnchors_endInsetMultiple";

    private static final String ANCHOR_ICON_CLEARANCE_FIELD =
        "kmu_map_dev_labelAnchors_iconClearance";

    private static final String ANCHOR_FONT_HEIGHT_TOLERANCE_FIELD =
        "kmu_map_dev_labelAnchors_fontHeightTolerance";

    private static final String ANCHOR_BAND_OPACITY_FIELD =
        "kmu_map_dev_labelAnchors_bandOpacity";

    private static final String ANCHOR_BAND_LINE_OPACITY_FIELD =
        "kmu_map_dev_labelAnchors_bandLineOpacity";

    private static final String NAME_MIN_FONT_SIZE_FIELD =
        "kmu_map_visuals_labels_fontSize_min";

    private static final String NAME_MAX_FONT_SIZE_FIELD =
        "kmu_map_visuals_labels_fontSize_max";

    private static final String NAME_MAX_LINES_FIELD =
        "kmu_map_visuals_labels_maxLines";

    private static final String NAME_LINE_SPACING_FIELD =
        "kmu_map_visuals_labels_lineSpacing";

    private static final String SHOW_REJECTED_AXES_FIELD =
        "kmu_map_dev_diagnostics_labels_boxes_areRejectedShown";

    private static final String SHOW_UNBIASED_AXES_FIELD =
        "kmu_map_dev_diagnostics_labels_boxes_areUnbiasedShown";

    private static final double DEFAULT_BORDER_WELD_TOLERANCE = 100.0;

    private static final double DEFAULT_BORDER_MITER_LIMIT = 4.0;

    // The fan is the dominant cost in a map rebuild - every extra spoke re-sweeps the whole
    // offset family for every cluster - so the default is kept coarse. The axis and the slant
    // are searched regardless, which is what a coarse fan can afford to lean on.
    private static final int DEFAULT_ANCHOR_DIRECTION_COUNT = 4;

    private static final int DEFAULT_ANCHOR_OFFSET_COUNT = 10;

    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH = 0.3;

    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT = 2.0;

    private static final double DEFAULT_ANCHOR_MAX_SLANT_DEGREES = 45.0;

    private static final double DEFAULT_ANCHOR_END_INSET_MULTIPLE = 4.0;

    private static final double DEFAULT_ANCHOR_ICON_CLEARANCE = 100.0;

    // One world unit, on a font clamped between 200 and 1200 of them: already far below what a map
    // pixel resolves at any zoom, so the halvings this stops the font search short of would have
    // bought a height difference no one can see.
    private static final double DEFAULT_ANCHOR_FONT_HEIGHT_TOLERANCE = 1.0;

    // In world units where a distance: the font-size clamp brackets a readable line against the
    // map's scale (the border inset channel is 150), three lines is the HOI4-style ceiling, and
    // 1.15 leads the lines with a little air.
    private static final double DEFAULT_NAME_MIN_FONT_SIZE = 200.0;

    private static final double DEFAULT_NAME_MAX_FONT_SIZE = 4000.0;

    private static final int DEFAULT_NAME_MAX_LINES = 3;

    private static final double DEFAULT_NAME_LINE_SPACING = 1.15;

    private static final double DEFAULT_ANCHOR_BAND_OPACITY = 0.35;

    private static final double DEFAULT_ANCHOR_BAND_LINE_OPACITY = 0.9;

    private static final boolean DEFAULT_SHOW_REJECTED_AXES = false;

    private static final boolean DEFAULT_SHOW_UNBIASED_AXES = false;

    private KmuMapLabelSettings() {
    }

    /**
     * @return how far apart two outline points may be and still weld into one corner when chaining
     *         the national border, in world units; 100.0 by default. Raised if borders go missing,
     *         lowered if distinct corners merge
     */
    public static double getMapBorderWeldTolerance() {
        return KmuLunaSettings.readDouble(
            BORDER_WELD_TOLERANCE_FIELD,
            DEFAULT_BORDER_WELD_TOLERANCE);
    }

    /**
     * @return the multiple of the border inset past which a sharp corner's miter is bevelled
     *         instead of pointed; 4.0 by default. Lower bevels sooner (rounder corners, no inward
     *         spikes), higher keeps crisper points
     */
    public static double getMapBorderMiterLimit() {
        return KmuLunaSettings.readDouble(BORDER_MITER_LIMIT_FIELD, DEFAULT_BORDER_MITER_LIMIT);
    }

    /**
     * @return how many directions the label-anchor search fans over the half-circle (0..180
     *         degrees, a label line being undirected); 4 by default. Pure horizontal, the cluster's
     *         own principal axis, and the preferred slant are always searched on top of the fan
     */
    public static int getMapAnchorDirectionCount() {
        return KmuLunaSettings.readInt(
            ANCHOR_DIRECTION_COUNT_FIELD,
            DEFAULT_ANCHOR_DIRECTION_COUNT);
    }

    /**
     * @return how many parallel lines the search sweeps across the cluster per direction, spaced
     *         evenly over the cluster's extent perpendicular to that direction; 10 by default. This
     *         is what lets the accepted line slide off the centroid into a roomier part of the
     *         cluster, and 1 degenerates to a single centred line per direction
     */
    public static int getMapAnchorOffsetCount() {
        return KmuLunaSettings.readInt(
            ANCHOR_OFFSET_COUNT_FIELD,
            DEFAULT_ANCHOR_OFFSET_COUNT);
    }

    /**
     * @return how close the anchor search must land to the largest font height each candidate line
     *         holds, in world units; 1.0 by default. The fit grows the font by halving the size
     *         clamp and re-measures the label's band at every halving, so a coarser tolerance buys
     *         those measurements back one for one. Returned as stored, so a reader that cannot take
     *         a nonsensical value holds it to a floor of its own
     */
    public static double getMapAnchorFontHeightTolerance() {
        return KmuLunaSettings.readDouble(
            ANCHOR_FONT_HEIGHT_TOLERANCE_FIELD,
            DEFAULT_ANCHOR_FONT_HEIGHT_TOLERANCE);
    }

    /**
     * @return how much length a shallower (more horizontal) candidate line may give up and still
     *         win the search, 0..1; 0.3 by default. A candidate's clear length is scaled by
     *         {@code 1 - strength * sin(angle)^exponent}, so 0 picks the pure longest line
     *         regardless of slope and 1 scores a vertical line zero
     */
    public static double getMapAnchorVerticalPenaltyStrength() {
        return KmuLunaSettings.readDouble(
            ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH);
    }

    /**
     * @return how sharply the vertical penalty concentrates toward vertical - the exponent on
     *         {@code sin(angle)} in the score, at least 1; 2.0 by default. A higher exponent leaves
     *         already-shallow lines almost unpenalised and bites only as a line approaches vertical
     */
    public static double getMapAnchorVerticalPenaltyExponent() {
        return KmuLunaSettings.readDouble(
            ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT);
    }

    /**
     * @return how far a label may lean off level to follow its cluster's long axis, in degrees; 45
     *         by default. The search prefers this cluster-specific slant over screen-horizontal,
     *         capped here so a tall cluster never stands its name vertical and faded toward level
     *         as a cluster gets rounder; 0 forces dead-horizontal labels
     */
    public static double getMapAnchorMaxSlantDegrees() {
        return KmuLunaSettings.readDouble(
            ANCHOR_MAX_SLANT_DEGREES_FIELD,
            DEFAULT_ANCHOR_MAX_SLANT_DEGREES);
    }

    /**
     * @return how far short of the national border each end of a label anchor stops, in multiples
     *         of the border inset channel; 4.0 by default. A clear line shorter than twice this
     *         collapses to the dot
     */
    public static double getMapAnchorEndInsetMultiple() {
        return KmuLunaSettings.readDouble(
            ANCHOR_END_INSET_MULTIPLE_FIELD,
            DEFAULT_ANCHOR_END_INSET_MULTIPLE);
    }

    /**
     * @return the keep-out radius around each system icon that a label anchor must not cross, in
     *         world units; 100.0 by default. A tuned approximation of the icon's on-map footprint,
     *         icons drawing at a fixed pixel size while the anchor is fitted once in world space
     */
    public static double getMapAnchorIconClearance() {
        return KmuLunaSettings.readDouble(
            ANCHOR_ICON_CLEARANCE_FIELD,
            DEFAULT_ANCHOR_ICON_CLEARANCE);
    }

    /**
     * @return the smallest per-line font height a cluster's name may render at, in world units;
     *         200.0 by default. A placement that cannot hold even one line this tall anywhere
     *         collapses to the dot and shows no name
     */
    public static double getMapNameMinFontSize() {
        return KmuLunaSettings.readDouble(NAME_MIN_FONT_SIZE_FIELD, DEFAULT_NAME_MIN_FONT_SIZE);
    }

    /**
     * @return the largest per-line font height the name fit will grow to, in world units; 4000.0 by
     *         default, so a roomy cluster does not mint an oversized label
     */
    public static double getMapNameMaxFontSize() {
        return KmuLunaSettings.readDouble(NAME_MAX_FONT_SIZE_FIELD, DEFAULT_NAME_MAX_FONT_SIZE);
    }

    /**
     * @return the most lines the fit may wrap a name into; 3 by default. A length-poor but
     *         girth-rich cluster wraps the name to shorten its widest line, chosen only when that
     *         renders a strictly larger font than fewer lines would; 1 forces single-line names
     */
    public static int getMapNameMaxLines() {
        return KmuLunaSettings.readInt(NAME_MAX_LINES_FIELD, DEFAULT_NAME_MAX_LINES);
    }

    /**
     * @return the line-height multiple between a multi-line name's stacked lines; 1.15 by default,
     *         and at least 1 (lines flush)
     */
    public static double getMapNameLineSpacing() {
        return KmuLunaSettings.readDouble(NAME_LINE_SPACING_FIELD, DEFAULT_NAME_LINE_SPACING);
    }

    /**
     * @return the fill opacity of the debug band quad, 0..1; 0.35 by default - low enough that the
     *         national border reads through the band, an overflow being the whole point of drawing
     *         it
     */
    public static double getMapAnchorBandOpacity() {
        return KmuLunaSettings.readDouble(ANCHOR_BAND_OPACITY_FIELD, DEFAULT_ANCHOR_BAND_OPACITY);
    }

    /**
     * @return the opacity of the debug band box's strokes - its outline, the line-count divider
     *         rules, the centreline, and the anchor dot - 0..1; 0.9 by default, kept apart from the
     *         fill alpha so the outline stays legible over a faint band wash
     */
    public static double getMapAnchorBandLineOpacity() {
        return KmuLunaSettings.readDouble(
            ANCHOR_BAND_LINE_OPACITY_FIELD,
            DEFAULT_ANCHOR_BAND_LINE_OPACITY);
    }

    /**
     * @return whether a cluster whose accepted label line collapsed to the dot also draws, in red,
     *         the best candidate its fit found before the border, icon, or end-margin trim
     *         discarded it; off by default, meaningful only while the cluster anchors themselves
     *         draw
     */
    public static boolean shouldShowRejectedAxes() {
        return KmuLunaSettings.readBoolean(SHOW_REJECTED_AXES_FIELD, DEFAULT_SHOW_REJECTED_AXES);
    }

    /**
     * @return whether each cluster also draws, in yellow, the label line its fit would accept with
     *         no horizontal bias applied; off by default, meaningful only while the cluster anchors
     *         themselves draw. Drawn only where the unbiased direction actually differs from the
     *         accepted line's
     */
    public static boolean shouldShowUnbiasedAxes() {
        return KmuLunaSettings.readBoolean(SHOW_UNBIASED_AXES_FIELD, DEFAULT_SHOW_UNBIASED_AXES);
    }
}
