package kmu.maplayers.base.layer;

/**
 * Screen scopes for the suites that have to compose a screen's picks without being about which screen
 * they are composing.
 *
 * <p>A stand-in rather than one of the two live screens, since a case handed a live scope reads as being
 * about that screen: what those two compose is {@link MapLayerScreens}' to answer and is pinned there, so
 * a case elsewhere naming one would pin it twice and drag the map screen's spelling into suites that have
 * nothing to do with it. The same arrangement {@link MapLayerRosters} and {@link MapLayerScreenControls}
 * use for the state beside it.
 */
public final class ScreenMemoryScopes {

    private ScreenMemoryScopes() {
    }

    /** A screen of no particular identity, for picks whose subject is anything but which screen holds them. */
    public static ScreenMemoryScope createStandInScreen() {
        return new ScreenMemoryScope("test");
    }

    /**
     * A second screen of no particular identity, for the cases whose subject is that two screens hold a
     * pick apart. Distinct from the first and nothing else: which two screens the mod actually has is
     * {@link MapLayerScreens}' answer, and a case pinning "these two read apart" needs only that they
     * are two.
     *
     * @return a stand-in screen that is not {@link #createStandInScreen()}'s
     */
    public static ScreenMemoryScope createOtherStandInScreen() {
        return new ScreenMemoryScope("other");
    }
}
