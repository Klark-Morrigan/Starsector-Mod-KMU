package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

/**
 * A screen's active-layer pick persisted in sector memory, so the pick survives reload. Each screen holds
 * its own instance under its own scope, so the picks stay independent - a switch on one screen writes only
 * its key and leaves any other screen's untouched - while both screens persist the same way, for one
 * consistent behaviour across screens. The stored value is a layer ID resolved against {@link
 * MapLayerRegistry}'s registered layers, falling back to the registered default when the save holds no pick
 * yet or an ID from an older build no longer registered.
 *
 * <p>The base key is held here rather than supplied, because it is this pick's identity and the same on
 * every screen; what a caller chooses is the screen, and the two compose to the slot. A caller that could
 * supply the key could store this pick beside another's.
 */
public final class PersistedActiveLayerSelection implements ActiveLayerSelection {

    // Save-serialised identity of this pick, before the screen's own segment; frozen once shipped, since
    // renaming it silently resets every existing save under it to the default.
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer";

    // The sector-memory key this pick reads and writes, composed for the screen it was built for.
    private final String memoryKey;

    /**
     * @param memoryScope the screen whose pick this is; its slot is this pick's base key resolved under it
     */
    public PersistedActiveLayerSelection(ScreenMemoryScope memoryScope) {
        this.memoryKey = memoryScope.resolveKeyFor(ACTIVE_LAYER_KEY);
    }

    @Override
    public MapLayer getActiveLayer() {
        var storedId = readStoredLayerId();
        if (storedId == null) {
            return MapLayerRegistry.getDefaultLayer();
        }
        // Stale ID from a build that shipped a layer since removed, or from a mod uninstalled since the
        // save was written: fall back rather than leave the bar pointing at nothing.
        var storedLayer = MapLayerRegistry.resolveLayerById(storedId);

        return storedLayer != null ? storedLayer : MapLayerRegistry.getDefaultLayer();
    }

    @Override
    public void selectLayer(MapLayer layer) {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            // No sector yet means no save to write into; nothing to record until one exists.
            return;
        }
        memory.set(memoryKey, layer.getId());
    }

    // Reads the stored layer ID under this pick's key, or null when the sector is absent or the key was
    // never written - the caller resolves either to the default.
    private String readStoredLayerId() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(memoryKey)) {
            return null;
        }
        return memory.getString(memoryKey);
    }
}
