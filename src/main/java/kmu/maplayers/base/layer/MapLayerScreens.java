package kmu.maplayers.base.layer;

import kmlib.starsector.ui.intel.IntelScreenView;

import java.util.List;

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
 * <p>Each screen is named once here, as the {@link ScreenMemoryScope} every one of its keys is composed
 * through, and each screen's pair is its own {@link PersistedActiveLayerSelection} and
 * {@link PersistedMapLayerVisibility} built under that scope, so a switch or a hide on one screen survives
 * reload without moving the other's. What is named here is the screens; each preference's own base key
 * belongs to the holder that owns it, which is the only arrangement available to the ones this package may
 * not import. The picks and the scope travel as one {@link ScreenLayerPicks} so a screen is chosen once, at
 * the site that names it, rather than at each site wanting one of its picks or a key for a preference of
 * its own. Each screen's hide is handed out through {@link ControlBackedMapLayerVisibility}, so a stored
 * hide is acted on only while that screen has a control able to reverse it - the difference between an
 * optional decoration on the game's own chrome and a load-bearing one.
 *
 * <p>Which screen is live is a per-frame question rather than a fixed answer: the same map widget draws
 * on both, so a pass reading one fixed screen's pick would paint the sector map's choice onto the intel
 * screen and ignore the tab the player is looking at. Only the intel screen is asked, since the two are
 * never up together, so "not the intel screen" is the sector map.
 */
public final class MapLayerScreens {

    // The two screens as the save knows them. Every per-screen key resolves through one of these, so the
    // segment has one spelling and a screen is named here and nowhere else.
    private static final ScreenMemoryScope MAP_SCOPE = new ScreenMemoryScope("map");
    private static final ScreenMemoryScope INTEL_SCOPE = new ScreenMemoryScope("intel");

    // Each screen's show-or-hide pick as the rest of the mod reads it: the stored pick behind the rule
    // that a hide is acted on only while that screen has a control able to reverse it. Held as that
    // reading rather than as the stored pick, so no consumer can be handed the raw choice by accident -
    // the one entitled to it is whatever stands the control, and it asks the reading for it by name.
    private static final ControlBackedMapLayerVisibility MAP_LAYER_VISIBILITY =
        new ControlBackedMapLayerVisibility(new PersistedMapLayerVisibility(MAP_SCOPE));
    private static final ControlBackedMapLayerVisibility INTEL_LAYER_VISIBILITY =
        new ControlBackedMapLayerVisibility(new PersistedMapLayerVisibility(INTEL_SCOPE));

    // Each screen's picks, built under that screen's own scope, with the scope beside them. The map host
    // draws through the map value and the overlay follows it; the intel host draws through the intel value.
    private static final ScreenLayerPicks MAP_PICKS = new ScreenLayerPicks(
        new PersistedActiveLayerSelection(MAP_SCOPE),
        MAP_LAYER_VISIBILITY,
        MAP_SCOPE);
    private static final ScreenLayerPicks INTEL_PICKS = new ScreenLayerPicks(
        new PersistedActiveLayerSelection(INTEL_SCOPE),
        INTEL_LAYER_VISIBILITY,
        INTEL_SCOPE);

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
     * Every screen's picks, for a pass that has to act on all of them rather than on the one being
     * looked at - a self-heal clearing a stored choice that lapsed for both panels at once, since
     * something that stopped being on offer stopped being on offer wherever it was picked.
     *
     * <p>Answered here rather than assembled by such a caller, so how many screens there are stays a
     * fact this class holds. A caller naming the two itself is a second place that would have to learn
     * about a third.
     *
     * @return the picks of both screens, the map screen's first
     */
    public static List<ScreenLayerPicks> getAllScreenPicks() {
        return List.of(MAP_PICKS, INTEL_PICKS);
    }

    /**
     * @return the map screen's picks, for the map host to draw through, switch and flip, so the host and
     *         the overlay read one value rather than each resolving their own
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
     * Resolves the picks of the screen showing this frame.
     *
     * <p>The sector map is the primary screen, and the answer wherever the intel screen is not up: a pass
     * drawing the map somewhere the mod put no sidebar - another mod's minimap, a tooltip map, any foreign
     * widget compositing the map - reads the sector map's picks and preferences. The intel visor reads its
     * own. Only the intel screen is asked, so a surface the mod does not know about cannot land on a
     * screen of its own with nothing set for it.
     *
     * @return the whole value rather than any one part of it, so a caller wanting the tab, the hide and
     *         the scope together cannot answer one for a screen the others have already left - which would
     *         hide one screen's layers over the other's tab, or paint one screen's preferences under the
     *         other's. It is also what a control on a screen's own chrome is stood through: the tab and the
     *         hide are both its business, one to take over and one to move
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

    /**
     * Forgets what either screen was showing, leaving the reading screens that have never drawn give.
     *
     * <p>Package-private, and the running game asks it of nothing. What a screen was showing is only
     * ever consulted while that screen is switched off and part-way through a dissolve, and a campaign
     * load takes both controls back - so both screens read as shown until a box stands again, and the
     * first frame of a new campaign catches its own pick before any dissolve could ask for one. What
     * needs this is a process that poses a switched-off screen without drawing one first, which is a
     * suite rather than a session.
     */
    static void forgetDrawnLayers() {

        MAP_PICKS.drawnLayer().forgetDrawnLayer();
        INTEL_PICKS.drawnLayer().forgetDrawnLayer();
    }

    // Whether the intel screen is the one up, which is what "live screen" means above.
    private static boolean isIntelScreenLive() {
        return intelScreen != null && intelScreen.isIntelTabOpen();
    }
}
