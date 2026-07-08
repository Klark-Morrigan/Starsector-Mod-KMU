package kmu.maplayers.politicalmap.factions.render;

import kmu.maplayers.politicalmap.factions.render.model.MapStyle;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;

/**
 * Reads each political-map category's style out of the player's LunaLib settings into
 * one {@link MapStyle} bundle, so a rebuild applies a category's colors, opacities,
 * and widths per cluster from a single already-read bundle rather than re-querying
 * settings per cell.
 *
 * <p>The two owned categories - core factions and independent space - carry a full
 * fill/outer/inner style. The two factionless categories - decivilised and
 * uninhabited - carry only an outline, so their bundles set fill and inner seam to
 * "No color" and draw a single border in the shared neutral color.
 */
final class MapStyleReader {

    // Reads only settings; never instantiated.
    private MapStyleReader() {
    }

    // Reads each owned category's eight style settings into one bundle, so the build
    // loop applies them per cluster without eight lookups each.
    static MapStyle readFactionStyle() {
        return new MapStyle(
                KmuLunaSettings.getFactionFillColor(), KmuLunaSettings.getFactionFillOpacity(),
                KmuLunaSettings.getFactionOuterBorderColor(),
                KmuLunaSettings.getFactionOuterBorderOpacity(),
                KmuLunaSettings.getFactionOuterBorderWidth(),
                KmuLunaSettings.getFactionInnerBorderColor(),
                KmuLunaSettings.getFactionInnerBorderOpacity(),
                KmuLunaSettings.getFactionInnerBorderWidth());
    }

    static MapStyle readIndependentStyle() {
        return new MapStyle(
                KmuLunaSettings.getIndependentFillColor(),
                KmuLunaSettings.getIndependentFillOpacity(),
                KmuLunaSettings.getIndependentOuterBorderColor(),
                KmuLunaSettings.getIndependentOuterBorderOpacity(),
                KmuLunaSettings.getIndependentOuterBorderWidth(),
                KmuLunaSettings.getIndependentInnerBorderColor(),
                KmuLunaSettings.getIndependentInnerBorderOpacity(),
                KmuLunaSettings.getIndependentInnerBorderWidth());
    }

    // A factionless category resolves to the same style with no fill and no inner
    // seam - only its single outline draws, in the neutral color both palette slots
    // will carry, or "No color" to hide it.
    static MapStyle readDecivilisedStyle() {
        return neutralStyle(KmuLunaSettings.getDecivilisedBorderColor(),
                KmuLunaSettings.getDecivilisedBorderOpacity(),
                KmuLunaSettings.getDecivilisedBorderWidth());
    }

    static MapStyle readUninhabitedStyle() {
        return neutralStyle(KmuLunaSettings.getUninhabitedBorderColor(),
                KmuLunaSettings.getUninhabitedBorderOpacity(),
                KmuLunaSettings.getUninhabitedBorderWidth());
    }

    // Assembles a factionless outline's style: its outline as the outer border (in the
    // neutral color via a PRIMARY choice, or NONE to hide it), with no fill and no
    // inner seam. Both slots hold the neutral color at draw time, so PRIMARY and
    // SECONDARY would paint identically; PRIMARY is the drawn arm here.
    private static MapStyle neutralStyle(NeutralColorChoice color, double opacity, double width) {
        var outerColor = color.isDrawn() ? FactionPaletteChoice.PRIMARY : FactionPaletteChoice.NONE;
        return new MapStyle(FactionPaletteChoice.NONE, 0, outerColor, opacity, width,
                FactionPaletteChoice.NONE, 0, 0);
    }
}
