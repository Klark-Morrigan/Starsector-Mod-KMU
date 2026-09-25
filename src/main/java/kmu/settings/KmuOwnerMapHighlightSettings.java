package kmu.settings;

/**
 * What an owner map lights up in answer to a hover, in two tiers: the cursor's halo and wash
 * on the one cell under the pointer, and the preview's wash on a whole set of cells at once. The
 * two hover switches sit with them: one pair shared by every owner-painted layer and read through
 * {@code SharedOwnerMapHoverGates}, the bottom tier of the hover switching that starts on the
 * map-wide tab.
 *
 * <p>The two tiers are read as a pair even though only one paints at a time, since what each is
 * worth is judged against the other: they light the same map in the same palette, and a weight
 * set without the other in view is set against nothing.
 *
 * <p>The preview offers far fewer knobs than the cursor, and deliberately: a halo centred on the
 * edge of a single system's cell spills inward across the whole cell rather than rimming it, and
 * the halos of lit cells sitting close pile into one bright patch, so the preview carries no halo
 * at all and its trace is fixed thin enough never to read as a second border. Only what a player
 * can usefully weigh - which of the bloc's two shades to burn, and how hard the wash burns - is
 * left as a setting.
 */
public final class KmuOwnerMapHighlightSettings {

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
    private static final String PREVIEW_WASH_OPACITY_FIELD =
        "kmu_map_politics_visuals_previewHighlight_wash_opacity";

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

    // The shade the cursor's own highlight burns, so the two tiers light the same map alike.
    private static final FactionPaletteChoice DEFAULT_PREVIEW_HIGHLIGHT_COLOUR =
        FactionPaletteChoice.SECONDARY;
    private static final double DEFAULT_PREVIEW_WASH_OPACITY = 0.6;

    private KmuOwnerMapHighlightSettings() {
    }

    /**
     * @return whether the owner-painted layers paint their hover halo and cell wash; on by default.
     *         The bottom tier, so it can only withhold effects the two global tiers already allow
     */
    public static boolean getOwnerMapHoverEffectsEnabled() {
        return KmuLunaSettings.readBoolean(
            LAYER_HOVER_EFFECTS_ENABLED_FIELD,
            DEFAULT_LAYER_HOVER_EFFECTS_ENABLED);
    }

    /**
     * @return whether the owner-painted layers show their hover box; on by default. The bottom
     *         tier, so it can only withhold the box the two global tiers already allow, and it
     *         leaves a layer that paints by no owner alone
     */
    public static boolean getOwnerMapHoverTooltipEnabled() {
        return KmuLunaSettings.readBoolean(
            LAYER_HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_LAYER_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return which palette colour of the cell under the cursor the hover halo and cell wash both
     *         draw in; the secondary (dark) colour by default
     */
    public static FactionPaletteChoice getOwnerMapHoverHighlightColour() {
        return KmuLunaSettings.readChoice(
            HOVER_HIGHLIGHT_COLOR_FIELD,
            DEFAULT_HOVER_HIGHLIGHT_COLOUR);
    }

    /**
     * @return the alpha of the hover halo's innermost stroke, 0..1; 0.5 by default. Its brightest
     *         layer, which the outer layers fade away from
     */
    public static double getOwnerMapHoverGlowOpacity() {
        return KmuLunaSettings.readDouble(HOVER_GLOW_OPACITY_FIELD, DEFAULT_HOVER_GLOW_OPACITY);
    }

    /**
     * @return how far the hover halo reaches off the hovered frontier, in pixels; 14.0 by default.
     *         The width of its widest, faintest stroke
     */
    public static double getOwnerMapHoverGlowWidth() {
        return KmuLunaSettings.readDouble(HOVER_GLOW_WIDTH_FIELD, DEFAULT_HOVER_GLOW_WIDTH);
    }

    /**
     * @return how many strokes the hover halo accumulates from; 4 by default. More buys a smoother
     *         falloff at a stroke of the whole frontier apiece
     */
    public static int getOwnerMapHoverGlowLayers() {
        return KmuLunaSettings.readInt(HOVER_GLOW_LAYERS_FIELD, DEFAULT_HOVER_GLOW_LAYERS);
    }

    /**
     * @return how much of its alpha the hover halo gives up at the bottom of a breath, 0..1; 0 by
     *         default, holding it steady
     */
    public static double getOwnerMapHoverGlowPulseStrength() {
        return KmuLunaSettings.readDouble(
            HOVER_GLOW_PULSE_STRENGTH_FIELD,
            DEFAULT_HOVER_GLOW_PULSE_STRENGTH);
    }

    /**
     * @return how long one breath of the hover halo's pulse takes, in seconds; 0.2 by default.
     *         Ignored while the pulse strength is 0
     */
    public static double getOwnerMapHoverGlowPulsePeriod() {
        return KmuLunaSettings.readDouble(
            HOVER_GLOW_PULSE_PERIOD_FIELD,
            DEFAULT_HOVER_GLOW_PULSE_PERIOD_SECONDS);
    }

    /**
     * @return the alpha the hovered cell's wash brightens its painted extent by, 0..1; 0.35 by
     *         default
     */
    public static double getOwnerMapHoverWashOpacity() {
        return KmuLunaSettings.readDouble(HOVER_WASH_OPACITY_FIELD, DEFAULT_HOVER_WASH_OPACITY);
    }

    /**
     * @return the alpha the hovered cell's own outline traces at, 0..1; 0.8 by default. The only
     *         cue a cell surrounded by its own faction has, so it reads apart from the wash
     */
    public static double getOwnerMapHoverWashOutlineOpacity() {
        return KmuLunaSettings.readDouble(
            HOVER_WASH_OUTLINE_OPACITY_FIELD,
            DEFAULT_HOVER_WASH_OUTLINE_OPACITY);
    }

    /**
     * @return the line width the hovered cell's outline traces at, in pixels; 2.0 by default
     */
    public static double getOwnerMapHoverWashOutlineWidth() {
        return KmuLunaSettings.readDouble(
            HOVER_WASH_OUTLINE_WIDTH_FIELD,
            DEFAULT_HOVER_WASH_OUTLINE_WIDTH);
    }

    /**
     * @return which palette colour of the previewed bloc the preview's cell wash and its trace both
     *         draw in; the secondary (dark) colour by default. Keyed on the bloc rather than on
     *         each lit cell, so one shade covers the whole set
     */
    public static FactionPaletteChoice getOwnerMapPreviewHighlightColour() {
        return KmuLunaSettings.readChoice(
            PREVIEW_HIGHLIGHT_COLOUR_FIELD,
            DEFAULT_PREVIEW_HIGHLIGHT_COLOUR);
    }

    /**
     * @return the alpha a lit cell's wash brightens its painted extent by, 0..1; 0.6 by default
     */
    public static double getOwnerMapPreviewWashOpacity() {
        return KmuLunaSettings.readDouble(PREVIEW_WASH_OPACITY_FIELD, DEFAULT_PREVIEW_WASH_OPACITY);
    }
}
