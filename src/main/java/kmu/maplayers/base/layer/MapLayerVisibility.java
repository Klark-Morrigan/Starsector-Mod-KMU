package kmu.maplayers.base.layer;

/**
 * One screen's show-or-hide pick for the map layers: whether anything the layers put on that screen -
 * the overlay, the hover feedback, the labels, the sidebar - is on it at all. Held per screen for the
 * reason {@link ActiveLayerSelection} is: the control that flips it sits on each screen's own chrome, so
 * one shared value would move a screen the player is not looking at. A pick may be persistent (read from
 * and written to the save) or kept only for the session; the implementation decides which, and a consumer
 * reads and writes through this seam without knowing.
 *
 * <p>Hiding is a frame-level answer rather than a teardown: nothing is unwired, so showing again is a
 * read flipping back rather than a rebuild. What installs the machinery at all is a separate question,
 * and not this one.
 *
 * <p>Two readings rather than one, because they are owed different things. {@link #areLayersShown()} is
 * the crisp answer, true the instant the pick flips, which is what input and hit-testing stand down on -
 * a control that has been switched off must stop taking clicks at once, whatever is still on screen.
 * {@link #resolveShownFade()} is how far through the change the picture stands, which is what the paint
 * rides so the eye sees a dissolve rather than a cut.
 */
public interface MapLayerVisibility {

    /** @return whether this screen's layers are picked to show, answered the moment the pick flips. */
    boolean areLayersShown();

    /**
     * Records whether this screen's layers show.
     *
     * @param areLayersShown true to show them, false to hide them
     */
    void showLayers(boolean areLayersShown);

    /**
     * @return how much of this screen's layers is on it this frame - 0 with them wholly hidden, 1 with
     *         them wholly shown, and the values between while the pick is travelling from one to the
     *         other, for a caller multiplying it into what it paints
     */
    float resolveShownFade();
}
