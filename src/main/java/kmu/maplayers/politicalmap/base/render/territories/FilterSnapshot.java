package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;

import java.util.Set;

/**
 * The spotlight state one build resolved and then paints and names the sector under: which
 * bloc is spotlighted, how every other bloc recedes around it, and which of the spotlit
 * bloc's systems it holds but does not dominate.
 *
 * <p>Held on the built territories so an incremental re-shape and the label rebuild recede a
 * cell and name a spotlight cluster exactly as the full build did. Off filter it is the inert
 * default - no selected bloc, the identity recede, no contested systems - so a normal pass
 * paints every bloc untouched.
 */
public record FilterSnapshot(
        String selectedBlocId,
        BlocStyleAdjustment recedeAdjustment,
        Set<String> contestedSystemIds) {

    // A build spotlights a bloc exactly when one was selected, so the shared cell and faction
    // builders bypass the view's per-bloc styling for the filter's presence-aware rules.
    public boolean isFiltering() {
        return selectedBlocId != null;
    }
}
