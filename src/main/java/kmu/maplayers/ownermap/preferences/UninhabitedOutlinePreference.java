package kmu.maplayers.ownermap.preferences;

import kmlib.starsector.memory.AddressedMemoryFlag;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

/**
 * Whether uninhabited systems draw their outline, persisted per save and per screen. Off by default, so
 * only faction-held, independent, and decivilised systems draw until the player asks for the unowned ones
 * too. View-agnostic: the outline is a property of the map's factionless category, which every view
 * paints the same way.
 *
 * <p>One per layer, over a key the layer names, so two owner-painted layers offering the checkbox
 * store their choices apart.
 *
 * <p>Per screen because it settles how much of the sector one panel shows, and the two panels are looked
 * at for different things.
 *
 * <p>Sidebar-only, driven by the overlay's uninhabited-systems checkbox, for the reason
 * {@link OwnerMapBodyPreferences} gives; the opacity and width it strokes at stay on the settings
 * screen.
 *
 * <p>The flip fires a refresh: whether the category draws at all is baked into the drawables at
 * rebuild, so a flip has to invalidate them to show.
 */
public final class UninhabitedOutlinePreference {

    // Off while untouched: only the settled sector draws until the player asks for the rest.
    private static final boolean DEFAULT_IS_OUTLINE_DRAWN = false;

    // The choice, held once per screen under the layer's key.
    private final AddressedMemoryFlag isOutlineDrawn;

    /**
     * @param outlineKey the sector-memory key the choice is stored under, before each screen's own
     *                   segment; save-serialised, so a layer freezes it once shipped
     */
    public UninhabitedOutlinePreference(String outlineKey) {
        this.isOutlineDrawn = new AddressedMemoryFlag(outlineKey, DEFAULT_IS_OUTLINE_DRAWN);
    }

    /**
     * @param memoryScope the screen whose choice is read
     * @return whether that screen draws the outline of uninhabited systems; false before a save exists
     *         or when the toggle was never set there
     */
    public boolean isOutlineDrawn(ScreenMemoryScope memoryScope) {
        return isOutlineDrawn.isSet(memoryScope);
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
    public void setOutlineDrawn(
            ScreenMemoryScope memoryScope,
            boolean shouldDrawOutline,
            MapLayerRefreshBoard board) {

        // Repaint only on a real write (see OwnerMapBodyPreferences).
        if (isOutlineDrawn.set(memoryScope, shouldDrawOutline)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
        }
    }
}
