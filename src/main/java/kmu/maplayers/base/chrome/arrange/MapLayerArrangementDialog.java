package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import kmlib.starsector.ui.coreui.CoreUiOverlayPanels;
import kmlib.starsector.ui.map.probes.ShownMapTab;

import kmu.maplayers.base.layer.LiveMapLayerArrangement;
import kmu.maplayers.base.layer.MapLayerRegistry;

import java.util.List;

/**
 * The dialog the player arranges the layer bar in: when it stands up, what it claims while it is up,
 * and when it comes down. What it looks like is {@link MapLayerArrangementDialogBody}'s, and what the
 * controls in it actually do is {@link MapLayerArrangementEditor}'s.
 *
 * <p><b>Built from the game's own widgets</b> - a panel from {@code SettingsAPI.createCustom} stood in
 * the core UI's own tree by {@link CoreUiOverlayPanels}. Nothing here is painted into the map's render
 * pass, so the dialog reads as part of the game rather than as something drawn over it, and its
 * controls behave like the ones beside them.
 *
 * <p>Every published route to a custom dialog hangs off an interaction dialog, and the screens this is
 * opened from have none - raising one would close the screen whose bar is being arranged. The core UI
 * tree is what is left, which is a reach rather than an API and is why the dialog simply does not open
 * where that reach comes up empty.
 *
 * <p><b>Modality is supplied here, because the game does not supply it.</b> A panel added this way is
 * an ordinary child: nothing dims behind it and nothing stops the screen underneath being dispatched
 * to. So the body paints its own backdrop, through this plugin's own render hook, and this claims the
 * input its own widgets do not want. What cannot be supplied is being recognised as a modal by anything
 * reading the game's modal base, so whatever stands down under one has to read this dialog's own state
 * beside that reading - {@link #isDialogRaised()} where the question is input, and
 * {@link #resolveDialogState()} where it is input and paint together.
 *
 * <p>Which events that claim takes and which it leaves alone is
 * {@link ArrangementDialogEventResponse}'s; this acts on the answer.
 *
 * <p><b>Arrives and leaves on a fade of its own</b>, kept by {@link ArrangementDialogFade}, since the
 * game fades nothing it did not raise. The panel's opacity is written from that fade each frame, which
 * is what the engine multiplies into the alpha it hands every widget in the panel and this plugin's
 * render hook alike - so the box, its controls and the dim behind them arrive as one piece, and a panel
 * the engine is itself fading keeps that fade over ours rather than having it overwritten. The panel
 * therefore outlives the press that dismisses it, coming off the screen at the end of the fall; for
 * that length it is on screen and claims nothing.
 *
 * <p>Closes itself when the map goes off screen. The panel hangs from the core UI rather than from the
 * screen it was opened on, so nothing about leaving that screen takes it down - and a dialog left
 * standing over the campaign would be one the player cannot connect to anything they did.
 *
 * <p>One dialog for the process, like the hosts that open it. Two would be two arrangements of one bar
 * being edited at once, each recording over the other.
 */
public final class MapLayerArrangementDialog {

    /** The one dialog, opened from whichever screen's bar the player reached for it on. */
    public static final MapLayerArrangementDialog INSTANCE = new MapLayerArrangementDialog();

    // How far the panel is offset from the corner it hangs off, which is not at all: it is the size of
    // the screen and the core UI it hangs in is too, so the two corners coincide.
    private static final float NO_OFFSET = 0f;

    // Whether the dialog is up, and how far onto the screen it is painted. The first is the dialog's
    // "is it up" state; the panel below is not, since it stays for the fall after the press.
    private final ArrangementDialogFade fade = new ArrangementDialogFade();

    // What the player is arranging, or null while the dialog is down. Seeded when the dialog opens and
    // dropped on the press that dismisses it, so a second visit reads the store again rather than the
    // rows it left.
    private MapLayerArrangementEditor editor;

    // The screen-sized panel standing in the core UI, or null while none does. Stood up on the first
    // open and kept through the fall, so a reopen during the fall reuses it rather than standing a
    // second one over the first.
    private CustomPanelAPI dialogPanel;

    // The widgets currently in that panel, replaced whole on every change.
    private MapLayerArrangementDialogBody body;

    private MapLayerArrangementDialog() {
    }

    /**
     * @return whether the dialog is up for input - which the map-side gates read beside the game's own
     *         modal reading, this dialog being invisible to that one. False from the press that
     *         dismisses it, while the panel is still fading off the screen
     */
    public boolean isDialogRaised() {
        return fade.isDialogRaised();
    }

    /**
     * @return the crisp answer above beside how far the paint stands, on one frame, for a caller that
     *         both stands its input down under the dialog and fades against it
     */
    public ArrangementDialogState resolveDialogState() {
        return new ArrangementDialogState(fade.isDialogRaised(), fade.resolveFadeFraction());
    }

    /**
     * Raises the dialog over whatever screen is showing a map.
     *
     * <p>Does nothing where it is already up, where no store is bound to write the arrangement to, and
     * where the core UI cannot be reached to stand a panel in - all three leaving the screen as it was
     * rather than half-opening. A dialog reopened while it is still fading out keeps the panel it has
     * and comes back up from where the fade stood.
     */
    public void openDialog() {

        if (isDialogRaised()) {
            return;
        }

        var arrangementSelection = LiveMapLayerArrangement.resolveArrangementSelection();
        if (arrangementSelection == null) {
            return;
        }

        if (dialogPanel == null && !standPanelUp()) {
            return;
        }

        fade.raiseDialog();
        editor = new MapLayerArrangementEditor(arrangementSelection, MapLayerRegistry.getLayers());

        rebuildBody();
    }

