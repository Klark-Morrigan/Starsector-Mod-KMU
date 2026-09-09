package kmu.maplayers.base.chrome.arrange;

import kmlib.animation.EasedFraction;
import kmlib.animation.TraverseDurations;

/**
 * Whether the arranging dialog is up, and how far onto the screen it stands - two answers that part
 * company for the length of a fade.
 *
 * <p>The dialog fades because everything else that claims the screen does. A prompt a core screen
 * raises dissolves in and out over its own curve and the sidebar thins in step with it; a panel that
 * arrived whole beside that would snap the sidebar away on the frame it opened, and the difference
 * reads as a fault rather than as a style. So the dialog keeps a fraction of its own, rising from
 * nothing as it opens and falling back as it closes, at the pace the game's own prompts take.
 *
 * <p><b>Raised is crisp; the fraction is the paint's alone.</b> Input has to go the instant the
 * dialog appears and come back the instant it is dismissed, while the paint runs on for the length
 * of the fade. The panel outliving the press is what makes the split load-bearing rather than tidy: a
 * dialog still on screen after the press must claim nothing, or it eats the click that follows it.
 *
 * <p>Retargeted rather than replayed, so a dialog reopened mid-fall comes up from where the fade
 * stood rather than from nothing - the {@link EasedFraction} inside stores its position linearly and
 * eases only on read, which is what keeps a reversal continuous.
 *
 * <p>Apart from the dialog because the dialog's frame hooks exist only inside a panel the game built,
 * and a clock kept in there could be checked only by opening the dialog and watching it.
 */
final class ArrangementDialogFade {

    // The pace the game's own prompts arrive and leave at - every confirmation a core screen raises
    // in front of itself is shown at these two durations - so two dialogs over one screen move
    // together. The way out is the shorter, as it is there: a dismissal answers a press and should
    // be gone under it.
    private static final TraverseDurations MODAL_DURATIONS = new TraverseDurations(0.3f, 0.2f);

    // The two ends the fade travels between, named so a target reads as a destination rather than a
    // bare bound.
    private static final float FULLY_UP = 1f;
    private static final float FULLY_DOWN = 0f;

    // A drop covers the whole way in one step, so the time it is charged is immaterial.
    private static final float NO_ELAPSED_SECONDS = 0f;

    // Where the paint stands between down and up.
    private final EasedFraction fadeProgress = new EasedFraction();

    // Whether the dialog is up, which is what input reads. Moves on the press, never on the fade.
    private boolean isRaised;

    /**
     * Steps the fade toward the end the dialog is heading for, at that direction's own pace. A frame
     * spent already at that end leaves it unchanged, so a frame hook can call this unconditionally.
     *
     * @param elapsedSeconds real time since the last frame
     */
    void advanceFade(float elapsedSeconds) {
        stepFadeToward(elapsedSeconds, MODAL_DURATIONS);
    }

    /**
     * Takes the dialog down for input, leaving the paint to fall from wherever it stands.
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

        // Routed through the ordinary step rather than a second write to the fraction, so there is one
        // rule for where down is and the drop cannot land somewhere the fade never does. What makes it
        // one step rather than a run is the pace, which covers the whole way at once.
        stepFadeToward(NO_ELAPSED_SECONDS, TraverseDurations.SNAP);
    }

    /**
     * @return whether the dialog is up - true from the frame it is raised and false from the press
     *         that dismisses it, whatever the paint is doing
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
        return !isRaised && fadeProgress.hasReachedTarget(FULLY_DOWN);
    }

    /**
     * Puts the dialog up for input. The paint follows over the next frames from wherever it stands,
     * which is nothing on a first open and part-way down on a reopen during the fall.
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

    // The end the fade is heading for this frame. One rule for what raised means to the paint, so the
    // advance and the settled test cannot disagree about which end that is.
    private float resolveTargetProgress() {
        return isRaised
            ? FULLY_UP
            : FULLY_DOWN;
    }

    // One step of the fade toward whichever end the dialog is heading for, at a stated pace. Which way
    // it is heading is what raised says, so the pace follows from the same field rather than from a
    // second reading of where the fraction currently stands.
    private void stepFadeToward(float elapsedSeconds, TraverseDurations durations) {

        fadeProgress.advanceTowardTarget(
            resolveTargetProgress(),
            elapsedSeconds,
            durations.resolveDurationSeconds(isRaised));
    }
}
