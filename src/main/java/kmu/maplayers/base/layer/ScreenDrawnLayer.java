package kmu.maplayers.base.layer;

/**
 * Which layer one screen is drawing this frame, which is not always the layer it is set to: a screen
 * switched off goes on showing what was on it until its dissolve runs out, whatever its pick has done
 * meanwhile.
 *
 * <p>Apart from both picks because it is neither. The selection is what the player chose and the
 * visibility is whether that choice is on screen; this is what the eye can actually see, which parts
 * from the pick for exactly as long as a dissolve lasts. Every pass driven by "what draws" reads this,
 * and the two picks are left saying only what they are each about.
 *
 * <p><b>Why the picture has to be remembered rather than resolved.</b> A dissolve is of something, and
 * the something can move while it runs. Taking the last tab that paints off the bar switches the screen
 * off <em>and</em> lands the pick on the empty view in one frame - both are owed at once, or the bar,
 * the map and the box disagree - so a dissolve that painted the live pick would have nothing left to
 * dissolve on the very frame it began. Held, the layer the player was looking at goes on being drawn,
 * thinning, until it is gone. The same memory answers a tab switched by key part-way through a hide:
 * what leaves the screen is what was on it, not whatever was picked while it was leaving.
 *
 * <p><b>The picture is caught on the way past rather than at the switch-off.</b> Every frame a screen
 * is showing, this is asked what draws and holds the answer - so the last thing it held when a screen
 * goes off is what was on it. That works because every control able to switch a screen off stands on
 * that screen: the box on its filter row, and the dialog opened from its own sidebar. A screen carrying
 * either is a screen being drawn, so a switch-off is always preceded by drawn frames. A screen switched
 * off with no drawn frame behind it holds nothing and dissolves nothing, which is the honest answer -
 * there was no picture on it to leave.
 *
 * <p>Session state, like the ramp it serves: what is part-way off a screen is what the eye is in the
 * middle of, and no load is in the middle of anything. Nothing takes it back on a campaign load because
 * nothing has to - a loaded campaign has no control standing on either screen, so both read as shown
 * until one does, and the first read of the new campaign catches its own pick.
 */
public final class ScreenDrawnLayer {

    // The end of the hide ramp: nothing of this screen's layers left on it, and nothing left to name.
    private static final float FULLY_HIDDEN = 0f;

    private final ActiveLayerSelection layerSelection;
    private final MapLayerVisibility layerVisibility;

    // What this screen was showing when it was last asked while switched on - the picture a dissolve
    // has to go on drawing. Null before the screen has drawn at all, and again once one has run out.
    private MapLayer layerLastShown;

    /**
     * @param layerSelection  which layer this screen is set to
     * @param layerVisibility whether this screen's layers are on it, and how far through a change
     */
    public ScreenDrawnLayer(
            ActiveLayerSelection layerSelection,
            MapLayerVisibility layerVisibility) {

        this.layerSelection = layerSelection;
        this.layerVisibility = layerVisibility;
    }

    /**
     * The layer on this screen this frame: its pick while the screen is on, the picture still leaving
     * while a dissolve runs, and nothing once that dissolve is over.
     *
     * <p>The crisp pick is read first and the ramp only where the answer could still turn on it. Not a
     * style choice: the fade is derived from a clock with a settings read behind it, and this sits on
     * the map's hottest path, so a screen that is simply on must not pay for it.
     *
     * @return the layer being drawn, or null where nothing is
     */
    public MapLayer resolveDrawnLayer() {

        if (layerVisibility.areLayersShown()) {

            layerLastShown = layerSelection.getActiveLayer();

            return layerLastShown;
        }

        if (layerVisibility.resolveShownFade() > FULLY_HIDDEN) {
            return layerLastShown;
        }
        // Settled off, so there is no picture and nothing to hold one for. Released rather than kept:
        // a screen switched on again catches its pick on the next frame, and a layer held past the end
        // of its own dissolve is a reference to something nothing is drawing.
        layerLastShown = null;

        return null;
    }

    /**
     * Forgets what this screen was showing, leaving the reading a screen that has never drawn gives.
     *
     * <p>Package-private, and the running game asks it of nothing: a campaign load takes every screen's
     * control back, which reads both screens as shown until a box stands again, so the first frame of a
     * new campaign catches its own pick before any dissolve could ask for one.
     */
    void forgetDrawnLayer() {
        layerLastShown = null;
    }
}
