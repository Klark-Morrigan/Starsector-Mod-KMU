package kmu.maplayers.base.layer;

import java.util.List;

/**
 * The layers one screen offers as tabs, and what a control standing on that screen's own chrome does to a
 * pick the screen stops offering.
 *
 * <p>{@link NoLayer} is a tab whose whole job is to draw nothing, so a screen that has grown a tick box on
 * the game's own filter row carries two controls for one thought - and the box is the more discoverable of
 * the two, standing where the player already looks for "show or hide this map furniture". Its tab is
 * withheld where that box stands, and it is the fallback everywhere else: whether a screen gets a box is
 * settled afresh each session and can be settled no, so a strip that withheld the last way of emptying a
 * map on a screen the box never reached would leave the player nothing at all.
 *
 * <p>Withheld from the strip and never from the roster. A save's pick is a layer id resolved against the
 * registered layers, so a roster with that tab filtered out of it would read a save left on it as a stale
 * id and fall back to the default - which is to say, start painting over the map of a player who asked for
 * nothing.
 *
 * <p>One read for both the strip and the shortcut walk, because the two index the same list by
 * construction: a strip that withheld a tab while the key walk did not would switch to the layer one along
 * from the tab it lit.
 *
 * <p>A pick already sitting on a withheld tab is moved rather than left standing. Left, the two controls
 * would disagree in the one way that cannot be read off the screen: the map is empty because of the pick,
 * while the box - which has nothing to do with which layer is picked - stands ticked and says the layers
 * are shown. What the player chose is kept, expressed instead through the control that can reverse it.
 */
public final class ScreenLayerTabs {

    private ScreenLayerTabs() {
    }

    /**
     * @param screenPicks the screen's own picks, which say whether it has a control of its own
     * @return the layers this screen offers as tabs, in roster order - the whole roster on a screen with
     *         no control, and the roster without the empty view on a screen that has one
     */
    public static List<MapLayer> resolveTabbedLayers(ScreenLayerPicks screenPicks) {

        var registeredLayers = MapLayerRegistry.getLayers();
        if (!screenPicks.layerVisibility().hasControlBeenAttached()) {
            return registeredLayers;
        }
        return withholdEmptyViewTab(registeredLayers);
    }

    /**
     * Moves a pick this screen no longer offers to the default layer, and stores the empty map it stood
     * for as a hide - so the picture is unchanged and what the player chose is now held by the control
     * that can reverse it.
     *
     * <p>Owed once, on the first control to stand on a screen; a pick the strip still offers is left
     * alone, which is every other call.
     *
     * @param screenPicks the screen's own picks, both of which this may write
     */
    public static void migratePickOffWithheldTab(ScreenLayerPicks screenPicks) {

        if (!isPickWithheldOnceControlStands(screenPicks)) {
            return;
        }
        screenPicks.layerSelection().selectLayer(MapLayerRegistry.getDefaultLayer());
        screenPicks.layerVisibility().showLayers(false);
    }

    // Whether this screen's pick is one the strip stops offering once a control stands on it. Stated as
    // "not among the tabs" rather than as "is the empty view", so it cannot drift from what the strip
    // actually offers - the guard below included, which leaves a lone tab offered and so unmigrated.
    //
    // The pick is read off the selection rather than through the registry, which folds hiding into its
    // own answer: what is asked here is which tab the screen is set to, and a screen already hiding its
    // layers is set to one just the same.
    private static boolean isPickWithheldOnceControlStands(ScreenLayerPicks screenPicks) {

        var pick = screenPicks.layerSelection().getActiveLayer();

        return pick != null
            && !withholdEmptyViewTab(MapLayerRegistry.getLayers()).contains(pick);
    }

    // The roster without the empty view's tab, unless that would leave no tab at all: an empty strip has
    // no way back to itself, so the last one standing is offered whatever else is true. Unreachable while
    // a layer that paints is registered beside it, which is a fact about a composition root rather than
    // about this rule.
    private static List<MapLayer> withholdEmptyViewTab(List<MapLayer> registeredLayers) {

        var offeredLayers = registeredLayers.stream()
            .filter(layer -> layer != NoLayer.INSTANCE)
            .toList();

        return offeredLayers.isEmpty() ? registeredLayers : offeredLayers;
    }
}
