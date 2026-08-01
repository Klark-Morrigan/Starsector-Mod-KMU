package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * The source a political-map view resolves its per-system holder from, so the render
 * pipeline reads holders through one seam and never names a concrete resolver.
 *
 * <p>A view supplies its own provider the same way it supplies its grouping: the pass hands
 * the provider the sector, the once-sampled grouping, and the current filter selection, and
 * gets back the {@link HolderResolution} every later stage shapes, borders, and labels.
 * Lifting the resolver behind this seam makes a view that paints holding derived some other
 * way a swapped-in provider rather than a branch in the pass.
 */
public interface HolderProvider {

    /**
     * Resolves who paints each star system for one build.
     *
     * @param sector         the sector whose economy the resolution reads; null yields empty
     *                       holding
     * @param grouping       the view's grouping, sampled once for the whole pass, that collapses
     *                       factions into blocs before dominance is compared
     * @param selectedBlocId the spotlighted bloc's id, or null when no bloc is filtered - a
     *                       provider that spotlights reads it, one that does not ignores it
     * @return the holding per system and the systems drawn hatched
     */
    HolderResolution resolveHolder(
        SectorAPI sector,
        HolderGrouping grouping,
        String selectedBlocId);
}
