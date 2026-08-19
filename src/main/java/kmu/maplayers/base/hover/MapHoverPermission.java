package kmu.maplayers.base.hover;

import kmlib.starsector.ui.coreui.CampaignScreenView;
import kmlib.starsector.ui.map.presence.MapPresence;

import java.util.function.BooleanSupplier;

/**
 * Which frames the map layers may answer the cursor on, over the screen a running game is actually
 * showing.
 *
 * <p>{@link MapHoverGates#isCursorLocatableOn} states the rule and stays free of any live read, so
 * it can be pinned against settings alone. This binds the two screen reads that rule needs - a
 * vanilla map host being up, and the player looking at the campaign world itself - and is what a
 * caller in a running game holds.
 *
 * <p>One class rather than the same pair of method references composed at each wiring site, because
 * more than one feature turns on this answer and they must not drift: the pass that resolves the
 * hover and the box that reports it have to agree about which frames may answer the cursor, or the
 * map lights a cell it will not name, or names one it did not light. A second composition would be
 * free to bind one of the two reads differently, and nothing would say so.
 *
 * <p>Both reads are live and taken per ask, like the switches behind them, so a player toggling a
 * permission or opening a screen sees the map change on the next frame.
 */
public final class MapHoverPermission {

    private final BooleanSupplier isAnyMapShowing;

    private final BooleanSupplier isInGameSpace;

    /**
     * @param isAnyMapShowing whether either vanilla map host is showing, from
     *                        {@code MapPresence#isAnyMapShowing}
     * @param isInGameSpace   whether the player is looking at the campaign world with no screen over
     *                        it, from {@code CampaignScreenView#isShowingGameSpace}
     */
    public MapHoverPermission(BooleanSupplier isAnyMapShowing, BooleanSupplier isInGameSpace) {
        this.isAnyMapShowing = isAnyMapShowing;
        this.isInGameSpace = isInGameSpace;
    }

    /**
     * @return the permission over the screen reads a running game has
     */
    public static MapHoverPermission createForLiveScreen() {

        var mapPresence = new MapPresence();

        return new MapHoverPermission(
            mapPresence::isAnyMapShowing,
            CampaignScreenView::isShowingGameSpace);
    }

    /**
     * @return whether the cursor can be located against the frame now running - the player's
     *         permissions read against what is on screen
     */
    public boolean isCursorLocatable() {
        return MapHoverGates.isCursorLocatableOn(isAnyMapShowing, isInGameSpace);
    }
}
