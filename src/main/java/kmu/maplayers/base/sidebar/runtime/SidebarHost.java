package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.sidebar.SidebarFoldSelection;

import java.util.Set;

/**
 * One screen's binding for the shared map-layer sidebar: it answers when the sidebar is live on that
 * screen, resolves the placement to draw there, owns the panel's transient scroll and collapse state, and
 * routes a key press. The generic {@link SidebarRenderer} and {@link SidebarInput} run against this role,
 * so the same panel draws and routes on the sector map and on the intel screen with only the host differing
 * - one gate, one anchor, one controller per screen.
 *
 * <p>Each host owns its own {@link TabPanelController} and its own {@link SidebarFoldSelection}, so the two
 * screens' panels keep separate scroll and collapse state - and reopen at their own folds - even though
 * they share one layout and one set of layer controls. {@link BaseSidebarHost} carries the plumbing
 * common to every host - including the shortcut key that jumps to a layer, since the panel offers the same
 * tabs wherever it draws - leaving a concrete host only the questions that genuinely differ between
 * screens.
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
     * @return where this host's panel fold is read from and recorded to. A caller offers it the settled fold
     *         each frame the panel draws; whether that outlives the session is the selection's business, not
     *         the caller's
     */
    SidebarFoldSelection getFoldSelection();

    /**
     * Handles a key press while the sidebar is live: a key bound to a layer jumps this screen's pick to it
     * and is consumed, and any other key is left untouched to reach the screen underneath.
     *
     * @param event the key-down event
     */
    void handleKeyPress(InputEventAPI event);

    /**
     * @return a short description of this host's current view state, for the sidebar's view-state log so a
     *         panel gated out or drawn off-screen is diagnosable from the log alone. Prose for whoever reads
     *         that log, not a value to branch on: each host names its own states in its own words, and the
     *         wording is free to change as those states need to say more, so nothing depends on its form
     */
    String describeViewState();
}
