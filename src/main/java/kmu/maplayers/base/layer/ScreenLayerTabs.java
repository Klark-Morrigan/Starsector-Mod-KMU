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
 * <p>The player's own arrangement is laid over the roster first, so the row is in the order they put it in
 * and without the tabs they took off, and the withholding then applies to that row rather than to the
 * roster: the empty view's tab is taken over by a screen's own control whether or not the player has moved
 * it. The two last-tab-standing guards compose into one that way - a row emptied by hiding and a row
 * emptied by withholding are the same unusable bar, and what is left standing either way is the leading tab
 * of the player's own row.
 *
 * <p>One read for both the strip and the shortcut walk, because the two index the same list by
 * construction: a strip that withheld or moved a tab while the key walk did not would switch to the layer
 * one along from the tab it lit.
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
     * @return the layers this screen offers as tabs, in the player's own order and without the tabs they
     *         took off - and without the empty view besides, on a screen carrying a control of its own
     */
    public static List<MapLayer> resolveTabbedLayers(ScreenLayerPicks screenPicks) {

        var arrangedLayers = ArrangedLayers.arrangeVisibleLayers(
            LiveMapLayerArrangement.resolveArrangement(),
            MapLayerRegistry.getLayers());

        if (!screenPicks.layerVisibility().hasControlBeenAttached()) {
            return arrangedLayers;
        }
        // The player's row rather than the roster, so a bar left with nothing standing falls back to
        // the leading tab of their own order rather than to one they never put there.
        return withholdEmptyViewTab(arrangedLayers);
    }

    /**
     * Moves a pick a control on this screen has taken over to the default layer, and stores the empty map
     * it stood for as a hide - so the picture is unchanged and what the player chose is now held by the
     * control that can reverse it.
     *
     * <p>Owed once, on the first control to stand on a screen; a pick nothing has taken over is left
     * alone, which is every other call. A pick whose tab the player took off the bar themselves is left
     * alone too, that being a tab they hid rather than a layer they switched off.
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
    // withholds - the guard below included, which leaves a lone tab offered and so unmigrated.
    //
    // Asked of the roster rather than of the arranged row, which is the one place the two part company:
    // taking a tab off the bar is not switching a layer off, so a pick the player has hidden goes on
    // painting and must not be moved - while a pick the withholding took has a control standing in its
    // place, which is the whole reason to move it.
    //
    // The pick is read off the selection rather than through the registry, which folds the screen's hide
    // into its own answer: what is asked here is which tab the screen is set to, and a screen already
    // hiding its layers is set to one just the same.
    private static boolean isPickWithheldOnceControlStands(ScreenLayerPicks screenPicks) {

        var pick = screenPicks.layerSelection().getActiveLayer();

        return pick != null
            && !withholdEmptyViewTab(MapLayerRegistry.getLayers()).contains(pick);
    }

    // The given row without the empty view's tab, unless that would leave no tab at all: a row with no
    // tabs has no way back to itself, so the last one standing is offered whatever else is true. Which
    // row is asked about is each caller's, and the two ask about different ones deliberately.
    private static List<MapLayer> withholdEmptyViewTab(List<MapLayer> offeredLayers) {

        var paintingLayers = offeredLayers.stream()
            .filter(layer -> layer != NoLayer.INSTANCE)
            .toList();

        return paintingLayers.isEmpty() ? offeredLayers : paintingLayers;
    }
}
