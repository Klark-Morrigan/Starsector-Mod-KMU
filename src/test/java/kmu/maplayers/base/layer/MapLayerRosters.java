package kmu.maplayers.base.layer;

import java.util.List;

/**
 * Puts {@link MapLayerRegistry} back to a usable roster. The registry is static, so a test that
 * deliberately empties it - to observe what the map surface does before any composition root has
 * run - would otherwise leave every later test in the JVM resolving to no layer at all. Any test
 * class with such a case restores through here rather than each carrying its own copy.
 *
 * <p>The roster it leaves is {@link NoLayer} alone: a real layer rather than a stand-in, and the one
 * whose whole contract is to draw nothing, so nothing downstream is dragged in by the restore.
 */
public final class MapLayerRosters {

    private MapLayerRosters() {
    }

    /** Registers a minimal non-empty roster, so a following test finds the registry populated. */
    public static void restoreNonEmptyRoster() {
        MapLayerRegistry.registerLayers(List.of(NoLayer.INSTANCE), NoLayer.INSTANCE);
    }
}
