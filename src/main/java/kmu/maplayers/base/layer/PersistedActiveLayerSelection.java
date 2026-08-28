package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

/**
 * A screen's active-layer pick persisted in sector memory under one key, so the pick survives reload. Each
 * screen holds its own instance under its own key, so the picks stay independent - a switch on one screen
 * writes only its key and leaves any other screen's untouched - while both screens persist the same way,
 * for one consistent behaviour across screens. The stored value is a layer id resolved against {@link
 * MapLayerRegistry}'s registered layers, falling back to the registered default when the save holds no pick
 * yet or an id from an older build no longer registered.
 */
public final class PersistedActiveLayerSelection implements ActiveLayerSelection {

    // The sector-memory key this pick reads and writes. Frozen by the caller once shipped: renaming it
    // silently resets every existing save under it to the default.
    private final String memoryKey;

    /**
     * @param memoryKey the sector-memory key this selection persists its pick under; must stay stable
     *                  across releases, since a rename resets the saved pick to the default
     */
    public PersistedActiveLayerSelection(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    @Override
    public MapLayer getActiveLayer() {
        var storedId = readStoredLayerId();
        if (storedId == null) {
            return MapLayerRegistry.getDefaultLayer();
        }
        for (var layer : MapLayerRegistry.getLayers()) {
            if (layer.getId().equals(storedId)) {
                return layer;
            }
        }
        // Stale id from a build that shipped a layer since removed: fall back rather than leave the bar
        // pointing at nothing.
        return MapLayerRegistry.getDefaultLayer();
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

    // Reads the stored layer id under this pick's key, or null when the sector is absent or the key was
    // never written - the caller resolves either to the default.
    private String readStoredLayerId() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(memoryKey)) {
            return null;
        }
        return memory.getString(memoryKey);
    }
}
