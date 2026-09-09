package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import kmlib.starsector.ui.coreui.CoreUiOverlayPanels;
import kmlib.starsector.ui.coreui.OverlayPresence;
import kmlib.starsector.ui.map.probes.ShownMapTab;

import java.util.List;
import java.util.function.Function;

/**
 * The surface the arranging dialog stands on: a screen-sized panel in the core UI, what it claims
 * while it holds the screen, how far onto the screen it is painted, and when it comes off again.
 *
 * <p>Apart from {@link MapLayerArrangementDialog} because the two answer different questions. What is
 * being arranged and when the player may start or stop is a question about an arrangement; where the
 * widgets stand, what the frame hooks do, and how long the box lingers after the press is a question
 * about a surface. Together they were one class holding a lifecycle, a clock, a panel and a claim
 * rule, and no half could be read without the other.
 *
 * <p><b>Built from the game's own widgets</b> - a panel from {@code SettingsAPI.createCustom} stood in
 * the core UI's own tree by {@link CoreUiOverlayPanels}. Nothing here is painted into the map's render
 * pass, so the dialog reads as part of the game rather than as something drawn over it.
 *
 * <p><b>Modality is supplied here, because the game does not supply it.</b> A panel added this way is
 * an ordinary child: nothing dims behind it and nothing stops the screen underneath being dispatched
 * to. So the body paints its own backdrop, through this plugin's render hook, and this claims the
 * input its own widgets do not want. Which events that claim takes is
 * {@link ArrangementDialogEventResponse}'s; this acts on the answer.
 *
 * <p><b>The fade is written as the panel's opacity</b>, which the engine multiplies into the alpha it
 * hands every widget the panel holds and this plugin's render hook alike. So one write a frame fades
 * the box, its controls and the fills beneath them as one piece, and a panel the engine is itself
 * fading keeps that fade over ours rather than having it overwritten.
 *
 * <p>The panel therefore outlives the press that dismisses it, coming off the screen at the end of the
 * fall. For that length it is on screen and claims nothing.
 *
 * <p>What it cannot answer for itself it reports rather than decides: a press on the way out and a
 * screen that has gone are both handed back, because whether either ends the arrangement is the
 * dialog's to say.
 */
final class ArrangementDialogPanel {

    // How far the panel is offset from the corner it hangs off, which is not at all: it is the size of
    // the screen and the core UI it hangs in is too, so the two corners coincide.
    private static final float NO_OFFSET = 0f;

    // Whether the dialog holds the screen, and how far onto it the box is painted.
    private final ArrangementDialogFade fade = new ArrangementDialogFade();

    // A press on the way out, for the dialog to answer for. Reported rather than acted on here: this
    // knows the press happened, and what it costs the player's arrangement is not its to decide.
    private final Runnable onCloseRequested;

    // The screen this stood over has gone, and the panel with it. Reported for the same reason, and
    // separately, because it is not a dismissal: there is nothing left to fade against.
    private final Runnable onPanelLost;

    // The screen-sized panel standing in the core UI, or null while none does. Stood up on the first
    // open and kept through the fall, so a reopen during the fall reuses it rather than standing a
    // second one over the first.
    private CustomPanelAPI panel;

    // The widgets currently in that panel, replaced whole on every change.
    private MapLayerArrangementDialogBody body;

    /**
     * @param onCloseRequested what a press on the way out is reported to
     * @param onPanelLost      what a screen gone out from under the panel is reported to
     */
    ArrangementDialogPanel(Runnable onCloseRequested, Runnable onPanelLost) {

        this.onCloseRequested = onCloseRequested;
        this.onPanelLost = onPanelLost;
    }

    /**
     * Hands the screen back. The box stays for the length of its fall and comes off at the end of it.
     */
    void dismissPanel() {
        fade.dismissDialog();
    }

    /**
     * @return whether the dialog holds the screen, which is false from the press that dismisses it
     *         even while the box is still painted
     */
    boolean isPanelRaised() {
        return fade.isDialogRaised();
    }

