package kmu.maplayers.politicalmap.base;

import kmu.maplayers.base.layer.MapLayerRegistry;

/**
 * The single place that sequences every one-time self-heal the political-map overlay runs against a
 * loaded save, so the composition root calls one seam instead of hand-listing each migration and the
 * full set of heals stays auditable in one spot. Each heal's logic lives on the class that privately
 * owns the state it repairs - this only orders the calls, naming no memory key of its own, so a class
 * stays the sole authority on how its own state migrates.
 *
 * <p>Order matters where one heal reads state another settles: the overlay-selection heal runs before
 * the filter heal, since the filter heal judges the stored bloc against the active view and that view
 * must be carried over from a pre-view save first. The rest are independent and run in a stable,
 * readable order. Every heal is itself a no-op on a save that needs no repair (already migrated, or
 * written after the rename), so running them all on every load is safe.
 */
public final class PoliticalMapSaveMigrations {

    private PoliticalMapSaveMigrations() {
    }

    /**
     * Runs every political-map save self-heal once against the loaded save: carry a pre-view save's
     * faction-overlay boolean into the active-view selection, carry a pre-split save's shared
     * active-layer pick from the un-suffixed key into the map key, rewrite the political-map tab's
     * former id to its current one, carry the recede toggles from their former alliance-view keys to
     * the shared keys and then on into the filter recede set, carry a pre-per-view save's single shared
     * spotlight into the active view's slot, and clear a spotlight selection the active view no longer
     * offers. Each is a no-op when its state needs no repair. Order matters here: the overlay-selection
     * heal runs first so the active view is settled before the spotlight migration and heal read it, the
     * active-layer key heal runs before the layer-id rewrite so the id rewrite lands on the carried-over
     * value, the spotlight migration runs before the heal so a carried-over choice is validated in the
     * same load, and the recede legacy-key heal runs before the split heal so an oldest-spelling choice
     * reaches the filter keys in one load. Call once on game load.
     */
    public static void healLoadedSave() {
        PoliticalMapViewRegistry.migrateLegacyOverlaySelection();
        MapLayerRegistry.migrateLegacyActiveLayerKey();
        PoliticalMapLayer.migrateLegacyStoredId();
        RecedePreferences.migrateLegacyKeys();
        RecedePreferences.migrateSharedKeysIntoFilterSet();
        FilterSelectionHeal.migrateLegacySharedSelectionToActiveView();
        FilterSelectionHeal.healStaleSelectionAgainstActiveView();
    }
}
