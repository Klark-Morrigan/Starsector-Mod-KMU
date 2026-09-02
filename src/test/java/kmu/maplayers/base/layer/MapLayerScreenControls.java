package kmu.maplayers.base.layer;

/**
 * Says whether a screen has a control able to reverse a hide, and takes the saying back.
 *
 * <p>{@link MapLayerScreens} is static and the record is a latch that never clears itself, so a
 * class that stands a control would otherwise leave every later one in the JVM acting on stored
 * hides where a run with no control reads the layers shown. Whether that showed up at all would then
 * depend on which class happened to run first, which is the failure this exists to remove.
 *
 * <p>Reached through here rather than off {@link MapLayerScreens} directly, because taking the
 * record back is not something the running game ever asks for: no event ends a run, so a game that
 * could unwind the latch would be answering a question nothing puts to it. Sitting at this package's
 * own name is what lets that stay shut away from the mod while every class that needs it, wherever
 * it lives, can still reach it - the arrangement {@link MapLayerRosters} already uses for the roster
 * beside it.
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
            .resolveLayerControlOfLiveScreen()
            .recordControlAttached();
    }

    /** Returns both screens to the reading a run that has never put a control up gives. */
    public static void forgetControlsAttached() {
        MapLayerScreens.forgetControlsAttached();
    }
}
