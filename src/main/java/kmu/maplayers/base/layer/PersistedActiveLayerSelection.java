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

    /**
     * Carries a pick stored under {@code legacyKey} into every one of {@code targets} that holds no pick
     * yet, then clears the legacy key once, so an upgraded save seeds each screen's own new key from the
     * single old key and no leftover key lingers. A target that already holds a pick keeps it, so a pick
     * written under a new key wins over the stale shared one. Reading the legacy value once and clearing
     * it only after every target has adopted is what lets one old key fan out into several new ones - a
     * per-target clear would starve the targets that ran after the first. A no-op before the sector
     * exists or when the legacy key holds nothing.
     *
     * @param legacyKey the former shared key to carry over and then clear
     * @param targets   the per-screen selections to seed, each adopting only into its own empty key
     */
    public static void migrateLegacyKeyInto(
            String legacyKey,
            PersistedActiveLayerSelection... targets) {

        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(legacyKey)) {
            return;
        }
        var legacyValue = memory.getString(legacyKey);
        for (var target : targets) {
            if (!memory.contains(target.memoryKey)) {
                memory.set(target.memoryKey, legacyValue);
            }
        }
        memory.unset(legacyKey);
    }

    /**
     * Rewrites the stored pick from a layer's former id to its current one, so a save written before a
     * layer's id was renamed tracks the current id in place rather than falling back to the default and
     * leaving the stale id in the save. The mechanism only - the caller (which owns the concrete rename)
     * supplies the ids. A no-op before the sector exists or when the stored pick is not {@code legacyId}.
     *
     * @param legacyId  the id the layer stored before it was renamed
     * @param currentId the layer's current id to rewrite the stored pick to
     */
    public void migrateStoredLayerId(String legacyId, String currentId) {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(memoryKey)) {
            return;
        }
        if (legacyId.equals(memory.getString(memoryKey))) {
            memory.set(memoryKey, currentId);
        }
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
