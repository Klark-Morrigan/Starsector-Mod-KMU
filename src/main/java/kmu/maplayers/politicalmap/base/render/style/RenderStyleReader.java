package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
import kmu.maplayers.politicalmap.base.render.style.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HatchStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverGlowStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverHighlightStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverWashStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.theme.RenderStyle;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reads the whole political-map theme out of the player's LunaLib settings into one
 * {@link RenderStyle}: the global tier (hatch, border smoothing, hover highlight, desaturation
 * profile) and
 * one {@link CategoryStyle} per {@link MapCategory}. This is the single seam a rebuild reads
 * the render settings through, so every knob resolves in one place rather than being fetched
 * ad hoc across the builders, and an incremental re-shape restyles against the same snapshot.
 *
 * <p>The two owned categories - core factions and independent space - carry a full
 * fill/outer/inner style. The two factionless categories - decivilised and uninhabited -
 * have no faction palette, so both paint in the shared neutral color and neither carries a
 * colour choice: decivilised ground draws a fill and an outline, uninhabited ground an
 * outline alone, and both set their inner seam to "No color" since factionless cells never
 * fuse into clusters. Whether the uninhabited outline draws at all is the player's sidebar
 * checkbox rather than a settings field, so that one input is read from the per-save
 * preference; every other input here is a LunaLib knob.
 */
public final class RenderStyleReader {

    // Reads only settings; never instantiated.
    private RenderStyleReader() {
    }

    // Reads the global tier and all four category styles into one theme, so the build loop
    // and every incremental re-shape draw from a single already-read snapshot.
    public static RenderStyle readRenderStyle() {
        Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
        categories.put(MapCategory.FACTION, readFactionStyle());
        categories.put(MapCategory.INDEPENDENT, readIndependentStyle());
        categories.put(MapCategory.DECIVILISED, readDecivilisedStyle());
        categories.put(MapCategory.UNINHABITED, readUninhabitedStyle());
        return new RenderStyle(readGlobalStyle(), categories);
    }

    // Folds the sector-wide knobs into the global tier: the contested-fill hatch, the
    // national-border smoothing, the hover highlight, and how far a receded bloc's
    // Independent-based grey darkens. The hatch angle is authored in degrees and converted to
    // radians at the reader so the hatch math downstream stays in radians.
    public static GlobalStyle readGlobalStyle() {
        return new GlobalStyle(
                new HatchStyle(
                        KmuLunaSettings.getPoliticalMapHatchSpacing(),
                        KmuLunaSettings.getPoliticalMapHatchAngleRadians(),
                        KmuLunaSettings.getPoliticalMapHatchWidth()),
                new BorderSmoothingStyle(
                        KmuLunaSettings.shouldSandBorderSpikes(),
                        KmuLunaSettings.shouldRoundBorderCorners(),
                        KmuLunaSettings.getPoliticalMapBorderCornerRadius(),
                        KmuLunaSettings.getPoliticalMapBorderCornerSegments(),
                        KmuLunaSettings.getPoliticalMapBorderChamferAngleRadians()),
                readHoverHighlightStyle(),
                KmuLunaSettings.getPoliticalMapDesaturationDarkening());
    }

    // Reads the cursor's feedback into one style: the shared palette choice both its elements
    // paint in, the frontier halo's stack and pulse, and the hovered cell's wash. Read here
    // with the rest of the theme rather than per frame in the highlight pass, so a knob moved
    // mid-hover repaints through the same rebuild every other style change does.
    public static HoverHighlightStyle readHoverHighlightStyle() {
        return new HoverHighlightStyle(
                KmuLunaSettings.getPoliticalMapHoverHighlightColor(),
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
                        KmuLunaSettings.getFactionFillColor(),
                        KmuLunaSettings.getFactionFillOpacity()),
                new ElementStyle(
                        KmuLunaSettings.getFactionOuterBorderColor(),
                        KmuLunaSettings.getFactionOuterBorderOpacity()),
                KmuLunaSettings.getFactionOuterBorderWidth(),
                new ElementStyle(
                        KmuLunaSettings.getFactionInnerBorderColor(),
                        KmuLunaSettings.getFactionInnerBorderOpacity()),
                KmuLunaSettings.getFactionInnerBorderWidth());
    }

    public static CategoryStyle readIndependentStyle() {
        return new CategoryStyle(
                new ElementStyle(
                        KmuLunaSettings.getIndependentFillColor(),
                        KmuLunaSettings.getIndependentFillOpacity()),
                new ElementStyle(
                        KmuLunaSettings.getIndependentOuterBorderColor(),
                        KmuLunaSettings.getIndependentOuterBorderOpacity()),
                KmuLunaSettings.getIndependentOuterBorderWidth(),
                new ElementStyle(
                        KmuLunaSettings.getIndependentInnerBorderColor(),
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
                        FactionPaletteChoice.PRIMARY,
                        KmuLunaSettings.getDecivilisedFillOpacity()),
                true,
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
    // border (in the neutral color via a PRIMARY choice, or NONE to hide it), and no inner
    // seam - factionless cells do not fuse into clusters, so they have no province seams to
    // stroke. Both palette slots hold the neutral color at draw time, so PRIMARY and
    // SECONDARY would paint identically; PRIMARY is the drawn arm throughout.
    private static CategoryStyle neutralStyle(
            ElementStyle fill,
            boolean isOutlineDrawn,
            double outlineOpacity,
            double outlineWidth) {

        var outerColor = isOutlineDrawn
                ? FactionPaletteChoice.PRIMARY
                : FactionPaletteChoice.NONE;

        return new CategoryStyle(
                fill,
                new ElementStyle(outerColor, outlineOpacity),
                outlineWidth,
                ElementStyle.NOT_DRAWN,
                0);
    }
}
