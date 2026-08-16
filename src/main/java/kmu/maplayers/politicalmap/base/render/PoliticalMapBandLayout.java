package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.render.MapOverlayBand;
import kmu.settings.KmuPoliticalMapSettings;
import kmu.settings.NebulaDrawOrderChoice;

/**
 * Which side of the map's own nebulae each of the political overlay's choosable sub-layers paints
 * on, for one frame. The player picks a side per sub-layer and this is that pick expressed as
 * bands, so the compositor asks what it is painting rather than matching a stored choice.
 *
 * <p>The mapping from a choice to a band lives here, on the layer's side of the render seam, which
 * is what lets the framework's {@link MapOverlayBand} go on knowing nothing about settings: the
 * bands stay named for their positions, and what rides where is answered where the answer comes
 * from.
 *
 * <p>Only four sub-layers are chosen. The rest ride with one of them, each pinned to the choice its
 * own picture depends on - the contested hatch and the hover feedback to the fills they are drawn
 * into and over, the cluster anchors and the border-tracing overlay to the base view they annotate
 * or replace - and a rider is followed by reading the band its owner landed in.
 *
 * @param fillBand   where the cluster and lone-cell fills paint, the contested hatch inside them
 * @param borderBand where the cluster boundaries, province seams, and factionless outlines paint.
 *                   Never lower than {@code fillBand}, whatever was asked for
 * @param ribbonBand where the per-cell presence bands paint
 * @param labelBand  where the cluster names paint
 */
record PoliticalMapBandLayout(
    MapOverlayBand fillBand,
    MapOverlayBand borderBand,
    MapOverlayBand ribbonBand,
    MapOverlayBand labelBand) {

    /**
     * Resolves the one combination that is not offered, so that every layout in hand is already
     * coherent and no emitting pass has to carry the rule.
     *
     * <p>Fills above their own borders would bury them, and everything else in the cell with them.
     * The asymmetry is the picture's: a border drawn over its own fill is still a border, while a
     * fill drawn over its own border leaves a blank cell. So the borders may be lifted alone, and
     * are lifted with the fills where the player left them lower.
     */
    PoliticalMapBandLayout {
        borderBand = liftBordersToTheirFills(borderBand, fillBand);
    }

    /**
     * Reads the player's four draw-order choices as one layout.
     *
     * <p>Read per band pass rather than held: four map lookups against LunaLib, the same order of
     * cost as the band style the presence pass already reads every frame, where a cached copy would
     * have to watch the settings revision and be reset for no measurable gain.
     *
     * @return where each choosable sub-layer paints this pass
     */
    static PoliticalMapBandLayout readChosenLayout() {
        return new PoliticalMapBandLayout(
            resolveBand(KmuPoliticalMapSettings.getPoliticalMapFillNebulaDrawOrder()),
            resolveBand(KmuPoliticalMapSettings.getPoliticalMapBorderNebulaDrawOrder()),
            resolveBand(KmuPoliticalMapSettings.getPoliticalMapRibbonNebulaDrawOrder()),
            resolveBand(KmuPoliticalMapSettings.getPoliticalMapLabelNebulaDrawOrder()));
    }

    // The whole of the translation: the map draws its nebulae between two of its terrain passes, so
    // a draw order the player picked is a band and there is no third answer either could have. A switch
    // expression rather than a comparison, so a choice added later fails to compile here instead of
    // quietly resolving to whichever band the else branch named.
    private static MapOverlayBand resolveBand(NebulaDrawOrderChoice drawOrder) {
        return switch (drawOrder) {
            case BELOW -> MapOverlayBand.BENEATH_STARSCAPE_NEBULAE;
            case ABOVE -> MapOverlayBand.ABOVE_STARSCAPE_NEBULAE;
        };
    }

    // The fills' band is the floor for the borders: lifting the fills lifts the borders with them,
    // while borders lifted alone are left where they were asked for.
    private static MapOverlayBand liftBordersToTheirFills(
            MapOverlayBand borderBand,
            MapOverlayBand fillBand) {

        return fillBand == MapOverlayBand.ABOVE_STARSCAPE_NEBULAE ? fillBand : borderBand;
    }
}
