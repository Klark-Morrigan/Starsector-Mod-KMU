package kmu.maplayers.base.render;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Draws the active map layer's upper band over the map's nebula icons, which Starscape mode paints
 * as a large sprite laid over the sector. It is the third surface, and it exists for one reason: the
 * map paints those nebulae between the terrain icons it holds, so the only way to put part of an
 * overlay above them is to have a second icon seeded after them - which means a second terrain
 * entity, and so a second plugin for it to resolve to.
 *
 * <p>Everything that decides <em>whether</em> to draw is inherited from
 * {@link SectorMapLayerStarscapeTerrainPlugin}: the whitelisted type its entity reports, and the
 * stand-aside that keeps it off a schematic map. All this adds is which band it emits, which is the
 * whole of the difference between the two Starscape surfaces.
 *
 * <p>It does not prepare the frame. The lower band is prepared by the surface that paints it, and
 * that surface is always reached first - it is present in every save the map is drawn from, and its
 * icon is always seeded ahead of this one, which is the same ordering that puts this pass above the
 * nebulae. Should this one ever paint first regardless, it paints the previous frame's geometry: one
 * frame of lag in whatever the layer put above, and no double preparation.
 *
 * <p>Being drawn above the nebulae is not something this class arranges. Its icon is seeded with
 * every other hyperspace entity, ahead of the nebulae the widget appends per map open, and it is
 * lifted past them by being taken out of the location for one advance - see the reseat wired at the
 * mod's entry point. Without that lift this surface paints exactly where the lower one does, which
 * is the pre-split picture and the graceful direction for it to fail in.
 */
public class SectorMapLayerAboveStarscapeNebulaeTerrainPlugin
        extends SectorMapLayerStarscapeTerrainPlugin {

    // The band clear of the fog, whatever the drawing layer chose to emit into it.
    private static final List<MapOverlayBand> ABOVE_BAND =
        List.of(MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

    /** The pairing the terrain spec instantiates, resolving its Starscape read on first render. */
    public SectorMapLayerAboveStarscapeNebulaeTerrainPlugin() {
    }

    /** Binds the Starscape read explicitly instead of letting the first render resolve it. */
    SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(BooleanSupplier isStarscapeMapShowing) {
        super(isStarscapeMapShowing);
    }

    @Override
    protected List<MapOverlayBand> resolvePaintedBands() {
        return ABOVE_BAND;
    }
}
