package kmu.maplayers.politicalmap.base.render.style;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.HatchJoining;

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
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the whole political-map theme out of the player's LunaLib settings into one
 * {@link RenderStyle}: the global tier (hatch, border smoothing, hover highlight, desaturation
 * profile) and one {@link CategoryStyle} per {@link PoliticalMapCategory}. This is the single
 * seam a rebuild reads the render settings through, so every knob resolves in one place rather
 * than being fetched ad hoc across the builders, and an incremental re-shape restyles against
 * the same snapshot.
 *
 * <p>The two owned categories - core factions and independent space - carry a full
 * fill/outer/inner style. The two factionless categories - decivilised and uninhabited -
 * have no faction palette, so both paint in the shared neutral colour and neither carries a
 * colour choice: decivilised ground draws a fill and an outline, uninhabited ground an
 * outline alone, and both set their inner seam to "No color" since factionless cells never
 * fuse into clusters. Whether the uninhabited outline draws at all is the player's sidebar
 * checkbox rather than a settings field, so that one input is read from the per-save
 * preference; every other input here is a LunaLib knob.
 */
public final class RenderStyleReader {

    // The hatch's two structural axes, fixed here rather than read back from a knob: the player
    // tunes the pattern (spacing, angle, width), not how the clipped line family is cut into
    // primitives or how those primitives rasterise. Named constants rather than literals inline in
    // the assembly below, so each decision is stated once, where the rest of the hatch is read.
    private static final HatchJoining HATCH_JOINING = HatchJoining.PER_TRIANGLE;

    // Aliased: the hatch is a dense field of short strokes, so smoothing it would cost a blend per
    // covered pixel across the whole contested territory while making every line read softer and
    // slightly wider - the opposite of the crisp texture the hatch is there to give.
    private static final GlLineQuality HATCH_LINE_QUALITY = GlLineQuality.ALIASED;

    // Reads only settings; never instantiated.
    private RenderStyleReader() {
    }

    // Reads the global tier and all four category styles into one theme, so the build loop
    // and every incremental re-shape draw from a single already-read snapshot. The theme keys
    // on the open MapStyleCategory rather than on this layer's enum, so the map is a plain
    // hash map rather than an EnumMap - four inserts once per rebuild, against a lookup the
    // framework can serve for any layer's categories.
    public static RenderStyle readRenderStyle() {
        Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();
        categories.put(PoliticalMapCategory.FACTION, readFactionStyle());
        categories.put(PoliticalMapCategory.INDEPENDENT, readIndependentStyle());
        categories.put(PoliticalMapCategory.DECIVILISED, readDecivilisedStyle());
        categories.put(PoliticalMapCategory.UNINHABITED, readUninhabitedStyle());
        return new RenderStyle(readGlobalStyle(), categories);
    }

    // Folds the sector-wide knobs into the global tier: the contested-fill hatch, the
    // national-border smoothing, the hover highlight, and how far a receded bloc's
    // Independent-based grey darkens. The hatch angle is authored in degrees and converted to
    // radians at the reader so the hatch math downstream stays in radians. The player's hatch
    // width is a property of the stroke rather than of the pattern, so it is read into the stroke
    // the renderer dispatches on rather than sitting loose beside the layout.
    public static GlobalStyle readGlobalStyle() {
        return new GlobalStyle(
            new HatchStyle(
                KmuLunaSettings.getPoliticalMapHatchSpacing(),
                KmuLunaSettings.getPoliticalMapHatchAngleRadians(),
                HATCH_JOINING,
                new GlLineHatchStroke(
                    HATCH_LINE_QUALITY,
                    KmuLunaSettings.getPoliticalMapHatchWidth())),
            readBorderSmoothingStyle(),
            readHoverHighlightStyle(),
            KmuLunaSettings.getPoliticalMapDesaturationDarkening());
    }

    /**
     * Reads the border smoothing profile: each pass's Dev-tab gate alongside the shape that same
     * pass works to, so a knob lands in the sub-record of the pass that reads it. Read as one
     * value so every pass over any loops - a cluster border, a lone cell's outline, the debug
     * capture of each stage - works to the same numbers.
     *
     * @return the sector-wide smoothing profile as the player has it set
     */
    public static BorderSmoothingStyle readBorderSmoothingStyle() {
        return new BorderSmoothingStyle(
            new SpikeSandingStyle(
                KmuLunaSettings.shouldSandBorderSpikes(),
                KmuLunaSettings.getPoliticalMapBorderSpikeHeight(),
                KmuLunaSettings.getPoliticalMapBorderSpikeAngleRadians()),
            new CornerRoundingStyle(
                KmuLunaSettings.shouldRoundBorderCorners(),
                KmuLunaSettings.getPoliticalMapBorderCornerRadius(),
                KmuLunaSettings.getPoliticalMapBorderCornerSegments(),
                KmuLunaSettings.getPoliticalMapBorderChamferAngleRadians()));
    }

