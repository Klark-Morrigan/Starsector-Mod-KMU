package kmu.maplayers.politicalmap.factions;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.layer.MapLayerRegistry;

/**
 * The faction overlay's on/off state - the Political Map tab's in-body toggle - and the gate
 * the faction terrain plugin reads to draw or stay dark. This is the faction layer's own
 * state, kept off the framework registry so the registry stays agnostic to which views exist:
 * the registry owns only which tab is active, while whether that tab's paint is switched on
 * lives here with the layer it belongs to.
 *
 * <p>The flag persists in sector memory under a stable key, so it serialises into the save and
 * survives reload. It defaults to on before any save has recorded a pick, so the political map
 * is up the first time the sector map opens rather than dark until the player finds the toggle.
 */
public final class FactionOverlayState {
    // Save-serialised on/off state of the faction overlay, the in-body toggle's memory; frozen
    // once shipped, since renaming it silently resets every existing save to the default.
    private static final String FACTION_OVERLAY_KEY = "$kmu_political_faction_overlay_on";

    // The overlay's state before any save has recorded a pick: on, so the political map is up
    // the first time the sector map opens rather than dark until the player finds the toggle.
    private static final boolean DEFAULT_FACTION_OVERLAY_ON = true;

    private FactionOverlayState() {
    }

    /**
     * @return whether the faction-territory view paints - the gate the terrain plugin reads to
     *         draw or stay dark. True only when the faction view is the active tab AND its
     *         in-body overlay toggle is on, so the tab can stay selected (its control panel
     *         open) while the player switches the paint off.
     */
    public static boolean isFactionTerritoryActive() {
        return MapLayerRegistry.isActive(FactionsLayer.INSTANCE) && isFactionOverlayEnabled();
    }

    /**
     * @return whether the faction overlay is switched on - the in-body toggle's state, read
     *         from sector memory. Defaults to on before any save has recorded a pick, so the
     *         overlay is up the first time the map opens.
     */
    public static boolean isFactionOverlayEnabled() {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(FACTION_OVERLAY_KEY)) {
            return DEFAULT_FACTION_OVERLAY_ON;
        }
        return memory.getBoolean(FACTION_OVERLAY_KEY);
    }

    /**
     * Records whether the faction overlay is on in sector memory. A no-op before the sector
     * exists, since there is no save to write into yet.
     */
    public static void setFactionOverlayEnabled(boolean isEnabled) {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        memory.set(FACTION_OVERLAY_KEY, isEnabled);
    }

    /** Flips the faction overlay between on and off, persisting the new state. */
    public static void toggleFactionOverlay() {
        setFactionOverlayEnabled(!isFactionOverlayEnabled());
    }
}
