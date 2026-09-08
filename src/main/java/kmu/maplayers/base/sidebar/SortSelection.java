package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.base.layer.ScreenMemoryScope;

/**
 * The metric a sidebar picker list currently ranks its rows by and the direction that ranking runs in
 * - held per screen and per scope, so each pair keeps its own answer and moving between either
 * neither resets nor cross-reads another's. It holds only the raw stored values - a bare mode key and
 * a bare direction key - and the plumbing to read and write them; what a key means (which comparator
 * or direction it maps to, what it falls back to when unset) is the sort mode's and sort direction's
 * concern, not this class's, so this stays a leaf that names no sort type. The two are stored apart
 * because they change apart: switching mode rewrites both (a new mode resets to its default
 * direction), while flipping rewrites only the direction.
 *
 * <p>Per scope because a mode key is scope-specific: scopes rank their rows by different vocabularies,
 * so one scope's stored key read under another resolves against nothing and silently falls back to
 * that scope's default - a shared slot would make every switch look like a reset. The class stays
 * ignorant of what a scope is, exactly as {@link FilterSelection} does - it partitions storage by an
 * opaque id the caller supplies, never a registry of scopes.
 *
 * <p>Per screen for the reason {@link FilterSelection} is: a ranking is a pick made on one panel, and
 * a player reading one screen's list by distance has said nothing about the other's. The screen
 * arrives as the {@link ScreenMemoryScope} the calling panel was built under and composes last into
 * both keys.
 *
 * <p>Sidebar-only state: the mode is driven solely by a picker's sort selector, never a settings-screen
 * control, so it persists in sector memory (each save keeps its own choice and it survives reload)
 * rather than as a LunaLib field - a LunaLib field would render on a settings tab and an unregistered
 * key would not round-trip.
 *
 * <p>No refresh follows a write: the sort only reorders the picker list, which the sidebar rebuilds
 * from live state every frame, so the next frame re-reads the stored key and re-sorts on its own.
 * Nothing on the map changes, so there is no overlay repaint to request.
 */
public final class SortSelection {

    // Per-scope key prefixes; the scope's own id is appended, and the screen's segment after that, to
    // give one save-serialised slot per screen per scope per half. Both are layer-neutral because
    // every map layer's picker stores through this one class - a prefix naming one layer would have
    // every other layer persisting its sort under that layer's key. A key is a save-serialised
    // identity, not a description of where the class lives: a renamed prefix reads as absent and
    // silently resets every existing save's sort choice back to the default, so both stay frozen in
    // this spelling (as are the scope ids appended to them).
    //
    // Prefix of the chosen sort mode's slot. Absent until the player first picks a mode in that
    // scope, which the read reports as null for the sort mode to default.
    private static final String SELECTED_SORT_MODE_KEY_PREFIX = "$kmu_map_sort_mode_";

    // Prefix of the chosen sort direction's slot. Absent until the player first flips a direction
    // (or picks a mode) in that scope, which the read reports as null for the caller to resolve to
    // the active mode's default direction.
    private static final String SELECTED_SORT_DIRECTION_KEY_PREFIX = "$kmu_map_sort_direction_";

    private SortSelection() {
    }

    /**
     * @param memoryScope the screen whose slot is read
     * @param scopeId     the scope whose slot is read
     * @return that screen's stored sort-mode key in that scope, or null when none is stored (a fresh
     *         save, a scope never sorted in, or a read before the sector exists) - the caller
     *         resolves null to the default mode
     */
    public static String getSortModeKeyOf(ScreenMemoryScope memoryScope, String scopeId) {
        return resolveModeSlot(memoryScope, scopeId).get();
    }

    /**
     * @param memoryScope the screen whose slot is read
     * @param scopeId     the scope whose slot is read
     * @return that screen's stored sort-direction key in that scope, or null when none is stored (a
     *         pair whose sort was never flipped) - the caller resolves null to the active mode's
     *         default direction
     */
    public static String getSortDirectionKeyOf(ScreenMemoryScope memoryScope, String scopeId) {
        return resolveDirectionSlot(memoryScope, scopeId).get();
    }

    /**
     * Persists the chosen sort mode's key in one screen's slot for one scope, so the choice is
     * remembered against that pair alone. A no-op before the sector exists, since there is no save to
     * write into yet. No repaint follows: the picker list re-sorts on the next per-frame body build,
     * and nothing on the map depends on the sort.
     *
     * @param memoryScope the screen whose panel made the pick
     * @param scopeId     the scope the pick belongs to
     * @param modeKey     the save-stable key of the mode to sort by
     */
    public static void selectSortMode(
            ScreenMemoryScope memoryScope,
            String scopeId,
            String modeKey) {

        resolveModeSlot(memoryScope, scopeId).set(modeKey);
    }

    /**
     * Persists the chosen sort direction's key in one screen's slot for one scope. A no-op before the
     * sector exists. Like the mode write it fires no repaint - only the picker list re-orders, on the
     * next body build.
     *
     * @param memoryScope  the screen whose panel made the flip
     * @param scopeId      the scope the flip belongs to
     * @param directionKey the save-stable key of the direction to sort in
     */
    public static void selectSortDirection(
            ScreenMemoryScope memoryScope,
            String scopeId,
            String directionKey) {

        resolveDirectionSlot(memoryScope, scopeId).set(directionKey);
    }

    // The sector-memory slot holding one screen's sort direction in one scope, keyed by the scope's
    // id under the screen's segment. A fresh wrapper per call - the wrapper only holds its key, the
    // value lives in sector memory - so no per-pair instance has to be cached here.
    private static SectorMemoryString resolveDirectionSlot(
            ScreenMemoryScope memoryScope,
            String scopeId) {

        return new SectorMemoryString(
            memoryScope.resolveKeyFor(SELECTED_SORT_DIRECTION_KEY_PREFIX + scopeId));
    }

    // The sector-memory slot holding one screen's sort mode in one scope, keyed the same way. Built
    // per call for the same reason the direction slot is.
    private static SectorMemoryString resolveModeSlot(
            ScreenMemoryScope memoryScope,
            String scopeId) {

        return new SectorMemoryString(
            memoryScope.resolveKeyFor(SELECTED_SORT_MODE_KEY_PREFIX + scopeId));
    }
}
