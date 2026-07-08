package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import java.util.List;

/**
 * The map-layer registry and the single source of truth for which layer is active. The layer
 * bar composes its tabs from {@link #getLayers()}, the input listener switches the pick with
 * {@link #selectLayer}, and each layer's own state reads {@link #isActive} to decide whether
 * it is the one to draw - so all agree on one selection without sharing state directly.
 *
 * <p>This is the feature-agnostic framework half: it knows nothing of any concrete layer.
 * The set of layers and the default pick are supplied once at startup by a composition root
 * through {@link #registerLayers}, so a new view is added by registering it rather than by
 * editing this class. The active pick lives in sector memory under a stable key, so it
 * serialises into the save and survives reload; an untouched save resolves to the registered
 * default.
 */
public final class MapLayerRegistry {
    // Save-serialised identity of the active pick; frozen once shipped, since renaming it
    // silently resets every existing save to the default.
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer";

    // The registered layers, in tab order, and the pick an untouched save resolves to. Empty
    // until a composition root registers them at startup, before any sector map can open.
    private static List<MapLayer> orderedLayers = List.of();
    private static MapLayer defaultLayer;

    private MapLayerRegistry() {
    }

    /**
     * Records the layers the bar shows and the pick an untouched save resolves to. Called once
     * by the composition root at startup: it is the only place a concrete layer is named, so
     * the framework here stays agnostic to which views exist.
     *
     * @param layers        the registered layers in tab order, left to right
     * @param defaultLayer  the pick an untouched save (or a stale stored id) resolves to
     */
    public static void registerLayers(List<MapLayer> layers, MapLayer defaultLayer) {
        orderedLayers = List.copyOf(layers);
        MapLayerRegistry.defaultLayer = defaultLayer;
    }

    /** @return the registered layers in tab order, left to right. */
    public static List<MapLayer> getLayers() {
        return orderedLayers;
    }

    /**
     * @return the active layer, resolved from sector memory; the default when the save holds
     *         no pick yet or an id from an older build no longer registered
     */
    public static MapLayer getActiveLayer() {
        var storedId = readStoredLayerId();
        if (storedId == null) {
            return defaultLayer;
        }
        for (var layer : orderedLayers) {
            if (layer.getId().equals(storedId)) {
                return layer;
            }
        }
        // Stale id from a build that shipped a layer since removed: fall back rather than
        // leave the bar pointing at nothing.
        return defaultLayer;
    }

    /** @return whether {@code layer} is the active pick. */
    public static boolean isActive(MapLayer layer) {
        // Layers are singletons, so identity settles it without an id compare.
        return getActiveLayer() == layer;
    }

    /**
     * Records {@code layer} as the active pick in sector memory. A no-op before the sector
     * exists, since there is no save to write into yet.
     */
    public static void selectLayer(MapLayer layer) {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        memory.set(ACTIVE_LAYER_KEY, layer.getId());
    }

    /**
     * Rewrites the stored active pick from a layer's former id to its current one, so a save
     * written before a layer's id was renamed tracks the current id in place rather than falling
     * back to the default and leaving the stale id in the save. The mechanism only - the caller
     * (which owns the concrete rename) supplies the ids, so this framework stays agnostic to which
     * layers exist. A no-op before the sector exists or when the stored pick is not {@code legacyId}.
     *
     * @param legacyId  the id the layer stored before it was renamed
     * @param currentId the layer's current id to rewrite the stored pick to
     */
    public static void migrateStoredLayerId(String legacyId, String currentId) {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(ACTIVE_LAYER_KEY)) {
            return;
        }
        if (legacyId.equals(memory.getString(ACTIVE_LAYER_KEY))) {
            memory.set(ACTIVE_LAYER_KEY, currentId);
        }
    }

    // Reads the stored layer id, or null when the sector is absent or the key was never
    // written - the caller resolves either to the default.
    private static String readStoredLayerId() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(ACTIVE_LAYER_KEY)) {
            return null;
        }
        return memory.getString(ACTIVE_LAYER_KEY);
    }
}
