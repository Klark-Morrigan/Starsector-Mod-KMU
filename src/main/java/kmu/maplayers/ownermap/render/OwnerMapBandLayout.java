package kmu.maplayers.ownermap.render;

import kmu.maplayers.base.render.MapOverlayBand;

/**
 * Which side of the map's own nebulae each of the owner map's choosable sub-layers paints
 * on, for one frame. The player picks a side per sub-layer and this is that pick expressed as
 * bands, so the compositor asks what it is painting rather than matching a stored choice.
 *
 * <p>The value alone. Which band a player's choice amounts to is read beside the settings it comes
 * from and handed in, which is what lets the framework's {@link MapOverlayBand} go on knowing
 * nothing about settings: the bands stay named for their positions, the layout says what rides
 * where, and neither has to name a choice.
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
public record OwnerMapBandLayout(
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
    public OwnerMapBandLayout {
        borderBand = liftBordersToTheirFills(borderBand, fillBand);
    }

    // The fills' band is the floor for the borders: lifting the fills lifts the borders with them,
    // while borders lifted alone are left where they were asked for.
    private static MapOverlayBand liftBordersToTheirFills(
            MapOverlayBand borderBand,
            MapOverlayBand fillBand) {

        return fillBand == MapOverlayBand.ABOVE_STARSCAPE_NEBULAE ? fillBand : borderBand;
    }
}
