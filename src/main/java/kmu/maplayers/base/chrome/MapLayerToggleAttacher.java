package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;

import kmu.maplayers.base.layer.MapLayerVisibility;

/**
 * Puts the map layers' own tick box on a map's filter row and binds it to one screen's show-or-hide
 * pick: the box opens showing the state it is handed, and a click on it moves that pick.
 *
 * <p>The pick travels with the row rather than being read where the box is built, because a row
 * belongs to a screen and a pick belongs to the same one. Handed the pair, this cannot put a box on
 * one screen's row that moves the other screen's layers - which is the one way an attachment could
 * be wrong without looking wrong.
 *
 * <p>A seam rather than the write itself. What stands a control on somebody else's widget is a
 * reach into the running game's own tree, and the handle it answers with is made by that reach and
 * by nothing else - so keeping it behind this leaves the decision of <em>when</em> to attach
 * separable from the one thing about attaching that only a running game can carry out.
 *
 * <p>Refusal is an answer rather than a failure. A row with no room left, or one whose shape no
 * longer builds a button this can drive, leaves the screen without a control and the row exactly as
 * it was found - so a caller has one thing to do about every way of not attaching.
 */
public interface MapLayerToggleAttacher {

    /**
     * Stands a tick box on {@code row}, opening it at {@code areLayersShownAtFirst} and driving
     * {@code layerVisibility} from then on.
     *
     * @param row                   the row to put it on, which is the one on screen
     * @param layerVisibility       the show-or-hide pick of the screen that row belongs to, which a click
     *                              on the box moves
     * @param areLayersShownAtFirst what the box opens showing. Handed over rather than read off the pick,
     *                              because standing a box can itself settle the screen it stands on: one
     *                              seeded from what that pick read a moment earlier would open on the
     *                              state the screen is leaving, and go on saying so until it was used
     *                              twice
     * @return whether a control is now standing on that row
     */
    boolean attachToggleTo(
        MapFilterRow row,
        MapLayerVisibility layerVisibility,
        boolean areLayersShownAtFirst);
}
