package kmu.maplayers.ownermap.render.style;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.HatchPattern;

import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.GlLineHatchStroke;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.HatchStyle;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.base.theme.HoverWashStyle;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.settings.KmuOwnerMapGeometrySettings;
import kmu.settings.KmuOwnerMapHighlightSettings;
import kmu.settings.KmuOwnerMapStyleSettings;

/**
 * Reads the whole owner-map theme out of the player's LunaLib settings into one
 * {@link RenderStyle}: the global tier (hatch, border smoothing, the cursor and preview highlight
 * tiers, desaturation profile) and one {@link CategoryStyle} per category the painting layer
 * declares. This is the single seam a rebuild reads the render settings through, so every knob
 * resolves in one place rather than being fetched ad hoc across the builders, and an incremental
 * re-shape restyles against the same snapshot.
 *
 * <p>Which categories there are, and how each reads its style, is the layer's
 * ({@link OwnerCategories}): this reads the global tier and asks the layer's declaration for the
 * rest.
 */
public final class RenderStyleReader {

    // The weights the preview tier traces a lit region's edge at, held here rather than offered as
    // settings - see readPreviewHighlightStyle for why they are not the player's to move.
    private static final double PREVIEW_WASH_OUTLINE_OPACITY = 0.9;
    private static final double PREVIEW_WASH_OUTLINE_WIDTH = 0.5;

    // Reads only settings; never instantiated.
    private RenderStyleReader() {
    }

    // Reads the global tier and every category style the painting layer declares into one theme,
    // so the build loop and every incremental re-shape draw from a single already-read snapshot.
    //
    // The preferences arrive rather than being read, because a category's style may ride on a
    // sidebar pick and every one of those is sampled once per rebuild: read here it would be a
    // second reading, free to disagree with the one the rebuild decided it was owed by.
    public static RenderStyle readRenderStyle(
            OwnerCategories categories,
            ContentInputs contentInputs) {

        return new RenderStyle(
            readGlobalStyle(),
            categories.readCategoryStyles(contentInputs));
    }

    // Folds the sector-wide knobs into the global tier: the hatch over contested cells,
    // the cluster-border smoothing, the two highlight tiers, and the two strengths a spotlight separates
    // its subject from its backdrop by - how far a receded owner's recede grey darkens,
    // and how far a spared cell's neutral lifts. The hatch angle is authored in degrees and
    // converted to
    // radians at the reader so the hatch math downstream stays in radians. The player's hatch
    // width and smoothing are properties of the stroke rather than of the pattern, so both are
    // read into the stroke the renderer dispatches on rather than sitting loose beside the layout.
    public static GlobalStyle readGlobalStyle() {

        return new GlobalStyle(
            new HatchStyle(
                new HatchPattern(
                    KmuOwnerMapGeometrySettings.getOwnerMapHatchSpacing(),
                    KmuOwnerMapGeometrySettings.getOwnerMapHatchAngleRadians(),
                    KmuOwnerMapGeometrySettings.getOwnerMapHatchJoinToleranceFraction()),
                new GlLineHatchStroke(
                    resolveHatchLineQualityOf(KmuOwnerMapGeometrySettings.shouldSmoothHatchLines()),
                    KmuOwnerMapGeometrySettings.getOwnerMapHatchWidthFraction())),
            readBorderSmoothingStyle(),
            readHoverHighlightStyle(),
            readPreviewHighlightStyle(),
            KmuOwnerMapStyleSettings.getOwnerMapDesaturationDarkening(),
            KmuOwnerMapStyleSettings.getOwnerMapPresenceLightening());
    }

