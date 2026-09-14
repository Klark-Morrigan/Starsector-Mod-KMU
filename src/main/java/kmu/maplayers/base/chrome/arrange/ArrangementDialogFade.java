package kmu.maplayers.base.chrome.arrange;

import kmlib.animation.TraverseDurations;
import kmlib.animation.TraverseFraction;

/**
 * Whether the arranging dialog holds the screen, and how far onto it the dialog stands - two answers
 * that part company for the length of a fade.
 *
 * <p>The dialog fades because everything else that claims the screen does. A prompt a core screen
 * raises dissolves in and out over its own curve and the sidebar thins in step with it; a panel that
 * arrived whole beside that would snap the sidebar away on the frame it opened, and the difference
 * reads as a fault rather than as a style.
 *
 * <p><b>Raised is crisp; the fraction is the paint's alone.</b> Input has to go the instant the
 * dialog appears and come back the instant it is dismissed, while the paint runs on for the length
 * of the fade. The panel outliving the press is what makes the split load-bearing rather than tidy: a
 * dialog still on screen after the press must claim nothing, or it eats the click that follows it.
 * That is why the flag is held here rather than handed to the travel each frame - it is an answer in
 * its own right, and the one input reads.
 *
 * <p>Apart from the panel it paces because a clock kept inside a frame hook could be checked only by
 * opening the dialog and watching it.
 */
final class ArrangementDialogFade {

    // The pace the game's own prompts arrive and leave at - every confirmation a core screen raises
    // in front of itself is shown at these two durations - so two dialogs over one screen move
    // together. Stated here rather than taken from the toolkit's own default pace, which is what a
    // panel's motions answer input at: this one matches something the game does, so it is written
    // where that match is made and moves only if the game's does.
    private static final TraverseDurations MODAL_DURATIONS = new TraverseDurations(0.3f, 0.2f);

    // How far onto the screen the dialog is painted.
    private final TraverseFraction fadeProgress = new TraverseFraction();

    // Whether the dialog holds the screen, which is what input reads. Moves on the press, never on
    // the fade.
    private boolean isRaised;

    /**
     * Steps the fade toward the end the dialog is heading for, at that direction's own pace. A frame
     * spent already at that end leaves it unchanged, so a frame hook can call this unconditionally.
     *
     * @param elapsedSeconds real time since the last frame
     */
    void advanceFade(float elapsedSeconds) {
        fadeProgress.advanceTowardEnd(isRaised, elapsedSeconds, MODAL_DURATIONS);
    }

    /**
     * Hands the screen back while leaving the paint to fall from wherever it stands.
     */
    void dismissDialog() {
        isRaised = false;
    }

    /**
     * Takes the dialog down and its paint with it, in one step. For a panel that has lost the screen
     * it stood over: there is nothing left to fade against, and a fraction left part-way up would be
     * the first thing the next open painted before winding down.
     */
    void dropFade() {

        isRaised = false;
        fadeProgress.dropToRest();
    }

    /**
     * @return whether the dialog holds the screen - true from the frame it is raised and false from
     *         the press that dismisses it, whatever the paint is doing
     */
    boolean isDialogRaised() {
        return isRaised;
    }

    /**
     * Whether there is nothing left to show: the dialog is down and its fall has run out. What tells a
     * panel it can come off the screen, a panel still fading being one the player is watching leave.
     *
     * @return true once the dialog is dismissed and its fade sits exactly at down
     */
    boolean isSettledDown() {
        return !isRaised && fadeProgress.hasSettledAtRest();
    }

    /**
     * Takes the screen. The paint follows over the next frames from wherever it stands, which is
     * nothing on a first open and part-way down on a reopen during the fall.
     */
    void raiseDialog() {
        isRaised = true;
    }

    /**
     * @return how far onto the screen the dialog's paint stands, eased so the travel accelerates off
     *         the start and settles into the end: 0 nothing showing, 1 fully in place
     */
    float resolveFadeFraction() {
        return fadeProgress.getEasedValue();
    }
}
