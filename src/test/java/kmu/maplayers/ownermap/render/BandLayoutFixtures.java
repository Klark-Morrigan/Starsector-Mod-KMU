package kmu.maplayers.ownermap.render;

import kmu.maplayers.base.render.MapOverlayBand;

/**
 * Where a drawn layer's choosable sub-layers ride, as a tier case states it.
 *
 * <p>The compositor is handed a layout rather than reading one, so a case about which band a
 * sub-layer lands in states the answer outright. Which settings a layer offers to move it, and
 * how it reads them, are that layer's own and no tier case's business.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class BandLayoutFixtures {

    private BandLayoutFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * The split the shipped defaults amount to: the geometry beneath the map's own nebulae and the
     * readouts above them.
     *
     * @return that layout
     */
    public static OwnerMapBandLayout buildGeometryBelowAndReadoutsAbove() {
        return buildLayout(
            MapOverlayBand.BENEATH_STARSCAPE_NEBULAE,
            MapOverlayBand.BENEATH_STARSCAPE_NEBULAE,
            MapOverlayBand.ABOVE_STARSCAPE_NEBULAE,
            MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);
    }

    /**
     * A layout stating each sub-layer's band one by one, in the order the layout declares them.
     *
     * @param fillBand   where the cluster and lone-cell fills paint
     * @param borderBand where the boundaries and seams paint; the layout lifts this to the fills
     *                   where they were put above it
     * @param ribbonBand where the per-cell presence bands paint
     * @param labelBand  where the cluster names paint
     * @return that layout
     */
    public static OwnerMapBandLayout buildLayout(
            MapOverlayBand fillBand,
            MapOverlayBand borderBand,
            MapOverlayBand ribbonBand,
            MapOverlayBand labelBand) {

        return new OwnerMapBandLayout(fillBand, borderBand, ribbonBand, labelBand);
    }
}
