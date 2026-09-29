package kmu.maplayers.ownermap.render.labels;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.OwnerCategories;

import java.util.Map;

/**
 * Everything one label rebuild resolves a cluster's colour and name from: who owns each system, the
 * shades a desaturated owner recolours to, the painting layer's categories and its reading of its
 * owners, and the picks the pass was baked under - the spotlight the rest of the sector recedes by,
 * and the format a name is spelled in.
 *
 * <p>Held as one value because the parts have to describe the same moment. A rebuild that named
 * owners under this pass's reading while receding them by the last pass's filter would produce
 * labels that disagree with the fills beneath them, and the disagreement would show as a colour,
 * not as an error. Passing them one by one is what makes that possible; passing the snapshot
 * makes it unrepresentable.
 *
 * <p>The two paths that produce one differ in where the holding comes from - the production
 * build hands over the clusters it just built, the border-tracing diagnostic resolves owners
 * straight from the sector and spotlights nothing - and agree on everything downstream, which is
 * the point of them meeting in one type.
 *
 * @param holderBySystemKey   the owner of each drawn system, keyed by {@link SystemKey}
 * @param desaturationPalette the shared shades a desaturated owner's name recolours to
 * @param categories          the painting layer's categories: the full-strength one a spotlit
 *                            owner's name is held in, and each category's name style
 * @param reading             the painting layer's answers about its owners - each one's name,
 *                            category and recede
 * @param contentInputs       the picks the pass was baked under: the spotlight names and shades
 *                            recede by, and the name format they are spelled and fitted in
 */
public record ClusterLabelStylingSnapshot(
    Map<SystemKey, SystemOwner> holderBySystemKey,
    OwnerPalette desaturationPalette,
    OwnerCategories categories,
    OwnerReading reading,
    ContentInputs contentInputs) {

    /**
     * The snapshot a production rebuild styles by: the clusters' own retained state, read off
     * the one build whose fills and borders the labels must match.
     *
     * @param clusters the clusters built this pass
     * @return the styling snapshot those clusters were built under
     */
    public static ClusterLabelStylingSnapshot resolveFrom(OwnerMapClusters clusters) {

        // The owners off the live occupancy, which the refresh folds, and the rest off the inputs
        // the build was baked under, which stand until the next rebuild.
        var buildInputs = clusters.getBuildInputs();

        return new ClusterLabelStylingSnapshot(
            clusters.getOccupancy().getHolderBySystemKey(),
            buildInputs.styling().desaturationPalette(),
            buildInputs.styling().categories(),
            buildInputs.viewReading().reading(),
            buildInputs.contentInputs());
    }
}
