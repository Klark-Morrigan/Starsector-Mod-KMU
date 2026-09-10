package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;

import java.util.HashMap;
import java.util.Map;

/**
 * The single id a sidebar filter picker's pointer currently rests on, or none - held per
 * {@link PickerScope}, so each mod's every list keeps its own and one list's hover is never read
 * under another. A layer reading it previews what picking that id would spotlight, without picking
 * it.
 *
 * <p>Nothing is persisted and no refresh signal is raised, which is the whole of what separates it
 * from {@link FilterSelection}: a hover is where a pointer happens to be this frame, previewed over
 * paint that is already there, where a pick has the reading layer rebuild everything it draws.
 *
 * <p>One sector's, held by that sector's machinery rather than for the process. The id is one a
 * sector's own walk offered and is read back against what that sector holds, so a hover left
 * standing when the sector goes would light a set the next sector never produced. Held that way it
 * needs no discard of its own: a load disposes the machinery and the hover goes with it.
 *
 * <p>Clearing within a sector's life is still a caller's obligation at both ends a hover can stop at
 * - the pointer leaving the row, and the panel standing down without a leave ever being reported -
 * since neither is visible from here.
 */
public final class FilterHoverSlot implements InstalledMachinery {

    // One live id per picker, keyed by the scope value rather than by a composed string: nothing
    // here is serialised, so there is no key to spell and no chance of two holders spelling it
    // differently. A plain map because the value dies with the sector - an entry exists only while a
    // pointer rests on a row, and absence is the resting state every list starts and ends in.
    private final Map<PickerScope, String> hoveredIdByPickerScope = new HashMap<>();

    // Reached through resolveHoverSlotIn, so the only slots that exist are ones machinery
    // holds - and so goes with the sector it was made for.
    FilterHoverSlot() {
    }

    /**
     * The hover slot {@code machinery}'s picker reports into and its map preview reads back, made
     * on the first ask and released with the machinery holding it.
     *
     * <p>The one way to a slot, so the picker writing a hover and the pass drawing from it cannot
     * end up on two different ones.
     *
     * @param machinery the machinery installed on the sector whose picker is being drawn
     * @return that sector's slot
     */
    public static FilterHoverSlot resolveHoverSlotIn(SectorMapMachinery machinery) {
        return machinery.resolveMachinery(FilterHoverSlot.class, FilterHoverSlot::new);
    }

    /**
     * Drops one picker's hover, returning it to resting on no row. A no-op on a picker that already
     * rests there, so a stand-down needs no check of its own.
     *
     * @param pickerScope the list whose hover is cleared
     */
    public void clearHoveredId(PickerScope pickerScope) {
        hoveredIdByPickerScope.remove(pickerScope);
    }

    /**
     * Drops every picker's hover. Holds nothing a collector would not free, so this is about the
     * answer rather than the memory: a caller still holding a slot resolved before the disposal
     * reads no hover rather than the gone sector's.
     */
    @Override
    public void disposeMachinery() {
        hoveredIdByPickerScope.clear();
    }

    /**
     * @param pickerScope the list whose hover is read
     * @return the id the pointer rests on in that list, or null when it rests on no row - which is
     *         also the answer for a list no hover was ever reported for
     */
    public String getHoveredIdOf(PickerScope pickerScope) {
        return hoveredIdByPickerScope.get(pickerScope);
    }

    /**
     * Records the id the pointer now rests on in one picker's list, replacing whatever it rested on
     * before.
     *
     * @param pickerScope the list the hover belongs to
     * @param hoveredId   the stable id under the pointer; null clears the list, so a hover channel
     *                    reporting a leave needs no second call to make
     */
    public void recordHoveredId(PickerScope pickerScope, String hoveredId) {
        if (hoveredId == null) {
            clearHoveredId(pickerScope);
            return;
        }
        hoveredIdByPickerScope.put(pickerScope, hoveredId);
    }
}
