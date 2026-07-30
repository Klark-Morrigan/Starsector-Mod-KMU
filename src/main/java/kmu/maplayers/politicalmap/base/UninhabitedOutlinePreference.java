package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.maplayers.base.refresh.MapLayerRefresh;

/**
 * Whether uninhabited systems draw their outline, persisted per save. Off by default, so only
 * faction-held, independent, and decivilised systems draw until the player asks for the unowned ones
 * too. Layer-agnostic: the outline is a property of the map's factionless category, which every view
 * paints the same way.
 *
 * <p>Sidebar-only: the outline is driven solely by the overlay's uninhabited-systems checkbox, never a
 * settings-screen control, so it persists in sector memory (each save keeps its own choice and it
 * survives reload) rather than as a LunaLib field - a LunaLib field would render on a settings tab,
 * duplicating the sidebar checkbox, and an unregistered key would not round-trip. The outline's
 * supplementary tuning - the opacity and width it strokes at - stays on the settings screen, since
 * those are knobs rather than the toggle itself.
 *
 * <p>The flip fires a refresh: whether the category draws at all is baked into the drawables at
 * rebuild, so a flip has to invalidate them to show.
 */
public final class UninhabitedOutlinePreference {
    // Save-serialised key of the toggle; frozen once shipped, since renaming it silently resets every
    // existing save's choice back to off.
    private static final SectorMemoryFlag isOutlineDrawn =
            new SectorMemoryFlag("$kmu_political_uninhabited_outline", false);

    private UninhabitedOutlinePreference() {
    }

    /**
     * @return whether uninhabited systems draw their outline; false before a save exists or when the
     *         toggle was never set
     */
    public static boolean isOutlineDrawn() {
        return isOutlineDrawn.isSet();
    }

    /**
     * Persists whether uninhabited systems draw their outline in this save and restyles the overlay so
     * the flip shows at once. A no-op before the sector exists, since there is no save to write into
     * yet.
     *
     * @param shouldDrawOutline the new state, as the sidebar checkbox reads it
     */
    public static void setOutlineDrawn(boolean shouldDrawOutline) {
        // Repaint only on a real write: before the sector exists the write no-ops and reports no
        // write, so nothing bumps a revision no overlay would read. The refresh stands in for
        // settingsRevision, which this sidebar-only toggle never moves since it is not a LunaLib
        // field.
        if (isOutlineDrawn.set(shouldDrawOutline)) {
            MapLayerRefresh.requestMapStyleRefresh();
        }
    }
}
