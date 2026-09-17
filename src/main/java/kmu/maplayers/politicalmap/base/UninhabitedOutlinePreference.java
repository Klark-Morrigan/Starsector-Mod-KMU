package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.AddressedMemoryFlag;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

/**
 * Whether uninhabited systems draw their outline, persisted per save and per screen. Off by default, so
 * only faction-held, independent, and decivilised systems draw until the player asks for the unowned ones
 * too. Layer-agnostic: the outline is a property of the map's factionless category, which every view
 * paints the same way.
 *
 * <p>Per screen because it settles how much of the sector one panel shows, and the two panels are looked
 * at for different things.
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

    // Off while untouched: only the settled sector draws until the player asks for the rest.
    private static final AddressedMemoryFlag IS_OUTLINE_DRAWN =
        new AddressedMemoryFlag("$kmu_political_uninhabited_outline", false);

    private UninhabitedOutlinePreference() {
    }

    /**
     * @param memoryScope the screen whose choice is read
     * @return whether that screen draws the outline of uninhabited systems; false before a save exists
     *         or when the toggle was never set there
     */
    public static boolean isOutlineDrawn(ScreenMemoryScope memoryScope) {
        return IS_OUTLINE_DRAWN.isSet(memoryScope);
    }

    /**
     * Persists whether uninhabited systems draw their outline in this save, against the screen the box
     * was flipped on, and restyles the overlay so the flip shows at once. A no-op before the sector
     * exists, since there is no save to write into yet.
     *
     * @param memoryScope       the screen whose panel was flipped
     * @param shouldDrawOutline the new state, as the sidebar checkbox reads it
     * @param board             the refresh board of the sector whose checkbox was flipped, raised on
     *                          so that sector's overlay restyles
     */
    public static void setOutlineDrawn(
            ScreenMemoryScope memoryScope,
            boolean shouldDrawOutline,
            MapLayerRefreshBoard board) {

        // Repaint only on a real write: before the sector exists the write no-ops and reports no
        // write, so nothing bumps a revision no overlay would read. The refresh stands in for
        // settingsRevision, which this sidebar-only toggle never moves since it is not a LunaLib
        // field.
        if (IS_OUTLINE_DRAWN.set(memoryScope, shouldDrawOutline)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
        }
    }
}
