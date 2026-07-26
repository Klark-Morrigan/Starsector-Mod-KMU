package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import java.util.Set;

/**
 * One screen's binding for the shared political-map sidebar: it answers when the sidebar is live on that
 * screen, resolves the placement to draw there, owns the panel's transient scroll and collapse state, and
 * routes a key press. The generic {@link SidebarRenderer} and {@link SidebarInput} run against this role,
 * so the same panel draws and routes on the sector map and on the intel screen with only the host differing
 * - one gate, one anchor, one controller per screen.
 *
 * <p>Each host owns its own {@link TabPanelController}, so the two screens' panels keep separate scroll and
 * collapse state (the on-map panel opens expanded, the intel panel docked) even though they share one
 * layout and one set of political-map controls. What a key press means is the host's too: the on-map host
 * jumps to a layer by its shortcut, while a host with no keyboard role ignores it.
 */
public interface SidebarHost {

    /**
     * @return whether the sidebar is live on this host's screen this frame - the sole gate on drawing and
     *         routing; false leaves the screen untouched
     */
    boolean isOverlayShowing();

    /**
     * @return the placement to draw and hit-test this frame, or {@code null} when there is nothing to draw
     *         (the tab font cannot load, or the anchor is gone); the caller then draws and consumes nothing
     */
    TabPanelPlacement resolvePlacement();

    /**
     * @param placement the placement resolved this frame, so a host can decide the edges from the box's
     *                  laid-out position - an edge sitting flush against another panel is dropped
     * @return which edges of the panel's frame to stroke this frame; a host framing the whole box returns
     *         {@link BoxEdge#ALL}, while one drawn flush against another panel omits the shared edges so
     *         its border does not double that panel's own frame
     */
    Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement);

    /**
     * @return this host's panel controller, holding the panel's transient scroll and collapse state across
     *         frames
     */
    TabPanelController getController();

    /**
     * Handles a key press while the sidebar is live, for a host with a keyboard role (the on-map host jumps
     * to a layer by its shortcut); a host with none leaves the event untouched.
     *
     * @param event the key-down event
     */
    void handleKeyPress(InputEventAPI event);

    /**
     * @return a short description of this host's current view state, for the sidebar's view-state log so a
     *         panel gated out or drawn off-screen is diagnosable from the log alone
     */
    String describeViewState();
}
