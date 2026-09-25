package kmu.maplayers.ownermap.render.style;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.HatchPattern;

import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlLineHatchStroke;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.HatchStyle;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.base.theme.HoverWashStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuOwnerMapGeometrySettings;
import kmu.settings.KmuOwnerMapHighlightSettings;
import kmu.settings.KmuOwnerMapStyleSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the whole owner-map theme out of the player's LunaLib settings into one
 * {@link RenderStyle}: the global tier (hatch, border smoothing, the cursor and preview highlight
 * tiers, desaturation profile) and one {@link CategoryStyle} per {@link OwnerMapCategory}.
 * This is the single
 * seam a rebuild reads the render settings through, so every knob resolves in one place rather
 * than being fetched ad hoc across the builders, and an incremental re-shape restyles against
 * the same snapshot.
 *
 * <p>The two owned categories - core factions and independent space - carry a full
 * fill/outer/inner style. The two factionless categories - decivilised and uninhabited -
 * have no faction palette, so both paint in the shared neutral colour and neither carries a
 * colour choice: a decivilised cell draws a fill and an outline, an uninhabited cell an
 * outline alone, and both set their inner seam to "No color" since factionless cells never
 * fuse into clusters. Whether the uninhabited outline draws at all is the player's sidebar
 * checkbox rather than a settings field, so that one input arrives from the rebuild's own sampling
 * of the preferences instead of being read here; every other input is a LunaLib knob this reads.
 */
public final class RenderStyleReader {

    // The weights the preview tier traces a lit region's edge at, held here rather than offered as
    // settings - see readPreviewHighlightStyle for why they are not the player's to move.
    private static final double PREVIEW_WASH_OUTLINE_OPACITY = 0.9;
    private static final double PREVIEW_WASH_OUTLINE_WIDTH = 0.5;

    // Reads only settings; never instantiated.
    private RenderStyleReader() {
    }

    // Reads the global tier and all four category styles into one theme, so the build loop
    // and every incremental re-shape draw from a single already-read snapshot. The theme keys
    // on the open MapStyleCategory rather than on this layer's enum, so the map is a plain
    // hash map rather than an EnumMap - four inserts once per rebuild, against a lookup the
    // framework can serve for any layer's categories.
    //
    // The uninhabited outline's on/off arrives rather than being read, because it is a sidebar
    // preference and every one of those is sampled once per rebuild: read here it would be a second
    // reading, free to disagree with the one the rebuild decided it was owed by.
    public static RenderStyle readRenderStyle(boolean isUninhabitedOutlineDrawn) {
        Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();
        categories.put(OwnerMapCategory.FACTION, readFactionStyle());
        categories.put(OwnerMapCategory.INDEPENDENT, readIndependentStyle());
        categories.put(OwnerMapCategory.DECIVILISED, readDecivilisedStyle());
        categories.put(
            OwnerMapCategory.UNINHABITED,
            readUninhabitedStyle(isUninhabitedOutlineDrawn));

        return new RenderStyle(readGlobalStyle(), categories);
    }

