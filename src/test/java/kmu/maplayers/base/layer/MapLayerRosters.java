package kmu.maplayers.base.layer;

import kmlib.starsector.ui.intel.IntelScreenView;

/**
 * Builds the roster, the bar arrangement and the screens a case poses, each a fresh instance so nothing
 * one case registers or stands is seen by the next.
 *
 * <p>The default roster is {@link NoLayer} alone: a real layer rather than a stand-in, and the one whose
 * whole contract is to draw nothing, so nothing downstream is dragged in by it. It declines to be the
 * default pick, and with nothing else registered the fallback answers it anyway, so it resolves to a
 * pick as the shipped roster does.
 */
public final class MapLayerRosters {

    private MapLayerRosters() {
    }

    /**
     * @param layers the layers to register, left to right
     * @return a roster holding exactly those layers, in that order
     */
    public static MapLayerRegistry createRosterOf(MapLayer... layers) {

        var layerRegistry = new MapLayerRegistry();

        for (var layer : layers) {
            layerRegistry.registerLayer(layer);
        }
        return layerRegistry;
    }

    /**
     * @param layers the layers to register, left to right
     * @return screens over exactly those layers, unarranged, always answering for the sector map
     */
    public static MapLayerScreens createScreensOver(MapLayer... layers) {
        return createScreensOver(createRosterOf(layers), MapLayerArrangements.createUnarrangedBar(), null);
    }

    /**
     * @param layerRegistry    the roster both screens pick from
     * @param layerArrangement the player's arrangement of that roster
     * @param intelScreen      whether the intel screen is up, or null for screens answering for the map
     * @return screens over that roster and arrangement
     */
    public static MapLayerScreens createScreensOver(
            MapLayerRegistry layerRegistry,
            LiveMapLayerArrangement layerArrangement,
            IntelScreenView intelScreen) {

        return new MapLayerScreens(layerRegistry, layerArrangement, intelScreen);
    }

    /**
     * @return screens over the minimal non-empty roster, for a case that needs some bar but none in
     *         particular
     */
    public static MapLayerScreens createScreensOverNoLayer() {
        return createScreensOver(NoLayer.INSTANCE);
    }
}
