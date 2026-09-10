package kmu.maplayers.base.layer;

import java.util.List;

/**
 * The layers one screen offers as tabs, and what becomes of a pick that screen has stopped offering a tab
 * for.
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
 * <p>A pick the offered row has no tab for is moved onto the leading tab it does offer, and that screen's
 * show-or-hide control set to whether the tab it lands on paints. Both halves, because the two controls
 * have to end up saying one thing: a layer painting from a tab that is not on the bar is a map nothing on
 * screen accounts for, and a lit tab standing over an empty map is that same disagreement from the other
 * end. It reaches a tab the player took off as well as one a control took over, and the first of those
 * deliberately reverses the older reading that hiding a tab is not switching a layer off - true of the
 * store, where the layer stays registered and its id resolvable, and blind to the bar the player is
 * looking at.
 *
 * <p>Both halves settle together and do not land together: switching off is immediate and the pick waits
 * out the dissolve that begins, so what is leaving the screen is seen to leave. Which frame each write
 * falls on is {@link #healPickOntoOfferedTabs}'s.
 */
public final class ScreenLayerTabs {

    // The end of a screen's hide ramp: none of its layers left on it, which is where a pick is free to move
    // without cutting anything away.
    private static final float FULLY_HIDDEN = 0f;

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
     * What the row above is composed from this frame, for a standing pass that has to ask whether it
     * could have moved rather than build it again to find out.
     *
     * <p>Every ingredient the method above reads, and nothing else: what a caller does with this is
     * compare it against the one it last acted on, so an ingredient missing from it is a change no
     * caller would see. That is why it is built here rather than assembled by whoever wants it.
     *
     * @param screenPicks the screen whose row is being asked about
     * @return this frame's revision of that screen's offered row
     */
    public static OfferedTabsRevision readOfferedTabsRevision(ScreenLayerPicks screenPicks) {

        return new OfferedTabsRevision(
            LiveMapLayerArrangement.resolveArrangement(),
            MapLayerRegistry.getLayers(),
            screenPicks.layerVisibility().hasControlBeenAttached());
    }

    /**
     * Moves a pick this screen offers no tab for onto the leading tab it does offer, and sets that
     * screen's own show-or-hide control to whether the tab it lands on paints - so the bar, the map and
     * the control end up saying one thing.
     *
     * <p>Asked every frame rather than at the moment a row changes. One dialog moves both screens' rows
     * at once, a screen nobody is looking at still has to be right when they next look, and the pass that
     * lays a row out may not write. A pick the row still offers is left alone, which is every call but
     * the ones just after something moved.
     *
     * <p>The two writes are not one moment. Switching the screen off is immediate, so the box the player
     * can see says what is happening from that frame on; landing the pick waits until the ramp the
     * switch-off started has run out. Both orders leave the same settled state and only one of them can be
     * watched: a pick moved onto a tab that paints nothing takes the picture off the screen in the frame
     * the dissolve begins, leaving the ramp nothing to dissolve. Left where it is, the outgoing layer goes
     * the way the player's own hide takes it.
     *
     * @param screenPicks the screen's own picks, both of which this may write
     * @return whether that screen is now on a tab its row offers. False where the move did not take -
     *         a selection with nowhere to write drops it silently, and one waiting on a dissolve has not
     *         been tried yet - so a caller holding off until the row next moves knows not to hold off on
     *         this one
     */
    public static boolean healPickOntoOfferedTabs(ScreenLayerPicks screenPicks) {

        var offeredLayers = resolveTabbedLayers(screenPicks);

        // Nothing registered, so the bar is bare too: there is no tab to land on, and no pick that could
        // be disagreeing with one. Settled rather than unsettled - nothing here is owed a second look.
        if (offeredLayers.isEmpty()) {
            return true;
        }
        var pick = screenPicks.layerSelection().getActiveLayer();

        // A null pick counts as one the row does not offer, so a selection seam that answers nothing over
        // a populated bar is landed on a tab rather than left lighting none.
        if (pick != null && offeredLayers.contains(pick)) {
            return true;
        }
        var leadingTab = offeredLayers.get(0);
        var visibility = screenPicks.layerVisibility();
        var doesLeadingTabPaint = isLayerPainting(leadingTab);

        visibility.showLayers(doesLeadingTabPaint);

        // Only a landing on a tab that paints nothing has a dissolve to wait for, and it waits: the frame
        // the pick moves is the frame the outgoing layer stops being drawn, so landing it now would cut
        // away what the switch-off above just began dissolving. A landing on a tab that paints is a switch
        // between two pictures rather than a picture leaving, so it takes effect at once.
        if (!doesLeadingTabPaint && isScreenStillFadingOut(visibility)) {
            return false;
        }

        screenPicks.layerSelection().selectLayer(leadingTab);

        // Read back rather than assumed. A pick persisted in sector memory drops the write where there
        // is no memory to write into, and a caller that took the attempt for the outcome would record
        // this screen as settled and never come back to a bar still lit wrong.
        return screenPicks.layerSelection().getActiveLayer() == leadingTab;
    }

    // Whether any of this screen's layers is still on it while switched off - the tail of a dissolve, which
    // is the one state a pick must not move during.
    //
    // The crisp pick is read first and the ramp only where the answer could still turn on it: a screen that
    // is shown, or one with no control of its own to be switched off from, is settled by a flag read, and
    // the fade behind it is a clock read with a settings read behind that.
    private static boolean isScreenStillFadingOut(MapLayerVisibility visibility) {
        return !visibility.areLayersShown() && visibility.resolveShownFade() > FULLY_HIDDEN;
    }

    // The given row without the empty view's tab, unless that would leave no tab at all: a row with no
    // tabs has no way back to itself, so the last one standing is offered whatever else is true.
    private static List<MapLayer> withholdEmptyViewTab(List<MapLayer> offeredLayers) {

        var paintingLayers = offeredLayers.stream()
            .filter(ScreenLayerTabs::isLayerPainting)
            .toList();

        return paintingLayers.isEmpty() ? offeredLayers : paintingLayers;
    }

    // Whether a layer draws anything at all. One reading for both the tab the empty view's control takes
    // over and the state a pick landing on that tab settles the control at, so the two cannot drift into
    // a control saying the layers are shown over the one tab whose whole job is to show none.
    private static boolean isLayerPainting(MapLayer layer) {
        return layer != NoLayer.INSTANCE;
    }
}