    /**
     * Reads the border smoothing profile: each pass's "Map - Dev" gate alongside the shape that
     * same pass works to, so a knob lands in the sub-record of the pass that reads it. Read as one
     * value so every pass over any loops - a cluster border, a lone cell's outline, the debug
     * capture of each stage - works to the same numbers.
     *
     * @return the sector-wide smoothing profile as the player has it set
     */
    static BorderSmoothingStyle readBorderSmoothingStyle() {

        return new BorderSmoothingStyle(
            new SpikeSandingStyle(
                KmuOwnerMapGeometrySettings.shouldSandBorderSpikes(),
                KmuOwnerMapGeometrySettings.getOwnerMapBorderSpikeHeight(),
                KmuOwnerMapGeometrySettings.getOwnerMapBorderSpikeAngleRadians()),
            new CornerRoundingStyle(
                KmuOwnerMapGeometrySettings.shouldRoundBorderCorners(),
                KmuOwnerMapGeometrySettings.getOwnerMapBorderCornerRadius(),
                KmuOwnerMapGeometrySettings.getOwnerMapBorderCornerSegments(),
                KmuOwnerMapGeometrySettings.getOwnerMapBorderChamferAngleRadians(),
                KmuOwnerMapGeometrySettings.getOwnerMapBorderRoundBelowAngleRadians()));
    }

    // Reads the cursor's feedback into one style: the shared palette choice both its elements
    // paint in, the frontier halo's stack and pulse, and the hovered cell's wash. Read here
    // with the rest of the theme rather than per frame in the highlight pass, so a knob moved
    // mid-hover repaints through the same rebuild every other style change does.
    static HoverHighlightStyle readHoverHighlightStyle() {

        return new HoverHighlightStyle(
            OwnerPaletteSlot.resolvePaintSelectionOf(KmuOwnerMapHighlightSettings.getOwnerMapHoverHighlightColour()),
            new HoverGlowStyle(
                KmuOwnerMapHighlightSettings.getOwnerMapHoverGlowOpacity(),
                KmuOwnerMapHighlightSettings.getOwnerMapHoverGlowWidth(),
                KmuOwnerMapHighlightSettings.getOwnerMapHoverGlowLayers(),
                KmuOwnerMapHighlightSettings.getOwnerMapHoverGlowPulseStrength(),
                KmuOwnerMapHighlightSettings.getOwnerMapHoverGlowPulsePeriod()),
            new HoverWashStyle(
                KmuOwnerMapHighlightSettings.getOwnerMapHoverWashOpacity(),
                KmuOwnerMapHighlightSettings.getOwnerMapHoverWashOutlineOpacity(),
                KmuOwnerMapHighlightSettings.getOwnerMapHoverWashOutlineWidth()));
    }

    // Reads the second highlight tier - the one a whole set of cells lights up in. It answers with
    // the wash alone: a halo is centred on the edge it blooms off, and a lit region here can be a
    // single system's cell, so the bloom spills inward across the cell and reads as a lump rather
    // than a lit rim, while two lit cells sitting close pile their halos into one bright patch.
    // Neither defect has a weight that resolves it, so the tier declines the halo outright.
    //
    // The trace's weights are fixed for the same reason rather than being offered as knobs: only a
    // thin, near-solid line reads as the edge of a lit region instead of as a second border laid
    // over the cluster's own.
    static HoverHighlightStyle readPreviewHighlightStyle() {

        return new HoverHighlightStyle(
            OwnerPaletteSlot.resolvePaintSelectionOf(
                KmuOwnerMapHighlightSettings.getOwnerMapPreviewHighlightColour()),
            HoverGlowStyle.NO_GLOW,
            new HoverWashStyle(
                KmuOwnerMapHighlightSettings.getOwnerMapPreviewWashOpacity(),
                PREVIEW_WASH_OUTLINE_OPACITY,
                PREVIEW_WASH_OUTLINE_WIDTH));
    }

    // Turns the player's smoothing switch into the quality the hatch pass strokes at. Named here
    // rather than left as a conditional inside the assembly because the mapping is where a boolean
    // knob becomes a rendering term - the theme downstream reads a quality and never a switch.
    private static GlLineQuality resolveHatchLineQualityOf(boolean shouldSmoothLines) {

        return shouldSmoothLines
            ? GlLineQuality.SMOOTHED
            : GlLineQuality.ALIASED;
    }
}
