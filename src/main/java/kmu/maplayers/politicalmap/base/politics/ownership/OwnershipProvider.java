package kmu.maplayers.politicalmap.base.politics.ownership;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;

/**
 * The source a political-map view resolves its per-system ownership from, so the render
 * pipeline reads ownership through one seam and never names a concrete resolver.
 *
 * <p>A view supplies its own provider the same way it supplies its grouping: the pass hands
 * the provider the sector, the once-sampled grouping, and the current filter selection, and
 * gets back the {@link OwnershipResolution} every later stage shapes, borders, and labels.
 * Lifting the resolver behind this seam makes a view that paints ownership derived some other
 * way a swapped-in provider rather than a branch in the pass.
 */
public interface OwnershipProvider {

    /**
     * Resolves who paints each star system for one build.
     *
     * @param sector         the sector whose economy the resolution reads; null yields empty
     *                       ownership
     * @param grouping       the view's grouping, sampled once for the whole pass, that collapses
     *                       factions into blocs before dominance is compared
     * @param selectedBlocId the spotlighted bloc's id, or null when no bloc is filtered - a
     *                       provider that spotlights reads it, one that does not ignores it
     * @return the ownership per system and the systems drawn hatched
     */
    OwnershipResolution resolveOwnership(
            SectorAPI sector, OwnershipGrouping grouping, String selectedBlocId);
}
