package kmu.maplayers.base.layer;

/**
 * Says whether a screen has a control able to reverse a hide, and takes back the two things a case can
 * otherwise leave on the process-wide screens: that saying, and what a screen was last showing.
 *
 * <p>{@link MapLayerScreens} is static and neither clears itself, so a class that stands a control would
 * otherwise leave every later one in the JVM acting on stored hides where a run with no control reads the
 * layers shown, and a class that drew a screen would leave the next one's dissolve showing its picture.
 * Whether either showed up at all would then depend on which class happened to run first, which is the
 * failure this exists to remove.
 *
 * <p>Reached through here rather than off {@link MapLayerScreens} directly so that a suite states the
 * unwinding in its own terms, beside the standing it pairs with. The running game asks for the same
 * thing on every campaign load - the screens outlive a sector while the picks they govern do not - so
 * this is a convenience over a live seam rather than a way in to one the mod never uses. The same
 * arrangement {@link MapLayerRosters} uses for the roster beside it.
 *
 * <p>Which screen a control lands on is deliberately not settled here. It follows whichever screen
 * is showing, exactly as it does in play, so a caller states that arrangement itself rather than
 * being handed one - a control recorded against a screen the caller did not mean to pose is the one
 * mistake this could hide.
 */
public final class MapLayerScreenControls {

    private MapLayerScreenControls() {
    }

    /**
     * Says a control able to reverse a hide stands on the screen showing now, from which point that
     * screen's stored pick is what the mod acts on.
     */
    public static void standAControlOnTheShownScreen() {

        MapLayerScreens
            .resolveLivePicks()
            .layerVisibility()
            .recordControlAttached();
    }

    /** Returns both screens to the reading a run that has never put a control up gives. */
    public static void forgetControlsAttached() {
        MapLayerScreens.forgetControlsAttached();
    }

    /**
     * Forgets what either screen was showing, so a case posing a switched-off screen dissolves nothing
     * it did not put there itself.
     *
     * <p>The other process-wide thing a case can leave behind, and the one the running game never has
     * to take back: a screen holds what was last on it so a dissolve has a picture to finish, and in
     * play that is always caught on a drawn frame of the campaign now loaded. A suite poses a
     * switched-off screen directly, so what one case held is what the next would see dissolving.
     */
    public static void forgetDrawnLayers() {
        MapLayerScreens.forgetDrawnLayers();
    }
}