    /**
     * Takes the dialog down for input and drops what it was holding; the panel stays for the length of
     * its fall and comes off the screen at the end of it. Safe to call with the dialog already down,
     * which is what lets every way it can end - the close button, Escape, the screen closing - say the
     * same thing.
     */
    public void closeDialog() {

        if (!isDialogRaised()) {
            return;
        }

        fade.dismissDialog();
        editor = null;
    }

    // The body, replaced whole rather than patched. The rows move, so what a change alters is the
    // column's order - and a body built afresh from the editor cannot disagree with it, which a set of
    // widgets each nudged into a new place eventually would.
    private void rebuildBody() {

        if (body != null) {
            body.removeFromPanel(dialogPanel);
        }
        body = new MapLayerArrangementDialogBody(dialogPanel, editor, this::applyRowAction, this::closeDialog);
    }

    // What a press on a row's controls does. The row is named by layer id rather than by position, the
    // position having moved by the time a second press arrives. Which control means what is the editor's,
    // leaving this with the half that only a standing dialog has: drawing the result.
    //
    // Ignored once the dialog is dismissed: its widgets stay on screen for the fall and the game goes on
    // dispatching to them, but there is no editor left for a press to reach.
    private void applyRowAction(String layerId, ArrangementRowAction action) {

        if (!isDialogRaised()) {
            return;
        }

        editor.applyRowAction(layerId, action);

        rebuildBody();
    }

    // Whether this event belongs to the dialog's own widgets, which is the one thing the claim leaves
    // alone. Answered off the box the layout placed rather than off the numbers it was laid out from,
    // so a resized window moves the claim with the box.
    private boolean isEventInsideDialogBox(InputEventAPI event) {

        return body != null
            && event.isMouseEvent()
            && body.getBoxPlacement().containsEvent(event);
    }

    // Stands the screen-sized panel in the core UI, painted at nothing so its first frame is the start
    // of the rise rather than a flash of the whole box before the fade has been stepped once.
    private boolean standPanelUp() {

        var settings = Global.getSettings();
        var panel = settings.createCustom(
            settings.getScreenWidth(),
            settings.getScreenHeight(),
            new DialogPanelPlugin());

        var placement = CoreUiOverlayPanels.attachOverlayPanel(panel);
        if (placement == null) {
            return false;
        }
        placement.inTL(NO_OFFSET, NO_OFFSET);

        dialogPanel = panel;
        paintFadeOntoPanel();

        return true;
    }

    // Takes the panel off the screen and forgets everything built into it, the fade included: what
    // comes down here is not fading, it is gone.
    private void takePanelDown() {

        CoreUiOverlayPanels.detachOverlayPanel(dialogPanel);

        dialogPanel = null;
        body = null;
        editor = null;
        fade.dropFade();
    }

    // One frame of the fade: step it, show it, and take the panel down once there is nothing left of
    // it to show.
    private void advanceDialogFade(float elapsedSeconds) {

        fade.advanceFade(elapsedSeconds);
        paintFadeOntoPanel();

        if (fade.isSettledDown()) {
            takePanelDown();
        }
    }

    // Where the paint stands, written onto the panel as its opacity. The engine multiplies that into
    // the alpha it hands every widget the panel holds and the plugin's own render hook, so one write
    // fades the box, its controls and the fills beneath them together, under whatever fade the engine
    // is itself applying to the panel.
    private void paintFadeOntoPanel() {
        dialogPanel.setOpacity(fade.resolveFadeFraction());
    }

    // The dialog's own frame hooks: the claim that makes it modal, the fade that brings it in and out,
    // and the guard that takes it down with the screen it was opened on.
    private final class DialogPanelPlugin implements CustomUIPanelPlugin {

        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {

            // Under every widget the panel holds, which is where the game draws the interiors of its own
            // custom panels: the dim that stands the screen down and the box's own surface. Nothing else
            // paints them - the game publishes a rectangle component that strokes and none that fills, so
            // the rule around the box is a widget and the two filled areas are not. The alpha handed in
            // already carries the dialog's own fade, the panel's opacity being where that is written.
            if (body != null) {
                body.renderFills(alphaMult);
            }
        }

        @Override
        public void render(float alphaMult) {
        }

        @Override
        public void advance(float amount) {

            // A panel this dialog has already let go of, which is what a detach that could not reach the
            // core UI leaves behind: still advanced by whatever is holding it, and no longer ours to
            // paint. Asked first because everything below it writes through a panel this no longer has.
            if (dialogPanel == null) {
                return;
            }

            // The panel hangs from the core UI, which outlives the screen the dialog was opened on -
            // so leaving that screen has to be noticed rather than waited for. Down at once rather than
            // faded: there is no screen left under it to fade against.
            if (!isMapShowing()) {
                takePanelDown();
                return;
            }

            advanceDialogFade(amount);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {

            for (var event : events) {

                var response = ArrangementDialogEventResponse.resolveResponseTo(
                    event,
                    isDialogRaised(),
                    MapLayerArrangementDialog.this::isEventInsideDialogBox);

                if (response == ArrangementDialogEventResponse.LEAVE_ALONE) {
                    continue;
                }
                event.consume();

                // Nothing after the dialog has been dismissed is this plugin's to answer for: from here
                // on the panel is only fading, and the events under it are the screen's again.
                if (response == ArrangementDialogEventResponse.CLOSE_DIALOG) {
                    closeDialog();
                    return;
                }
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }

        // Whether a map is still on screen to have a bar to arrange. The reach raises where the widget
        // tree cannot be walked at all, and that reads as no map: a dialog nobody can place should come
        // down rather than stay up over a screen it can no longer see.
        private boolean isMapShowing() {

            try {
                return ShownMapTab.resolveShownMapTab() != null;

            } catch (RuntimeException cannotReachMap) {
                return false;
            }
        }
    }
}
