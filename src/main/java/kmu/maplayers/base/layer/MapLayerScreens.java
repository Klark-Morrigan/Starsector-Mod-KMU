package kmu.maplayers.base.layer;

import kmlib.starsector.ui.intel.IntelScreenView;

/**
 * The two screens the map layers draw on - the sector map and the intel screen's visor - each with its
 * own picks, and which of the two the player is looking at this frame.
 *
 * <p>Apart from {@link MapLayerRegistry} because they answer different questions and change for
 * different reasons. The registry's roster is the set of layers that exist, settled once at startup and
 * the same for the whole process; this is where the player's own state lives, per screen, in the save,
 * moving whenever they touch a tab or a control. A reader wanting "what draws" asks the registry, and
 * one wanting "what has this screen been set to" asks here.
 *
 * <p>Each screen's pair is its own {@link PersistedActiveLayerSelection} and
 * {@link PersistedMapLayerVisibility} under its own frozen keys, so a switch or a hide on one screen
 * survives reload without moving the other's, and all four keys sit in one place. The two travel as one
 * {@link ScreenLayerPicks} so a screen is chosen once, at the site that names its keys, rather than at
 * each site wanting one of its two picks. Each screen's hide is handed out through
 * {@link ControlBackedMapLayerVisibility}, so a stored hide is acted on only while that screen has a
 * control able to reverse it - the difference between an optional decoration on the game's own chrome
 * and a load-bearing one.
 *
 * <p>Which screen is live is a per-frame question rather than a fixed answer: the same map widget draws
 * on both, so a pass reading one fixed screen's pick would paint the sector map's choice onto the intel
 * screen and ignore the tab the player is looking at. Only the intel screen is asked, since the two are
 * never up together, so "not the intel screen" is the sector map.
 */
public final class MapLayerScreens {

    // Save-serialised identity of each screen's active pick; frozen once shipped, since renaming one
    // silently resets every existing save under it to the default.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The same for each screen's show-or-hide pick, and frozen for the same reason.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // Each screen's show-or-hide pick as the rest of the mod reads it: the stored pick behind the rule
    // that a hide is acted on only while that screen has a control able to reverse it. Held as that
    // reading rather than as the stored pick, so no consumer can be handed the raw choice by accident -
    // the one caller entitled to it is named below.
    private static final ControlBackedMapLayerVisibility MAP_LAYER_VISIBILITY =
        new ControlBackedMapLayerVisibility(new PersistedMapLayerVisibility(MAP_LAYERS_SHOWN_KEY));
    private static final ControlBackedMapLayerVisibility INTEL_LAYER_VISIBILITY =
        new ControlBackedMapLayerVisibility(new PersistedMapLayerVisibility(INTEL_LAYERS_SHOWN_KEY));

    // Each screen's pair of picks, under that screen's own frozen keys. The map host draws through the
    // map pair and the overlay follows it; the intel host draws through the intel pair.
    private static final ScreenLayerPicks MAP_PICKS = new ScreenLayerPicks(
        new PersistedActiveLayerSelection(MAP_ACTIVE_LAYER_KEY),
        MAP_LAYER_VISIBILITY);
    private static final ScreenLayerPicks INTEL_PICKS = new ScreenLayerPicks(
        new PersistedActiveLayerSelection(INTEL_ACTIVE_LAYER_KEY),
        INTEL_LAYER_VISIBILITY);

    // Reads whether the intel screen is the one up. Null until the composition root supplies it, which
    // resolves every read to the map screen's pick - the answer to give before any screen is wired,
    // since the sector map is the overlay's home.
    private static IntelScreenView intelScreen;

    private MapLayerScreens() {
    }

    /**
     * Records the intel-screen seam that says which screen is up, so every read below can answer from
     * the picks of the screen the player is looking at. Called by the composition root, the one place a
     * concrete screen binding is named.
     *
     * @param intelScreen reads whether the intel screen is the one showing
     */
    public static void registerIntelScreen(IntelScreenView intelScreen) {
        MapLayerScreens.intelScreen = intelScreen;
    }

    /**
     * @return the map screen's picks, for the map host to draw through, switch and flip, so the host and
     *         the overlay read one pair rather than each resolving their own
     */
    public static ScreenLayerPicks getMapPicks() {
        return MAP_PICKS;
    }

    /**
     * @return the intel screen's picks, its own under its own keys, so the intel sidebar's tab and its
     *         show-or-hide state are independent of the map screen's and survive reload on their own
     */
    public static ScreenLayerPicks getIntelPicks() {
        return INTEL_PICKS;
    }

    /**
     * @return the picks of the screen showing this frame. The pair rather than either half, so a caller
     *         wanting both cannot answer one for a screen the other has already left - which would hide
     *         one screen's layers over the other's tab
     */
    public static ScreenLayerPicks resolveLivePicks() {
        return isIntelScreenLive()
            ? INTEL_PICKS
            : MAP_PICKS;
    }

    /**
     * @return whether the layers are picked to show on the screen showing this frame, answered the
     *         moment the pick flips. The crisp reading, for whatever has to stand down at once rather
     *         than ride the dissolve out - a control switched off must stop answering the player
     *         immediately, whatever is still fading off the screen
     */
    public static boolean areLayersShownOnLiveScreen() {
        return resolveLivePicks().layerVisibility().areLayersShown();
    }

    /**
     * @return how much of the showing screen's layers is on it this frame, 0 with them wholly hidden
     *         and 1 with them wholly shown, for a pass multiplying it into what it paints so the
     *         whole footprint thins together rather than one part snapping out from under another
     */
    public static float resolveShownFadeOnLiveScreen() {
        return resolveLivePicks().layerVisibility().resolveShownFade();
    }

    /**
     * @return the show-or-hide state of the screen showing this frame, for whatever stands a control on
     *         that screen's own chrome: the stored pick such a control shows and moves, and the word
     *         that one now stands there. One object for both halves, so a control cannot be bound to
     *         one screen and recorded against the other; which screen it belongs to is settled here, so
     *         the caller never has to ask
     */
    public static ControlBackedMapLayerVisibility resolveLayerControlOfLiveScreen() {
        return isIntelScreenLive()
            ? INTEL_LAYER_VISIBILITY
            : MAP_LAYER_VISIBILITY;
    }

    /**
     * Returns both screens to the reading a run that has never put a control up gives, whatever their
     * saves hold.
     *
     * <p>Both together, because the thing being unwound is a fact about the run rather than about
     * either screen: a caller unwinding one and not the other would leave an arrangement no session can
     * actually be in, one screen acting on its stored hide and the other refusing to.
     *
     * <p>Owed on every campaign load, which is what makes this the running game's business rather than
     * only a test's. These two are held for the process while the picks beneath them read the loaded
     * sector's memory, so a campaign hidden with a box standing would otherwise lend its word to the
     * next campaign loaded in the same session - which has no box on any row yet, and may turn out to
     * be unable to take one.
     */
    public static void forgetControlsAttached() {

        MAP_LAYER_VISIBILITY.forgetControlAttached();
        INTEL_LAYER_VISIBILITY.forgetControlAttached();
    }

    // Whether the intel screen is the one up, which is what "live screen" means above.
    private static boolean isIntelScreenLive() {
        return intelScreen != null && intelScreen.isIntelTabOpen();
    }
}
