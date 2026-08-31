package kmu.maplayers.base.sidebar;

import java.util.HashMap;
import java.util.Map;

/**
 * The single id a sidebar filter picker's pointer currently rests on, or none - held per scope
 * beside {@link FilterSelection}, so each scope keeps its own and one scope's hover is never read
 * under another. A layer reading it previews what picking that id would spotlight, without picking
 * it.
 *
 * <p>Deliberately not a second {@code FilterSelection}, and the two differences are the whole point
 * of the class:
 *
 * <ul>
 *   <li>Nothing is persisted. A hover is where a pointer happens to be this frame, not a choice
 *       worth carrying into the next session, so there is no save key to freeze and a fresh session
 *       starts with every scope clear.
 *   <li>No refresh signal is raised. {@code FilterSelection}'s signal has the reading layer rebuild
 *       everything it paints, which is far more than pointer motion down a list of rows can carry;
 *       a reader of this slot draws over the paint that is already there.
 * </ul>
 *
 * <p>Like {@code FilterSelection} this holds a bare id and nothing that resolves one: what the id
 * names, and what a preview of it looks like, is the reading layer's concern. The class stays
 * ignorant of what a scope is too, partitioning by an opaque id the caller supplies.
 *
 * <p>Clearing is a caller's obligation at both ends a hover can stop at - the pointer leaving the
 * row, and the panel standing down without a leave ever being reported - since neither is visible
 * from here.
 */
public final class FilterHoverSlot {

    // One live id per scope, keyed by the scope's opaque id. A plain map rather than a persisted
    // slot because the value dies with the session: an entry exists only while a pointer rests on a
    // row, and absence is the resting state every scope starts and ends in.
    private static final Map<String, String> HOVERED_ID_BY_SCOPE_ID = new HashMap<>();

    private FilterHoverSlot() {
    }

    /**
     * @param scopeId the scope whose slot is read
     * @return the id the pointer rests on in that scope, or null when it rests on no row - which is
     *         also the answer for a scope no hover was ever reported for
     */
    public static String getHoveredIdOf(String scopeId) {
        return HOVERED_ID_BY_SCOPE_ID.get(scopeId);
    }

    /**
     * Drops one scope's hover, returning it to resting on no row. A no-op on a scope that already
     * rests there, so a stand-down needs no check of its own.
     *
     * @param scopeId the scope whose slot is cleared
     */
    public static void clearHoveredId(String scopeId) {
        HOVERED_ID_BY_SCOPE_ID.remove(scopeId);
    }

    /**
     * Records the id the pointer now rests on in one scope, replacing whatever it rested on before.
     *
     * @param scopeId   the scope the hover belongs to
     * @param hoveredId the stable id under the pointer; null clears the scope, so a hover channel
     *                  reporting a leave needs no second call to make
     */
    public static void recordHoveredId(String scopeId, String hoveredId) {
        if (hoveredId == null) {
            clearHoveredId(scopeId);
            return;
        }
        HOVERED_ID_BY_SCOPE_ID.put(scopeId, hoveredId);
    }
}
