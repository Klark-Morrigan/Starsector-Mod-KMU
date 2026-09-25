package kmu.maplayers.ownermap.render.labels;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ViewGrouping;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import java.util.Map;

/**
 * Everything one label rebuild resolves a cluster's colour and name from: who holds each system,
 * the shades a desaturated bloc recolours to, the view and grouping the holding was classified
 * under, and the picks the pass was baked under - the spotlight the rest of the sector recedes by,
 * and the format a name is spelled in.
 *
 * <p>Held as one value because the four have to describe the same moment. A rebuild that named
 * blocs under this pass's view while receding them by the last pass's filter would produce
 * labels that disagree with the fills beneath them, and the disagreement would show as a colour,
 * not as an error. Passing them one by one is what makes that possible; passing the snapshot
 * makes it unrepresentable.
 *
 * <p>The two paths that produce one differ in where the holding comes from - the production
 * build hands over the clusters it just built, the border-tracing diagnostic resolves holders
 * straight from the sector and spotlights nothing - and agree on everything downstream, which is
 * the point of them meeting in one type.
 *
 * @param holderBySystemKey   the holder of each drawn system, keyed by {@link SystemKey}
 * @param desaturationPalette the shared shades a desaturated bloc's name recolours to
 * @param viewGrouping        the view painted and the grouping snapshot holding was resolved under
 * @param contentInputs       the picks the pass was baked under: the spotlight names and shades
 *                            recede by, and the name format they are spelled and fitted in
 */
public record ClusterLabelStylingSnapshot(
    Map<SystemKey, SystemOwner> holderBySystemKey,
    FactionPalette desaturationPalette,
    ViewGrouping viewGrouping,
    ContentInputs contentInputs) {

    /**
     * The snapshot a production rebuild styles by: the clusters' own retained state, read off
     * the one build whose fills and borders the labels must match.
     *
     * @param clusters the clusters built this pass
     * @return the styling snapshot those clusters were built under
     */
    public static ClusterLabelStylingSnapshot resolveFrom(OwnerMapClusters clusters) {

        // The holders off the live occupancy, which the refresh folds, and the other three off
        // the inputs the build was baked under, which stand until the next rebuild.
        var buildInputs = clusters.getBuildInputs();

        return new ClusterLabelStylingSnapshot(
            clusters.getOccupancy().getHolderBySystemKey(),
            buildInputs.styling().desaturationPalette(),
            buildInputs.viewGrouping(),
            buildInputs.contentInputs());
    }
}