    // Folds the sector-wide knobs into the global tier: the hatch over contested cells,
    // the cluster-border smoothing, the two highlight tiers, and the two strengths a spotlight separates
    // its subject from its backdrop by - how far a receded bloc's Independent-based grey darkens,
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
            FactionPaletteSlot.resolvePaintSelectionOf(KmuOwnerMapHighlightSettings.getOwnerMapHoverHighlightColour()),
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
            FactionPaletteSlot.resolvePaintSelectionOf(
                KmuOwnerMapHighlightSettings.getOwnerMapPreviewHighlightColour()),
            HoverGlowStyle.NO_GLOW,
            new HoverWashStyle(
                KmuOwnerMapHighlightSettings.getOwnerMapPreviewWashOpacity(),
                PREVIEW_WASH_OUTLINE_OPACITY,
                PREVIEW_WASH_OUTLINE_WIDTH));
    }

    // Reads each owned category's eight style settings into one bundle, so the build
    // loop applies them per cluster without eight lookups each.
    //
    // The two owned categories are the same eight knobs read twice over, and stay two methods
    // rather than one parameterised by group. The field IDs cannot be composed from a group name:
    // each is held whole in a constant so it traces to its row in the shipped settings table, and
    // an ID built from parts would trace to none and be free to drift from it. What the two share
    // is the crossing below, which is named once for that reason.
    static CategoryStyle readFactionStyle() {
        return new CategoryStyle(
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getFactionFillColour(),
                KmuOwnerMapStyleSettings.getFactionFillOpacity()),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getFactionOuterBorderColour(),
                KmuOwnerMapStyleSettings.getFactionOuterBorderOpacity()),
            KmuOwnerMapStyleSettings.getFactionOuterBorderWidth(),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getFactionInnerBorderColour(),
                KmuOwnerMapStyleSettings.getFactionInnerBorderOpacity()),
            KmuOwnerMapStyleSettings.getFactionInnerBorderWidth());
    }

    static CategoryStyle readIndependentStyle() {
        return new CategoryStyle(
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getIndependentFillColour(),
                KmuOwnerMapStyleSettings.getIndependentFillOpacity()),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getIndependentOuterBorderColour(),
                KmuOwnerMapStyleSettings.getIndependentOuterBorderOpacity()),
            KmuOwnerMapStyleSettings.getIndependentOuterBorderWidth(),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getIndependentInnerBorderColour(),
                KmuOwnerMapStyleSettings.getIndependentInnerBorderOpacity()),
            KmuOwnerMapStyleSettings.getIndependentInnerBorderWidth());
    }

    // Decivilised colonies keep both a neutral fill and a neutral outline: the system was settled
    // once, so it reads as occupied space rather than a bare ring around nothing. Neither
    // element has a colour choice - a factionless cell has no palette to pick from - so the
    // outline is unconditionally drawn and each opacity is its element's own on/off.
    static CategoryStyle readDecivilisedStyle() {
        return buildNeutralStyle(
            resolvePaintedElement(
                FactionPaletteChoice.PRIMARY,
                KmuOwnerMapStyleSettings.getDecivilisedFillOpacity()),
            true, // Outline is drawn.
            KmuOwnerMapStyleSettings.getDecivilisedBorderOpacity(),
            KmuOwnerMapStyleSettings.getDecivilisedBorderWidth());
    }

    // Never-settled space stays outline-only: filling it would wash the whole sector, since
    // uninhabited cells cover everything no faction and no decivilised colony holds. Its outline's
    // on/off is the one style input that is not a LunaLib field - the sidebar's
    // uninhabited-systems checkbox, a per-save preference the rebuild samples and hands over -
    // while the opacity and width it strokes at stay settings-screen knobs read here.
    static CategoryStyle readUninhabitedStyle(boolean isUninhabitedOutlineDrawn) {
        return buildNeutralStyle(
            ElementStyle.NOT_DRAWN,
            isUninhabitedOutlineDrawn,
            KmuOwnerMapStyleSettings.getUninhabitedBorderOpacity(),
            KmuOwnerMapStyleSettings.getUninhabitedBorderWidth());
    }

    // One element's paint as the player authored it: a palette choice crossed into the selection
    // the draw pass resolves, paired with the opacity it paints at.
    //
    // Named because the crossing is the same step for all seven elements read here, and an inline
    // copy of it is what lets one element end up pointed at a slot its opacity was never chosen
    // against - a drift neither half shows on its own, both halves being right.
    private static ElementStyle resolvePaintedElement(FactionPaletteChoice colour, double opacity) {
        return new ElementStyle(FactionPaletteSlot.resolvePaintSelectionOf(colour), opacity);
    }

    // Turns the player's smoothing switch into the quality the hatch pass strokes at. Named here
    // rather than left as a conditional inside the assembly because the mapping is where a boolean
    // knob becomes a rendering term - the theme downstream reads a quality and never a switch.
    private static GlLineQuality resolveHatchLineQualityOf(boolean shouldSmoothLines) {
        return shouldSmoothLines ? GlLineQuality.SMOOTHED : GlLineQuality.ALIASED;
    }

    // Assembles a factionless category's style: the given fill, its outline as the outer
    // border (pointed at the PRIMARY slot, or at nothing at all to hide it), and no inner seam -
    // factionless cells do not fuse into clusters, so they have no interior seams to stroke.
    // Both palette slots hold the neutral colour at draw time, so PRIMARY and SECONDARY would
    // paint identically; PRIMARY is the drawn arm throughout.
    //
    // The hidden arm is no selection rather than a no-colour one: this style is assembled here
    // rather than read from a player choice, so it can state the off case the way a style holds
    // it directly instead of routing a settings value through the crossing.
    private static CategoryStyle buildNeutralStyle(
            ElementStyle fill,
            boolean isOutlineDrawn,
            double outlineOpacity,
            double outlineWidth) {

        var outerPaint = isOutlineDrawn ? FactionPaletteSlot.PRIMARY : null;

        return new CategoryStyle(
            fill,
            new ElementStyle(outerPaint, outlineOpacity),
            outlineWidth,
            ElementStyle.NOT_DRAWN,
            0);
    }

}
