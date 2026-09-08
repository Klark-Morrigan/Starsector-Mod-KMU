package kmu.maplayers.base.chrome.arrange;

/**
 * What the arranging dialog is doing this frame: whether it is up, and how far onto the screen its
 * paint stands.
 *
 * <p>The two travel together because a caller standing aside for the dialog needs both on the same
 * frame and needs them to agree: input stands down on {@code isRaised}, and only what is drawn follows
 * {@code fadeFraction}. Read as one value rather than two so nothing standing between the reads can
 * move one and not the other.
 *
 * <p>Its own shape rather than the game's modal state, and the difference is in the boolean. The
 * game's presence holds until a modal's fade has run out, because that is when the game takes it off
 * the tree; this dialog lets go of input on the press, while its paint is still falling. A caller
 * reading this through the game's shape would go on claiming input over a box that has already let
 * go of it.
 *
 * @param isRaised     whether the dialog is up for input - true from the frame it is raised and
 *                     false from the press that dismisses it
 * @param fadeFraction how far onto the screen the paint stands, 0..1, still falling after the press
 */
public record ArrangementDialogState(
    boolean isRaised,
    float fadeFraction) {

    /** The dialog down with nothing of it showing, which is every ordinary frame. */
    public static final ArrangementDialogState DOWN =
        new ArrangementDialogState(false, 0f);
}
