package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

/**
 * Whether uninhabited systems draw their outline, persisted per save and per screen. Off by default, so
 * only faction-held, independent, and decivilised systems draw until the player asks for the unowned ones
 * too. Layer-agnostic: the outline is a property of the map's factionless category, which every view
 * paints the same way.
 *
 * <p>Per screen because the outline is how much of the sector the player wants shown on one panel: the
 * two screens are looked at for different things, so asking for the unowned systems on one is not asking
 * for them on the other. The screen arrives as a {@link ScreenMemoryScope} on every call rather than being
 * resolved here, so the checkbox writes the panel it was placed on and a bake reads the screen it is
 * painting for.
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

    // The base key the screen's segment composes onto; frozen once shipped, since renaming it silently
    // resets every existing save's choice back to off.
    private static final String OUTLINE_DRAWN_KEY = "$kmu_political_uninhabited_outline";

    // What the toggle reads as while its key is absent: only the settled sector draws until the player
    // asks for the rest.
    private static final boolean OUTLINE_DRAWN_DEFAULT = false;

    private UninhabitedOutlinePreference() {
    }

    /**
     * @param memoryScope the screen whose choice is read
     * @return whether that screen draws the outline of uninhabited systems; false before a save exists
     *         or when the toggle was never set there
     */
    public static boolean isOutlineDrawn(ScreenMemoryScope memoryScope) {
        return resolveSlot(memoryScope).isSet();
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
        if (resolveSlot(memoryScope).set(shouldDrawOutline)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
        }
    }

    // The sector-memory slot holding one screen's outline toggle. A fresh wrapper per call - the wrapper
    // only holds its key and default, the value lives in sector memory - so no per-screen instance has to
    // be cached here.
    private static SectorMemoryFlag resolveSlot(ScreenMemoryScope memoryScope) {
        return new SectorMemoryFlag(
            memoryScope.resolveKeyFor(OUTLINE_DRAWN_KEY),
            OUTLINE_DRAWN_DEFAULT);
    }
}