    // Reads the cursor's feedback into one style: the shared palette choice both its elements
    // paint in, the frontier halo's stack and pulse, and the hovered cell's wash. Read here
    // with the rest of the theme rather than per frame in the highlight pass, so a knob moved
    // mid-hover repaints through the same rebuild every other style change does.
    public static HoverHighlightStyle readHoverHighlightStyle() {
        return new HoverHighlightStyle(
            FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getPoliticalMapHoverHighlightColour()),
            new HoverGlowStyle(
                KmuLunaSettings.getPoliticalMapHoverGlowOpacity(),
                KmuLunaSettings.getPoliticalMapHoverGlowWidth(),
                KmuLunaSettings.getPoliticalMapHoverGlowLayers(),
                KmuLunaSettings.getPoliticalMapHoverGlowPulseStrength(),
                KmuLunaSettings.getPoliticalMapHoverGlowPulsePeriod()),
            new HoverWashStyle(
                KmuLunaSettings.getPoliticalMapHoverWashOpacity(),
                KmuLunaSettings.getPoliticalMapHoverWashOutlineOpacity(),
                KmuLunaSettings.getPoliticalMapHoverWashOutlineWidth()));
    }

    // Reads each owned category's eight style settings into one bundle, so the build
    // loop applies them per cluster without eight lookups each.
    public static CategoryStyle readFactionStyle() {
        return new CategoryStyle(
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getFactionFillColour()),
                KmuLunaSettings.getFactionFillOpacity()),
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getFactionOuterBorderColour()),
                KmuLunaSettings.getFactionOuterBorderOpacity()),
            KmuLunaSettings.getFactionOuterBorderWidth(),
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getFactionInnerBorderColour()),
                KmuLunaSettings.getFactionInnerBorderOpacity()),
            KmuLunaSettings.getFactionInnerBorderWidth());
    }

    public static CategoryStyle readIndependentStyle() {
        return new CategoryStyle(
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getIndependentFillColour()),
                KmuLunaSettings.getIndependentFillOpacity()),
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getIndependentOuterBorderColour()),
                KmuLunaSettings.getIndependentOuterBorderOpacity()),
            KmuLunaSettings.getIndependentOuterBorderWidth(),
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(KmuLunaSettings.getIndependentInnerBorderColour()),
                KmuLunaSettings.getIndependentInnerBorderOpacity()),
            KmuLunaSettings.getIndependentInnerBorderWidth());
    }

    // Dead colonies keep both a neutral fill and a neutral outline: the ground was settled
    // once, so it reads as occupied space rather than a bare ring around nothing. Neither
    // element has a colour choice - factionless ground has no palette to pick from - so the
    // outline is unconditionally drawn and each opacity is its element's own on/off.
    public static CategoryStyle readDecivilisedStyle() {
        return neutralStyle(
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(FactionPaletteChoice.PRIMARY),
                KmuLunaSettings.getDecivilisedFillOpacity()),
            true, // Outline is drawn.
            KmuLunaSettings.getDecivilisedBorderOpacity(),
            KmuLunaSettings.getDecivilisedBorderWidth());
    }

    // Never-settled space stays outline-only: filling it would wash the whole sector, since
    // uninhabited cells cover everything no faction and no dead colony holds. Its outline's
    // on/off is the one style input that is not a LunaLib field - the sidebar's
    // uninhabited-systems checkbox, a per-save preference - while the opacity and width it
    // strokes at stay settings-screen knobs.
    public static CategoryStyle readUninhabitedStyle() {
        return neutralStyle(
            ElementStyle.NOT_DRAWN,
            UninhabitedOutlinePreference.isOutlineDrawn(),
            KmuLunaSettings.getUninhabitedBorderOpacity(),
            KmuLunaSettings.getUninhabitedBorderWidth());
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
    private static CategoryStyle neutralStyle(
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