    /**
     * Takes the screen, standing the panel up first where there is none to reuse.
     *
     * <p>Painted at nothing on the frame it is stood up, so the first frame is the start of the rise
     * rather than a flash of the whole box before the fade has been stepped once.
     *
     * @return whether the panel is up, false only where the core UI could not be reached to stand one
     *         in - which leaves the screen exactly as it was rather than half-opened
     */
    boolean raisePanel() {

        if (panel == null && !standPanelUp()) {
            return false;
        }
        fade.raiseDialog();

        return true;
    }

    /**
     * @return what this is doing on one frame, for whatever stands aside for it
     */
    OverlayPresence resolvePanelPresence() {
        // Shaped here rather than by the clock beneath, which is state and knows nothing of screens.
        // What is raised over one is this, so how that is reported is this one's to say.
        return new OverlayPresence(fade.isDialogRaised(), fade.resolveFadeFraction());
    }

    /**
     * Replaces the widgets in the panel with freshly built ones.
     *
     * <p>Takes a builder rather than a built body because what goes in the box is the dialog's to
     * compose and the panel it is composed against is this one's to hold - so neither has to publish
     * what the other keeps.
     *
     * @param buildBody builds the widgets against the panel they will stand in
     */
    void showBody(Function<CustomPanelAPI, MapLayerArrangementDialogBody> buildBody) {

        if (body != null) {
            body.removeFromPanel(panel);
        }
        body = buildBody.apply(panel);
    }

    // Whether this event belongs to the dialog's own widgets, which is the one thing the claim leaves
    // alone. Answered off the box the layout placed rather than off the numbers it was laid out from,
    // so a resized window moves the claim with the box.
    private boolean isEventInsideDialogBox(InputEventAPI event) {

        return body != null
            && event.isMouseEvent()
            && body.getBoxPlacement().containsEvent(event);
    }

    // Where the paint stands, written onto the panel as its opacity - the one write that fades every
    // part of the dialog together.
    private void paintFadeOntoPanel() {
        panel.setOpacity(fade.resolveFadeFraction());
    }

    // Stands the screen-sized panel in the core UI, painted at nothing.
    private boolean standPanelUp() {

        var settings = Global.getSettings();
        var newPanel = settings.createCustom(
            settings.getScreenWidth(),
            settings.getScreenHeight(),
            new DialogPanelPlugin());

        var placement = CoreUiOverlayPanels.attachOverlayPanel(newPanel);
        if (placement == null) {
            return false;
        }
        placement.inTL(NO_OFFSET, NO_OFFSET);

        panel = newPanel;
        paintFadeOntoPanel();

        return true;
    }

    // Takes the panel off the screen and forgets everything built into it, the fade included: what
    // comes down here is not fading, it is gone.
    private void takePanelDown() {

        CoreUiOverlayPanels.detachOverlayPanel(panel);

        panel = null;
        body = null;
        fade.dropFade();
    }

    // The panel's own frame hooks: the claim that makes the dialog modal, the fade that brings it in
    // and out, and the guard that takes it down with the screen it was opened on. Glue by design -
    // a plugin exists only inside a panel the game built, so every rule it acts on is stated
    // somewhere it can be checked without one.
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

            // A panel this has already let go of, which is what a detach that could not reach the core
            // UI leaves behind: still advanced by whoever holds it, and no longer ours to paint. Asked
            // first because everything below writes through a panel this no longer has.
            if (panel == null) {
                return;
            }

            // The panel hangs from the core UI, which outlives the screen the dialog was opened on -
            // so leaving that screen has to be noticed rather than waited for. Down at once rather than
            // faded: there is no screen left under it to fade against.
            if (!isMapShowing()) {
                takePanelDown();
                onPanelLost.run();
                return;
            }

            fade.advanceFade(amount);
            paintFadeOntoPanel();

            if (fade.isSettledDown()) {
                takePanelDown();
            }
        }

        @Override
        public void processInput(List<InputEventAPI> events) {

            for (var event : events) {

                var response = ArrangementDialogEventResponse.resolveResponseTo(
                    event,
                    isPanelRaised(),
                    ArrangementDialogPanel.this::isEventInsideDialogBox);

                if (response == ArrangementDialogEventResponse.LEAVE_ALONE) {
                    continue;
                }
                event.consume();

                // Nothing after the press is this pass's to answer for: from here on the panel is only
                // fading, and the events under it are the screen's again.
                if (response == ArrangementDialogEventResponse.CLOSE_DIALOG) {
                    onCloseRequested.run();
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
