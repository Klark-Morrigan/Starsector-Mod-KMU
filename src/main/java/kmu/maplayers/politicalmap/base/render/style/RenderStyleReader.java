package kmu.maplayers.politicalmap.base.render.style;

import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reads the whole political-map theme out of the player's LunaLib settings into one
 * {@link RenderStyle}: the global tier (hatch, border smoothing, desaturation profile) and
 * one {@link CategoryStyle} per {@link MapCategory}. This is the single seam a rebuild reads
 * the render settings through, so every knob resolves in one place rather than being fetched
 * ad hoc across the builders, and an incremental re-shape restyles against the same snapshot.
 *
 * <p>The two owned categories - core factions and independent space - carry a full
 * fill/outer/inner style. The two factionless categories - decivilised and uninhabited -
 * carry only an outline, so their bundles set fill and inner seam to "No color" and draw a
 * single border in the shared neutral color.
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
    // national-border smoothing, and how far a receded bloc's Independent-based grey darkens.
    // The hatch angle is authored in degrees and converted to radians at the reader so the
    // hatch math downstream stays in radians.
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
                KmuLunaSettings.getPoliticalMapDesaturationDarkening());
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

    // A factionless category resolves to the same style with no fill and no inner
    // seam - only its single outline draws, in the neutral color both palette slots
    // will carry, or "No color" to hide it.
    public static CategoryStyle readDecivilisedStyle() {
        return neutralStyle(KmuLunaSettings.getDecivilisedBorderColor(),
                KmuLunaSettings.getDecivilisedBorderOpacity(),
                KmuLunaSettings.getDecivilisedBorderWidth());
    }

    public static CategoryStyle readUninhabitedStyle() {
        return neutralStyle(KmuLunaSettings.getUninhabitedBorderColor(),
                KmuLunaSettings.getUninhabitedBorderOpacity(),
                KmuLunaSettings.getUninhabitedBorderWidth());
    }

    // Assembles a factionless outline's style: its outline as the outer border (in the
    // neutral color via a PRIMARY choice, or NONE to hide it), with no fill and no
    // inner seam. Both slots hold the neutral color at draw time, so PRIMARY and
    // SECONDARY would paint identically; PRIMARY is the drawn arm here.
    private static CategoryStyle neutralStyle(NeutralColorChoice color, double opacity,
            double width) {
        var outerColor = color.isDrawn() ? FactionPaletteChoice.PRIMARY : FactionPaletteChoice.NONE;
        return new CategoryStyle(ElementStyle.NOT_DRAWN, new ElementStyle(outerColor, opacity),
                width, ElementStyle.NOT_DRAWN, 0);
    }
}
