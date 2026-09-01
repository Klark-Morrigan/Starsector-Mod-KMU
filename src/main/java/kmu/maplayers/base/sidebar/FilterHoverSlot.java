package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.installation.InstalledMachinery;
import kmu.maplayers.base.installation.MapLayerInstallation;

import java.util.HashMap;
import java.util.Map;

/**
 * The single id a sidebar filter picker's pointer currently rests on, or none - held per scope, so
 * each scope keeps its own and one scope's hover is never read under another. A layer reading it
 * previews what picking that id would spotlight, without picking it.
 *
 * <p>Nothing is persisted and no refresh signal is raised, which is the whole of what separates it
 * from {@link FilterSelection}: a hover is where a pointer happens to be this frame, previewed over
 * paint that is already there, where a pick has the reading layer rebuild everything it draws.
 *
 * <p>One sector's, held by that sector's installation rather than for the process. The id is one a
 * sector's own walk offered and is read back against what that sector holds, so a hover left
 * standing when the sector goes would light a set the next sector never produced. Held that way it
 * needs no discard of its own: a load disposes the installation and the hover goes with it.
 *
 * <p>Clearing within a sector's life is still a caller's obligation at both ends a hover can stop at
 * - the pointer leaving the row, and the panel standing down without a leave ever being reported -
 * since neither is visible from here.
 */
public final class FilterHoverSlot implements InstalledMachinery {

    // One live id per scope, keyed by the scope's opaque id. A plain map rather than a persisted
    // slot because the value dies with the sector: an entry exists only while a pointer rests on a
    // row, and absence is the resting state every scope starts and ends in.
    private final Map<String, String> hoveredIdByScopeId = new HashMap<>();

    // Reached through resolveHoverSlotIn, so the only slots that exist are ones an installation
    // holds - and so goes with the sector it was made for.
    FilterHoverSlot() {
    }

    /**
     * The hover slot {@code installation}'s picker reports into and its map preview reads back, made
     * on the first ask and released with the installation holding it.
     *
     * <p>The one way to a slot, so the picker writing a hover and the pass drawing from it cannot
     * end up on two different ones.
     *
     * @param installation the machinery installed on the sector whose picker is being drawn
     * @return that sector's slot
     */
    public static FilterHoverSlot resolveHoverSlotIn(MapLayerInstallation installation) {
        return installation.resolveMachinery(FilterHoverSlot.class, FilterHoverSlot::new);
    }

    /**
     * Drops one scope's hover, returning it to resting on no row. A no-op on a scope that already
     * rests there, so a stand-down needs no check of its own.
     *
     * @param scopeId the scope whose slot is cleared
     */
    public void clearHoveredId(String scopeId) {
        hoveredIdByScopeId.remove(scopeId);
    }

    /**
     * Drops every scope's hover. Holds nothing a collector would not free, so this is about the
     * answer rather than the memory: a caller still holding a slot resolved before the disposal
     * reads no hover rather than the gone sector's.
     */
    @Override
    public void disposeMachinery() {
        hoveredIdByScopeId.clear();
    }

    /**
     * @param scopeId the scope whose slot is read
     * @return the id the pointer rests on in that scope, or null when it rests on no row - which is
     *         also the answer for a scope no hover was ever reported for
     */
    public String getHoveredIdOf(String scopeId) {
        return hoveredIdByScopeId.get(scopeId);
    }

    /**
     * Records the id the pointer now rests on in one scope, replacing whatever it rested on before.
     *
     * @param scopeId   the scope the hover belongs to
     * @param hoveredId the stable id under the pointer; null clears the scope, so a hover channel
     *                  reporting a leave needs no second call to make
     */
    public void recordHoveredId(String scopeId, String hoveredId) {
        if (hoveredId == null) {
            clearHoveredId(scopeId);
            return;
        }
        hoveredIdByScopeId.put(scopeId, hoveredId);
    }
}
