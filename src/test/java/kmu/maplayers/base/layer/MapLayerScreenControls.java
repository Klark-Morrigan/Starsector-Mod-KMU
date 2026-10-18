package kmu.maplayers.base.layer;

/**
 * Says whether a screen has a control able to reverse a hide.
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
     *
     * @param screens the screens the control stands on one of
     */
    public static void standAControlOnTheShownScreen(MapLayerScreens screens) {

        screens
            .resolveLivePicks()
            .layerVisibility()
            .recordControlAttached();
    }
}
