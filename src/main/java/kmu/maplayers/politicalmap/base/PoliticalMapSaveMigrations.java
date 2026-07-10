package kmu.maplayers.politicalmap.base;

/**
 * The single place that sequences every one-time self-heal the political-map overlay runs against a
 * loaded save, so the composition root calls one seam instead of hand-listing each migration and the
 * full set of heals stays auditable in one spot. Each heal's logic lives on the class that privately
 * owns the state it repairs - this only orders the calls, naming no memory key of its own, so a class
 * stays the sole authority on how its own state migrates.
 *
 * <p>Order matters only where one heal reads state another writes; the current heals are independent,
 * so they run in a stable, readable order. Every heal is itself a no-op on a save that needs no
 * repair (already migrated, or written after the rename), so running them all on every load is safe.
 */
public final class PoliticalMapSaveMigrations {

    private PoliticalMapSaveMigrations() {
    }

    /**
     * Runs every political-map save self-heal once against the loaded save: carry a pre-view save's
     * faction-overlay boolean into the active-view selection, rewrite the political-map tab's former
     * id to its current one, and carry the recede toggles from their former alliance-view keys to the
     * shared keys. Each is a no-op when its state needs no repair. Call once on game load.
     */
    public static void healLoadedSave() {
        PoliticalMapViewRegistry.migrateLegacyOverlaySelection();
        PoliticalMapLayer.migrateLegacyStoredId();
        RecedePreferences.migrateLegacyKeys();
    }
}
