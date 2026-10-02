package kmu.maplayers.ownermap.render.labels;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.FontAtlas;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;

import java.util.Map;

/**
 * Everything one label rebuild resolves a cluster's colour and name from: who owns each system, the
 * shades a desaturated owner recolours to, the painting layer's categories and its reading of its
 * owners, the picks the pass was baked under - the spotlight the rest of the sector recedes by, and
 * the format a name is spelled in - and the face every name is measured in.
 *
 * <p>Held as one value because the parts have to describe the same moment. A rebuild that named
 * owners under this pass's reading while receding them by the last pass's filter would produce
 * labels that disagree with the fills beneath them, and the disagreement would show as a colour,
 * not as an error. Passing them one by one is what makes that possible; passing the snapshot
 * makes it unrepresentable.
 *
 * <p>The two paths that produce one differ in where the owners come from - the production
 * build hands over the clusters it just built, the border-tracing diagnostic resolves owners
 * straight from the sector and spotlights nothing - and agree on everything downstream, which is
 * the point of them meeting in one type.
 *
 * @param ownerBySystemKey    the owner of each drawn system, keyed by {@link SystemKey}
 * @param desaturationPalette the shared shades a desaturated owner's name recolours to
 * @param categories          the painting layer's categories: the full-strength one a spotlit
 *                            owner's name is held in, and each category's name style
 * @param reading             the painting layer's answers about its owners - each one's name,
 *                            category and recede
 * @param contentInputs       the picks the pass was baked under: the spotlight names and shades
 *                            recede by, and the name format they are spelled and fitted in
 * @param labelFace           the face the sector settled its labels on, which the fit measures every
 *                            name in and records beside its placements for the mint to draw in
 */
public record ClusterLabelStylingSnapshot(
    Map<SystemKey, SystemOwner> ownerBySystemKey,
    OwnerPalette desaturationPalette,
    OwnerCategories categories,
    OwnerReading reading,
    ContentInputs contentInputs,
    FontAtlas labelFace) {

    /**
     * The snapshot a production rebuild styles by: the clusters' own retained state, read off
     * the one build whose fills and borders the labels must match.
     *
     * @param clusters  the clusters built this pass
     * @param labelFace the face the sector settled its labels on
     * @return the styling snapshot those clusters were built under
     */
    public static ClusterLabelStylingSnapshot resolveFrom(OwnerMapClusters clusters, FontAtlas labelFace) {

        // The owners off the live occupancy, which the refresh folds, and the rest off the inputs
        // the build was baked under, which stand until the next rebuild.
        var buildInputs = clusters.getBuildInputs();

        return new ClusterLabelStylingSnapshot(
            clusters.getOccupancy().getOwnerBySystemKey(),
            buildInputs.styling().desaturationPalette(),
            buildInputs.styling().categories(),
            buildInputs.viewReading().reading(),
            buildInputs.contentInputs(),
            labelFace);
    }

    /**
     * The snapshot the debug border-tracing view styles by: the owners it traced, under the
     * active view's reading and categories, with nothing spotlit.
     *
     * <p>That view builds no drawables to borrow the palette from, so it is resolved here - through
     * the same darkening seam the theme reads, so the debug names desaturate exactly as production
     * does and the setting keeps a single reader. The view never filters, so the pick is dropped
     * from the picks it goes in under: it recedes nothing and names no synthetic spotlight key.
     * Dropped rather than the whole picks being replaced, since the names are still drawn in the
     * format the player asked for.
     *
     * @param ownerBySystemKey the owners the view traced, so its names and its borders name one
     *                         owner per cell
     * @param viewReading      the view the overlay is traced under, with its reading
     * @param contentInputs    the sidebar preferences the rebuild sampled
     * @param labelFace        the face the sector settled its labels on
     * @return the styling snapshot the traced names are fitted and drawn under
     */
    public static ClusterLabelStylingSnapshot resolveForTracing(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            ViewReading viewReading,
            ContentInputs contentInputs,
            FontAtlas labelFace) {

        var reading = viewReading.reading();

        return new ClusterLabelStylingSnapshot(
            ownerBySystemKey,
            MapPalettes.resolveDesaturationPalette(
                reading.resolveRecedePalette(),
                RenderStyleReader.readGlobalStyle().desaturationDarkening()),
            viewReading.view().resolveCategories(),
            reading,
            contentInputs.clearFilterPick(),
            labelFace);
    }
}
