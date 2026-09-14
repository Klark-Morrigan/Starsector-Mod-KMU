package kmu.settings;

/**
 * How dense the hover box is drawn, and how its two solid marks sit against the text.
 *
 * <p>Stated by depth rather than by what a layer lists at that depth, the box being shared: one
 * layer's box lands the player on a colony's terms, another's box of the same shape is set by the
 * same knobs. *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapTooltipSettings {

    // Stated by depth rather than by what a layer lists at that depth, the hover box being shared:
    // one layer's box lands them on a colony's terms, another's box of the same shape is set by the
    // same knobs.
    private static final String TOOLTIP_NESTING_LEVEL_SHRINK_FIELD =
        "kmu_map_visuals_tooltips_density_nestingLevelShrink";

    private static final String TOOLTIP_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap_tier1";

    private static final String TOOLTIP_TIER_2_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap_tier2";

    private static final String TOOLTIP_TIER_3_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap_tier3";

    // The two solid marks a hover box draws among its glyphs. Both are knobs rather than constants
    // for the same reason: how heavy a solid run looks beside a line of letters turns on the face,
    // the size it draws at, and how that atlas was rasterised, so where it sits against the text is
    // a judgement made on screen.
    private static final String TOOLTIP_LEADER_THICKNESS_FIELD =
        "kmu_map_visuals_tooltips_leader_thickness";

    private static final String TOOLTIP_LEADER_OPACITY_FIELD =
        "kmu_map_visuals_tooltips_leader_opacity";

    private static final String TOOLTIP_REDACTION_DARKENING_STRENGTH_FIELD =
        "kmu_map_visuals_tooltips_redacted_darkeningStrength";

    // The gaps tighten only the depths a listing runs long at: lines two steps in stand closer to
    // each other than the things they belong to do, and lines three steps in closer still, so each
    // run reads as one thing without the box's own spacing changing.
    private static final float DEFAULT_TOOLTIP_NESTING_LEVEL_SHRINK = 0.75f;

    private static final float DEFAULT_TOOLTIP_LINE_GAP = 4f;

    private static final float DEFAULT_TOOLTIP_TIER_2_LINE_GAP = 0.5f;

    private static final float DEFAULT_TOOLTIP_TIER_3_LINE_GAP = 0f;

    // Both marks are let down until they sit level with the text either side of them rather than
    // above it: a whole pixel of leader at roughly two thirds of the box's opacity, and two fifths
    // of a line's colour taken off the blocks a withheld name draws as.
    private static final float DEFAULT_TOOLTIP_LEADER_THICKNESS = 1f;

    private static final float DEFAULT_TOOLTIP_LEADER_OPACITY = 0.65f;

    private static final float DEFAULT_TOOLTIP_REDACTION_DARKENING_STRENGTH = 0.4f;

    private KmuMapTooltipSettings() {
    }

    /**
     * @return how much smaller each step of indent inside a hover tooltip draws than the step above
     *         it, as a multiple of its own size; 0.75 by default
     */
    public static float getMapTooltipNestingLevelShrink() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_NESTING_LEVEL_SHRINK_FIELD,
            DEFAULT_TOOLTIP_NESTING_LEVEL_SHRINK);
    }

    /**
     * @return the room left under a line of a hover tooltip before the next one, in UI units,
     *         wherever neither depth below applies; 4 by default
     */
    public static float getMapTooltipLineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_LINE_GAP);
    }

    /**
     * @return the room left under a line two steps in from a hover box's own heading, in UI units -
     *         a run long enough to be worth tightening on its own; 0.5 by default
     */
    public static float getMapTooltipTier2LineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_TIER_2_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_TIER_2_LINE_GAP);
    }

    /**
     * @return the room left under a line three steps in, in UI units - the deepest run a hover box
     *         lists and so the one tightened hardest; 0 by default
     */
    public static float getMapTooltipTier3LineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_TIER_3_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_TIER_3_LINE_GAP);
    }

    /**
     * @return how thick the line a hover box runs from a label across to its value draws, in UI
     *         units; 1 by default, and 0 to draw no such lines at all
     */
    public static float getMapTooltipLeaderThickness() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LEADER_THICKNESS_FIELD,
            DEFAULT_TOOLTIP_LEADER_THICKNESS);
    }

    /**
     * @return how strongly that line draws, as a fraction of the box's own opacity; 0.65 by
     *         default, and 0 to draw no such lines at all
     */
    public static float getMapTooltipLeaderOpacity() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LEADER_OPACITY_FIELD,
            DEFAULT_TOOLTIP_LEADER_OPACITY);
    }

    /**
     * @return how much of a line's colour is taken off the blocks a withheld name draws as, 0..1;
     *         0.4 by default, and 0 to fill them in the line's own colour
     */
    public static float getMapTooltipRedactionDarkeningStrength() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_REDACTION_DARKENING_STRENGTH_FIELD,
            DEFAULT_TOOLTIP_REDACTION_DARKENING_STRENGTH);
    }
}
