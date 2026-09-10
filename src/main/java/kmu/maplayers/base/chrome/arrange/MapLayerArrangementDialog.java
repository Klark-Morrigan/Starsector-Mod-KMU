package kmu.maplayers.base.chrome.arrange;

import kmlib.starsector.ui.coreui.OverlayPresence;

import kmu.maplayers.base.layer.LiveMapLayerArrangement;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerStandings;

/**
 * The dialog the player arranges the layer bar in: what is being arranged, when it may be started,
 * and what ends it. The surface it stands on is {@link ArrangementDialogPanel}'s, what the controls in
 * it do is {@link MapLayerArrangementEditor}'s, and what it looks like is
 * {@link MapLayerArrangementDialogBody}'s.
 *
 * <p>Every published route to a custom dialog hangs off an interaction dialog, and the screens this is
 * opened from have none - raising one would close the screen whose bar is being arranged. The core UI
 * tree is what is left, which is a reach rather than an API and is why the dialog simply does not open
 * where that reach comes up empty.
 *
 * <p>What cannot be supplied at all is being recognised as a modal by anything reading the game's own
 * modal base, so whatever stands down under one has to read this dialog beside that reading -
 * {@link #isDialogRaised()} where the question is input, and {@link #resolveDialogPresence()} where it
 * is input and paint together.
 *
 * <p>It is also where an arrangement reaches the layers themselves: the editor records what the
 * player did, and this asks {@link MapLayerStandings} to bring each layer's wiring into line with the
 * bar they now have. Here rather than in the editor because standing a layer up on a sector is the
 * framework's business and the editor is deliberately reachable without a running game.
 *
 * <p>The editor is seeded at each open and dropped at each close, so a second visit reads the store
 * again rather than the rows it left. It is thereafter its own source of truth: re-reading the store
 * between presses would read back what it just wrote, and re-reading the roster would let a mod
 * registering a layer mid-dialog shuffle the row under the player's pointer.
 *
 * <p>One dialog for the process, like the hosts that open it. Two would be two arrangements of one bar
 * being edited at once, each recording over the other.
 */
public final class MapLayerArrangementDialog {

    /** The one dialog, opened from whichever screen's bar the player reached for it on. */
    public static final MapLayerArrangementDialog INSTANCE = new MapLayerArrangementDialog();

    // The surface this stands on, told what to do and reporting back the two things it cannot answer
    // for: a press on the way out, and a screen gone out from under it.
    private final ArrangementDialogPanel panel =
        new ArrangementDialogPanel(this::closeDialog, this::abandonDialog);

    // What the player is arranging, or null while the dialog is down.
    private MapLayerArrangementEditor editor;

    private MapLayerArrangementDialog() {
    }

    /**
     * Takes the dialog down and drops what it was holding; the box stays for the length of its fall.
     * Safe to call with the dialog already down, which is what lets every way it can end - the way out,
     * Escape, the screen closing - say the same thing.
     */
    public void closeDialog() {

        if (!isDialogRaised()) {
            return;
        }

        panel.dismissPanel();
        editor = null;
    }

    /**
     * @return whether the dialog holds the screen - which the map-side gates read beside the game's own
     *         modal reading, this dialog being invisible to that one. False from the press that
     *         dismisses it, while the box is still fading off the screen
     */
    public boolean isDialogRaised() {
        return panel.isPanelRaised();
    }

    /**
     * Raises the dialog over whatever screen is showing a map.
     *
     * <p>Does nothing where it is already up, where no store is bound to write the arrangement to, and
     * where the core UI cannot be reached to stand a panel in - all three leaving the screen as it was
     * rather than half-opening. A dialog reopened while it is still fading out keeps the box it has and
     * comes back up from where the fade stood.
     */
    public void openDialog() {

        if (isDialogRaised()) {
            return;
        }

        var arrangementSelection = LiveMapLayerArrangement.resolveArrangementSelection();
        if (arrangementSelection == null) {
            return;
        }

        if (!panel.raisePanel()) {
            return;
        }

        editor = new MapLayerArrangementEditor(arrangementSelection, MapLayerRegistry.getLayers());

        rebuildBody();
    }

    /**
     * @return the crisp answer above beside how far the paint stands, on one frame, for a caller that
     *         both stands its input down under the dialog and fades against it
     */
    public OverlayPresence resolveDialogPresence() {
        return panel.resolvePanelPresence();
    }

    // The screen the dialog stood over has gone, taking the panel with it. Nothing to dismiss and
    // nothing to fade - only the arrangement to let go of.
    private void abandonDialog() {
        editor = null;
    }

    // What a press on a row's controls does. The row is named by layer id rather than by position, the
    // position having moved by the time a second press arrives. Which control means what is the editor's,
    // leaving this with the half that only a standing dialog has: drawing the result.
    //
    // Ignored once the dialog is dismissed: its widgets stay on screen for the fall and the game goes on
    // dispatching to them, but there is no arrangement left for a press to reach.
    private void applyRowAction(String layerId, ArrangementRowAction action) {

        if (!isDialogRaised()) {
            return;
        }

        editor.applyRowAction(layerId, action);

        // The bar the press just changed is also the answer to which layers are worth wiring, so a
        // tab taken off stops costing at the press rather than at the next load. Asked after every
        // press rather than after the hide alone: which press moved an id between shown and hidden
        // is the standings' own diff to make, and a caller deciding it here would be a second
        // statement of that rule.
        MapLayerStandings.applyArrangementWhereverInstalled();

        rebuildBody();
    }

    // The body, replaced whole rather than patched. The rows move, so what a change alters is the
    // column's order - and a body built afresh from the editor cannot disagree with it, which a set of
    // widgets each nudged into a new place eventually would.
    private void rebuildBody() {

        panel.showBody(box -> new MapLayerArrangementDialogBody(
            box,
            editor,
            this::applyRowAction,
            this::closeDialog));
    }
}
