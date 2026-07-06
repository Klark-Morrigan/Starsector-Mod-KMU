package kmu.politicalmap.layer;

import com.fs.starfarer.api.Global;

import java.util.List;

/**
 * The political map's layer registry and the single source of truth for which layer is
 * active. The layer bar composes its tabs from {@link #getLayers()}, the input listener
 * switches the pick with {@link #selectLayer}, and the terrain plugin gates its paint on
 * {@link #isFactionTerritoryActive()} - so all three agree on one selection without sharing
 * state directly.
 *
 * <p>The active pick lives in sector memory under a stable key, so it serialises into the
 * save and survives reload. An untouched save resolves to the faction view, keeping the
 * overlay visible the first time the sector map opens. Registering a further layer is a
 * matter of adding it to {@link #ORDERED}; the bar, its hit-testing, and its hotkey follow.
 */
public final class PoliticalMapLayers {
    // Save-serialised identity of the active pick; frozen once shipped, since renaming it
    // silently resets every existing save to the default.
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer";

    private static final PoliticalMapLayer NO_LAYER = new NoLayer();
    private static final FactionsLayer FACTIONS = new FactionsLayer();

    // Tab order, left to right. No Layer leads so the "show nothing" pick is the first tab.
    private static final List<PoliticalMapLayer> ORDERED = List.of(NO_LAYER, FACTIONS);

    // The pick an untouched save resolves to: the faction view, so the overlay is up the
    // first time the sector map opens rather than blank until the player finds the bar.
    private static final PoliticalMapLayer DEFAULT_LAYER = FACTIONS;

    private PoliticalMapLayers() {
    }

    /** @return the registered layers in tab order, left to right. */
    public static List<PoliticalMapLayer> getLayers() {
        return ORDERED;
    }

    /**
     * @return the active layer, resolved from sector memory; the default when the save holds
     *         no pick yet or an id from an older build no longer registered
     */
    public static PoliticalMapLayer getActiveLayer() {
        var storedId = readStoredLayerId();
        if (storedId == null) {
            return DEFAULT_LAYER;
        }
        for (var layer : ORDERED) {
            if (layer.getId().equals(storedId)) {
                return layer;
            }
        }
        // Stale id from a build that shipped a layer since removed: fall back rather than
        // leave the bar pointing at nothing.
        return DEFAULT_LAYER;
    }

    /** @return whether {@code layer} is the active pick. */
    public static boolean isActive(PoliticalMapLayer layer) {
        // Layers are singletons held here, so identity settles it without an id compare.
        return getActiveLayer() == layer;
    }

    /**
     * @return whether the faction-territory view is active - the gate the terrain plugin
     *         reads to paint or stay dark. Named for the concept, not the class, so the
     *         terrain gate does not reach into which layer instance backs it.
     */
    public static boolean isFactionTerritoryActive() {
        return isActive(FACTIONS);
    }

    /**
     * Records {@code layer} as the active pick in sector memory. A no-op before the sector
     * exists, since there is no save to write into yet.
     */
    public static void selectLayer(PoliticalMapLayer layer) {
        var sector = Global.getSector();
        if (sector == null) {
            return;
        }
        var memory = sector.getMemoryWithoutUpdate();
        if (memory == null) {
            return;
        }
        memory.set(ACTIVE_LAYER_KEY, layer.getId());
    }

    // Reads the stored layer id, or null when the sector is absent or the key was never
    // written - the caller resolves either to the default.
    private static String readStoredLayerId() {
        var sector = Global.getSector();
        if (sector == null) {
            return null;
        }
        var memory = sector.getMemoryWithoutUpdate();
        if (memory == null || !memory.contains(ACTIVE_LAYER_KEY)) {
            return null;
        }
        return memory.getString(ACTIVE_LAYER_KEY);
    }
}
