package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import java.util.Set;

/**
 * The spotlight state one build resolved and then paints and names the sector under: which
 * bloc is spotlighted, how every other bloc recedes around it, which of the spotlit bloc's
 * systems it holds but does not dominate, and which settled systems it lives in without this
 * build giving it any hold on them at all.
 *
 * <p>The last two are both "the pick is here but does not own the place", split by whether the
 * holder map reached the system. A contested system is inside the spotlit territory and needs a
 * fill telling it from the solid ones; a present-unheld system is outside every territory - the
 * holding rule could not attribute it to anyone - and needs only to be spared the recede.
 *
 * <p>Held on the built territories so an incremental re-shape and the label rebuild recede a
 * cell and name a spotlight cluster exactly as the full build did. Off filter it is the inert
 * default - no selected bloc, the identity recede, neither set populated - so a normal pass
 * paints every bloc untouched.
 */
public record FilterSnapshot(
    String selectedBlocId,
    ElementStyleAdjustment recedeAdjustment,
    Set<String> contestedSystemIds,
    Set<String> spotlitPresenceSystemIds) {

    // The inert default: nothing spotlighted, so nothing recedes, no system is contested, and no
    // system carries the pick's unheld presence. Named here rather than spelt out at each pass
    // that has no filter, so "off filter" is one value every such pass shares and cannot get
    // subtly wrong.
    public static FilterSnapshot unfiltered() {
        return new FilterSnapshot(null, ElementStyleAdjustment.NONE, Set.of(), Set.of());
    }

    // A build spotlights a bloc exactly when one was selected, so the shared cell and faction
    // builders bypass the view's per-bloc styling for the filter's presence-aware rules.
    public boolean isFiltering() {
        return selectedBlocId != null;
    }
}
