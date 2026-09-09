package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.input.InputEventAPI;

import org.lwjgl.input.Keyboard;

import java.util.function.Predicate;

/**
 * What the arranging dialog does with one input event, and the rule that decides it.
 *
 * <p>Apart from the panel plugin that acts on it because the rule is the whole of the dialog's
 * modality and the plugin is unreachable. A plugin exists only inside a panel the game built, over a
 * core UI that exists only while the game is running - so a rule left inside it could be checked
 * only by opening the dialog and clicking, which is how the claim came to be the one part of the
 * dialog nothing had verified.
 *
 * <p><b>Only mouse events inside the dialog's own box are left alone; everything else is claimed.</b>
 * That way round rather than "claim everything" because the order in which the game hands events to
 * a panel's widgets and to its plugin is the game's business: leaving the box's own events untouched
 * is correct whichever way round it is, while claiming them first would leave the dialog's buttons
 * dead on a build that dispatches to the plugin first.
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
     * <p>Whether the event landed in the box is asked through a predicate rather than taken as a
     * flag, because it must not be asked at all for an event somebody has already consumed: six of
     * {@link InputEventAPI}'s own accessors throw once that has happened, and the box test reads one
     * of them. Whether the dialog is up is a flag for the opposite reason - it is a field read that
     * touches no event at all, and it is asked first because a dialog that has let go answers for
     * every event without measuring any of them.
     *
     * @param event                  the event the panel was handed
     * @param isDialogRaised         whether the dialog is still up, as against on screen but dismissed
     * @param isEventInsideDialogBox whether an event lands inside the dialog's own box
     * @return what the dialog does with it
     */
    static ArrangementDialogEventResponse resolveResponseTo(
            InputEventAPI event,
            boolean isDialogRaised,
            Predicate<InputEventAPI> isEventInsideDialogBox) {

        if (!isDialogRaised || event.isConsumed()) {
            return LEAVE_ALONE;
        }

        if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
            return CLOSE_DIALOG;
        }

        return isEventInsideDialogBox.test(event)
            ? LEAVE_ALONE
            : CLAIM;
    }
}
