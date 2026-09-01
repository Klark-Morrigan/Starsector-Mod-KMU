package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.sidebar.SidebarFoldSelection;

import java.util.Set;

/**
 * One screen's binding for the shared map-layer sidebar: it answers when the sidebar is live on that
 * screen, resolves the placement to draw there, says what its panel looks like, owns the panel's transient
 * scroll and collapse state, and routes a key press. The generic {@link SidebarRenderer} and
 * {@link SidebarInput} run against this role, so the same panel draws and routes on the sector map and on
 * the intel screen with only the host differing - one gate, one anchor, one look, one controller per
 * screen.
 *
 * <p>The look is the host's rather than the renderer's because the two screens' panels sit in different
 * company: one floats free on the map, the other overlays the intel screen's visor amid that screen's own
 * chrome, and each should read as part of what surrounds it. A renderer holding both would have to name
 * the screens to tell them apart, which is exactly the branch this role exists to remove - a third screen
 * would then be a renderer change rather than a host.
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
     *         routing; false leaves the screen untouched, whether because this host's screen is not up or
     *         because something else has claimed the screen and the input the panel would otherwise take
     */
    boolean isOverlayShowing();

    /**
     * How strongly the panel should paint on this host's screen this frame - the draw's counterpart to
     * the crisp gate above, which stays what input and hit-testing answer to.
     *
     * <p>The two part company only while something is claiming the screen with a fade of its own. Input
     * has to stand down the moment such a claimant appears, or the panel goes on taking clicks meant for
     * it; the paint has to follow it down, or the panel cuts out against a backdrop that is still
     * darkening. Everything else moves them together - a screen that is not up paints nothing and routes
     * nothing, and a claimant that snaps takes both at once.
     *
     * @return the panel's alpha this frame, 0..1: 0 leaves the screen untouched, and anything above it
     *         draws, whether or not the gate above still admits input
     */
    float resolveOverlayFade();

    /**
     * @return the placement to draw and hit-test this frame, or {@code null} when there is nothing to draw
     *         (the tab font cannot load, or the anchor is gone); the caller then draws and consumes nothing
     */
    TabPanelPlacement resolvePlacement();

    /**
     * @return the look this host's panel is painted in - its fills, accents, frame colour, faces, and the
     *         tab style its band was laid out with, which a host must build the same way for both passes:
     *         a row painted from a style the layout never snapped to draws its tabs outside their own
     *         band. Resolved fresh each frame rather than held, because every shade in it reads the
     *         running game's colours and the player's live settings; nothing of it is persisted
     */
    WidgetStyle resolveWidgetStyle();

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
     * Opens this host's panel at the fold the loaded save was left at. Call once on game load: a host is a
     * process-lifetime singleton built long before any sector exists, so its construction cannot read the
     * save and only a per-load reseed can - which is also what stops the previous save's fold leaking into
     * the next one loaded in the same run.
     *
     * <p>On the role rather than on an implementation because the load-time pass over the hosts is the same
     * pass that registers their listeners: a host that could not be reseeded through this interface would
     * have to be named separately there, and a roster naming its members twice is one a new host can be
     * added to only halfway.
     */
    void restoreFoldFromSave();

    /**
     * @return a short description of this host's current view state, for the sidebar's view-state log so a
     *         panel gated out or drawn off-screen is diagnosable from the log alone. Prose for whoever reads
     *         that log, not a value to branch on: a host names its own screen's states in its own words,
     *         behind whatever every host reports alike, and the wording is free to change as those states
     *         need to say more, so nothing depends on its form
     */
    String describeViewState();
}
