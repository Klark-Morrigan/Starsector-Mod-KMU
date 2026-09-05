package kmu.maplayers.base.layer;

/**
 * States the whole roster {@link MapLayerRegistry} holds, and puts it back to a usable one.
 *
 * <p>The registry accumulates - which is what lets each mod register its own layers as it loads -
 * and it is static, so a caller that registered would otherwise leave its layers standing in every
 * roster read afterwards in the same JVM, and one that deliberately emptied it would leave every
 * later read resolving to no layer at all. Both are stated here rather than at each caller: a roster
 * named as the whole of what stands is a different thing from a layer registering beside whatever
 * else is there, and only the first is answerable.
 *
 * <p>Sits in the registry's own package so the emptying it composes stays package-private there,
 * that being an act the running game has no use for - a bar with no tabs has no way back to one.
 * The same arrangement {@link MapLayerScreenControls} uses for the record beside it.
 *
 * <p>The roster restored is {@link NoLayer} alone: a real layer rather than a stand-in, and the one
 * whose whole contract is to draw nothing, so nothing downstream is dragged in by the restore. It
 * declines to be the default pick, and with nothing else registered the fallback answers it anyway,
 * so the restored roster resolves to a pick as the shipped one does.
 */
public final class MapLayerRosters {

    private MapLayerRosters() {
    }

    /**
     * Makes the roster exactly these layers, in this order, whatever stood in it before.
     *
     * @param layers the layers to register, left to right
     */
    public static void replaceRosterWith(MapLayer... layers) {

        MapLayerRegistry.forgetLayers();

        for (var layer : layers) {
            MapLayerRegistry.registerLayer(layer);
        }
    }

    /** Empties the roster, posing the reading a process that has registered nothing gives. */
    public static void forgetEveryLayer() {
        MapLayerRegistry.forgetLayers();
    }

    /** Registers a minimal non-empty roster, so a following caller finds the registry populated. */
    public static void restoreNonEmptyRoster() {
        replaceRosterWith(NoLayer.INSTANCE);
    }
}
