package kmu.maplayers.base.layer;

import kmlib.starsector.ui.intel.IntelScreenView;

import java.util.List;

/**
 * The map-layer registry and the holder of each screen's active-layer pick. The layer bar
 * composes its tabs from {@link #getLayers()}, a screen's own selection ({@link #getMapSelection()}
 * or {@link #getIntelSelection()}) stores its pick, and each layer's own state reads {@link #isActive}
 * to decide whether it is the one to draw - so all agree on the live selection without sharing
 * state directly. Each screen's pick is its own {@link PersistedActiveLayerSelection} under its own
 * key, so a switch on one screen survives reload without moving the other's; the registry holds both
 * so their keys and the one-time legacy migration live in a single place.
 *
 * <p>Because the picks are per-screen, "which pick is live" is a per-frame question rather than a
 * fixed answer: the same map widget draws on the sector map and inside the intel screen's visor, so an
 * overlay reading one fixed screen's pick would paint the sector map's choice onto the intel screen
 * and ignore the tab the player is looking at. {@link #isActive} settles it by reading which screen is
 * up, through the intel-screen seam {@link #registerIntelScreen} supplies.
 *
 * <p>This is the feature-agnostic framework half: it knows nothing of any concrete layer.
 * The set of layers and the default pick are supplied once at startup by a composition root
 * through {@link #registerLayers}, so a new view is added by registering it rather than by
 * editing this class. Each pick lives in sector memory under a stable key, so it serialises into
 * the save and survives reload; an untouched save resolves to the registered default. A pre-split
 * save stored one shared pick under an un-suffixed key; {@link #migrateLegacyActiveLayerKey} fans
 * that into both screens' keys on load and clears it, so no leftover key lingers.
 */
public final class MapLayerRegistry {
    // Save-serialised identity of each screen's active pick; frozen once shipped, since renaming one
    // silently resets every existing save under it to the default.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The un-suffixed key a pre-split save stored the single shared pick under, before the map and
    // intel screens each took their own key. Fanned into both screens' keys on load, then cleared.
    private static final String LEGACY_ACTIVE_LAYER_KEY = "$kmu_political_active_layer";

    // Each screen's pick, persisted under its own frozen key. The map host draws through the map
    // selection and the overlay follows it; the intel host draws through the intel selection. Held here
    // so both keys and the legacy migration that seeds them sit in one place.
    private static final PersistedActiveLayerSelection MAP_SELECTION =
            new PersistedActiveLayerSelection(MAP_ACTIVE_LAYER_KEY);
    private static final PersistedActiveLayerSelection INTEL_SELECTION =
            new PersistedActiveLayerSelection(INTEL_ACTIVE_LAYER_KEY);

    // The registered layers, in tab order, and the pick an untouched save resolves to. Empty
    // until a composition root registers them at startup, before any sector map can open.
    private static List<MapLayer> orderedLayers = List.of();
    private static MapLayer defaultLayer;

    // Reads whether the intel screen is the one up, which is what decides whose pick is live. Null
    // until the composition root supplies it, which resolves every read to the map screen's pick - the
    // answer a registry with no screen wired yet should give, since the sector map is the overlay's home.
    private static IntelScreenView intelScreen;

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

    /**
     * Records the intel-screen seam that tells the registry which screen is up, so {@link #isActive}
     * can answer from the pick belonging to the screen the player is looking at. Called by the
     * composition root, the one place a concrete screen binding is named. Only the intel screen is
     * asked: the two screens are never up together, so "not the intel screen" is the sector map.
     *
     * @param intelScreen reads whether the intel screen is the one showing
     */
    public static void registerIntelScreen(IntelScreenView intelScreen) {
        MapLayerRegistry.intelScreen = intelScreen;
    }

    /** @return the registered layers in tab order, left to right. */
    public static List<MapLayer> getLayers() {
        return orderedLayers;
    }

    /** @return the pick an untouched save resolves to, the fallback for an absent or stale stored pick. */
    public static MapLayer getDefaultLayer() {
        return defaultLayer;
    }

    /**
     * @return the map screen's active-layer pick, for the map host to draw through and switch, so the
     *         host and the overlay read one selection rather than each resolving their own
     */
    public static ActiveLayerSelection getMapSelection() {
        return MAP_SELECTION;
    }

    /**
     * @return the intel screen's active-layer pick, its own selection under its own key, so the intel
     *         sidebar's tab is independent of the map screen's and survives reload on its own
     */
    public static ActiveLayerSelection getIntelSelection() {
        return INTEL_SELECTION;
    }

    /**
     * @return the active pick of the screen showing this frame, or null before a composition root has
     *         registered any layers. This is what the map surface dispatches its render pass through,
     *         so the paint follows the tab the player is looking at without the surface naming a
     *         layer; it follows the live screen rather than one fixed screen, so the intel screen's
     *         own tab governs what paints there while the sector map keeps its own pick
     */
    public static MapLayer getActiveLayer() {
        return resolveLiveSelection().getActiveLayer();
    }

    /**
     * @return whether {@code layer} is the active pick of the screen showing this frame - the gate a
     *         layer's own state reads to decide whether it is the one in play
     */
    public static boolean isActive(MapLayer layer) {
        // Layers are singletons, so identity settles it without an id compare.
        return getActiveLayer() == layer;
    }

    /**
     * Fans a pre-split save's shared active-layer pick from the un-suffixed legacy key into both screens'
     * keys, then clears the legacy key, so an upgraded save keeps its pick on both screens (each then
     * diverging independently) and no un-suffixed key lingers. A no-op on a save with no legacy key. Call
     * once on game load, before {@link #migrateStoredLayerId} so a layer-id rewrite lands on the
     * carried-over value.
     */
    public static void migrateLegacyActiveLayerKey() {
        PersistedActiveLayerSelection.migrateLegacyKeyInto(
                LEGACY_ACTIVE_LAYER_KEY,
                MAP_SELECTION,
                INTEL_SELECTION);
    }

    /**
     * Rewrites each screen's stored pick from a layer's former id to its current one, so a save written
     * before a layer's id was renamed tracks the current id in place rather than falling back to the
     * default and leaving the stale id in the save. Both screens' keys are rewritten, so a pick fanned
     * into both by {@link #migrateLegacyActiveLayerKey} tracks the rename on each. The mechanism only -
     * the caller (which owns the concrete rename) supplies the ids, so this framework stays agnostic to
     * which layers exist. A no-op before the sector exists or when a stored pick is not {@code legacyId}.
     *
     * @param legacyId  the id the layer stored before it was renamed
     * @param currentId the layer's current id to rewrite the stored pick to
     */
    public static void migrateStoredLayerId(String legacyId, String currentId) {
        MAP_SELECTION.migrateStoredLayerId(legacyId, currentId);
        INTEL_SELECTION.migrateStoredLayerId(legacyId, currentId);
    }

    // The pick of the screen that is up: the intel screen's while its tab is the one open, the map
    // screen's otherwise. The map screen is the fallback because the sector map is the overlay's home
    // surface, so a read taken elsewhere - or before the composition root has wired the seam - lands on
    // the pick that surface has always followed.
    private static ActiveLayerSelection resolveLiveSelection() {
        return intelScreen != null && intelScreen.isIntelTabOpen()
                ? INTEL_SELECTION
                : MAP_SELECTION;
    }
}
