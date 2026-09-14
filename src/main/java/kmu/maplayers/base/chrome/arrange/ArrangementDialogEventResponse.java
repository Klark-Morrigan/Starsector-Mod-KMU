package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.input.InputEventAPI;

import org.lwjgl.input.Keyboard;

/**
 * What the arranging dialog does with one input event, and the rule that decides it.
 *
 * <p>Apart from the panel plugin that acts on it because the rule is the whole of the dialog's
 * modality and the plugin is unreachable. A plugin exists only inside a panel the game built, over a
 * core UI that exists only while the game is running - so a rule left inside it could be checked
 * only by opening the dialog and clicking, which is how the claim came to be the one part of the
 * dialog nothing had verified.
 *
 * <p><b>A raised dialog claims every event it is handed that nothing has already taken.</b> The
 * engine hands a custom panel's events to its children first and to its plugin afterwards, so
 * anything the dialog's own controls acted on arrives here already consumed - which makes the
 * consumed skip below the whole of what protects them, and leaves nothing to except by position.
 *
 * <p>The earlier rule excepted the dialog's own box by measuring it, on the reading that the
 * dispatch order was the game's business. It is not the game's business, and the exception was
 * being read by the widget underneath: the map answers a pointer it hears, and inside the box it
 * heard one, so a star under the dim raised its own tooltip while the same star out on the open map
 * answered nothing.
 *
 * <p><b>A dismissed dialog claims nothing at all</b>, though its box is on screen for the length of
 * its fade. The press that dismisses it is the moment the screen is the player's again, and a claim
 * held for the fall would eat the click that follows.
 */
enum ArrangementDialogEventResponse {

    /** The event is the dialog's own widgets' to handle, or somebody else's already. */
    LEAVE_ALONE,

    /** The event is consumed, so nothing under the dialog is dispatched to. */
    CLAIM,

    /** The event is consumed and the dialog comes down with it. */
    CLOSE_DIALOG;

    /**
     * Decides what becomes of {@code event}.
     *
     * <p>Whether the dialog is up is asked before the event is touched at all, because a dialog that
     * has let go answers for every event without reading one - and six of {@link InputEventAPI}'s
     * accessors throw once something has consumed the event, so the consumed skip has to stand ahead
     * of the key readings below it either way.
     *
     * @param event          the event the panel was handed
     * @param isDialogRaised whether the dialog is still up, as against on screen but dismissed
     * @return what the dialog does with it
     */
    static ArrangementDialogEventResponse resolveResponseTo(
            InputEventAPI event,
            boolean isDialogRaised) {

        if (!isDialogRaised || event.isConsumed()) {
            return LEAVE_ALONE;
        }

        if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
            return CLOSE_DIALOG;
        }

        return CLAIM;
    }
}
