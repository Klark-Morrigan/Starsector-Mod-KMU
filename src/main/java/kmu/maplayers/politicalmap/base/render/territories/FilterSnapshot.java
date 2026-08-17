package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.base.theme.ElementStyleAdjustment;

import java.util.Set;

/**
 * The spotlight state one build resolved and then paints and names the sector under: which
 * bloc is spotlighted, how every other bloc recedes around it, and which of the spotlit bloc's
 * systems it holds but does not dominate.
 *
 * <p>A contested system is inside the spotlit territory and needs a fill telling it from the
 * solid ones. The other half of "the pick is here but does not own the place" - a system the
 * holding rule could attribute to nobody, which needs only to be spared the recede - is not
 * here: it moves between rebuilds as colonies come and go, so it is held live beside the holder
 * map rather than in this snapshot.
 *
 * <p>Held on the built territories so an incremental re-shape and the label rebuild recede a
 * cell and name a spotlight cluster exactly as the full build did. Off filter it is the inert
 * default - no selected bloc, the identity recede, no contested system - so a normal pass
 * paints every bloc untouched.
 */
public record FilterSnapshot(
    String selectedBlocId,
    ElementStyleAdjustment recedeAdjustment,
    Set<String> contestedSystemIds) {

    // The inert default: nothing spotlighted, so nothing recedes and no system is contested.
    // Named here rather than spelt out at each pass that has no filter, so "off filter" is one
    // value every such pass shares and cannot get subtly wrong.
    public static FilterSnapshot unfiltered() {
        return new FilterSnapshot(null, ElementStyleAdjustment.NONE, Set.of());
    }

    // A build spotlights a bloc exactly when one was selected, so the shared cell and faction
    // builders bypass the view's per-bloc styling for the filter's presence-aware rules.
    public boolean isFiltering() {
        return selectedBlocId != null;
    }
}
