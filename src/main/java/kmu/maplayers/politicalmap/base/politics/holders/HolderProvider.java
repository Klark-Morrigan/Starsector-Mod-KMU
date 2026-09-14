package kmu.maplayers.politicalmap.base.politics.holders;

import kmu.maplayers.politicalmap.base.dominance.HolderPass;

/**
 * The source a political-map view resolves its per-system holder from, so the render
 * pipeline reads holders through one seam and never names a concrete resolver.
 *
 * <p>A view supplies its own provider the same way it supplies its grouping: the rebuild hands
 * the provider the pass it opened and the current filter selection, and gets back the
 * {@link HolderResolution} every later stage shapes, borders, and labels. Lifting the resolver
 * behind this seam makes a view that paints holding derived some other way a swapped-in provider
 * rather than a branch in the rebuild.
 *
 * <p>What is handed over is the layer-generic {@link HolderPass} rather than any one mechanic's
 * pass, and that is the whole of what makes the seam implementable twice: a provider painting by
 * markets, by claims, or by a diplomatic relation all need the same reading of the sector - which
 * sector, the grouping, the reveal, and the one walk of each system - while the rule that decides
 * a winner is the provider's own to name. Handing the pass over rather than the sector is also
 * what makes two providers behind one view share a walk instead of taking one apiece.
 */
public interface HolderProvider {

    /**
     * Resolves who paints each star system for one build.
     *
     * @param pass           the rebuild's reading of the sector, opened once where the rebuild
     *                       began; a pass over no sector yields empty holding
     * @param selectedBlocId the spotlighted bloc's ID, or null when no bloc is filtered - a
     *                       provider that spotlights reads it, one that does not ignores it
     * @return the holding per system and the systems drawn hatched
     */
    HolderResolution resolveHolder(HolderPass pass, String selectedBlocId);
}
