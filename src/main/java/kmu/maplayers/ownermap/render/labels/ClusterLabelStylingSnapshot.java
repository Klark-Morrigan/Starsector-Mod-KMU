package kmu.maplayers.ownermap.render.labels;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.FontAtlas;

import kmu.maplayers.base.labels.anchor.ClusterAnchorFitInputs;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;

import java.util.Map;

/**
 * Everything one label rebuild resolves a cluster's colour and name from: who owns each system, the
 * paints the build beneath the names was painted in - its scheme, the painting layer's reading of
 * its owners, and the picks it was baked under - and the face every name is measured in.
 *
 * <p>Held as one value because the parts have to describe the same moment. A rebuild that named
 * owners under this pass's reading while receding them by the last pass's filter would produce
 * labels that disagree with the fills beneath them, and the disagreement would show as a colour,
 * not as an error. Passing them one by one is what makes that possible; passing the snapshot
 * makes it unrepresentable.
 *
 * <p>The paints are the build's own rather than a second set resolved from the same parts, so a
 * name's colour is read off the very style decision its fill was painted from.
 *
 * <p>The two paths that produce one differ in where the owners come from - the production
 * build hands over the clusters it just built, the border-tracing diagnostic hands over the owners
 * the active view's source resolved with nothing spotlit - and agree on everything downstream, which is
 * the point of them meeting in one type.
 *
 * @param ownerBySystemKey the owner of each drawn system, keyed by {@link SystemKey}
 * @param paints           the paints the build beneath the names was painted in: each owner's
 *                         style decision, the categories a name style is read for, the shades a
 *                         desaturated owner's name recolours to, the reading an owner's name comes
 *                         off, and the picks - the spotlight names recede by, and the name format
 *                         they are spelled and fitted in
 * @param labelFace        the face the sector settled its labels on, which the fit measures every
 *                         name in and records beside its placements for the mint to draw in
 */
public record ClusterLabelStylingSnapshot(
    Map<SystemKey, SystemOwner> ownerBySystemKey,
    MapPaints paints,
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

        // The owners off the live occupancy, which the refresh folds, and the paints off the inputs
        // the build was baked under, which stand until the next rebuild.
        return new ClusterLabelStylingSnapshot(
            clusters.getOccupancy().getOwnerBySystemKey(),
            clusters.getBuildInputs().getPaints(),
            labelFace);
    }

    /**
     * The owner tier's half of a name fit: the owners as the keys the clusters are cut by, the face,
     * which consumers want the fit, and the resolver pair this snapshot names and shades by.
     *
     * <p>The clusters are cut by owner ID. Under a filter that is a synthetic spotlight key, so the
     * solid and contested clusters trace as their own clusters exactly as the fills do.
     *
     * <p>The anchor overlay's toggle is read here, the one point every path asks for its inputs, so
     * the production rebuild, the incremental refresh and the tracing view all gate the same way.
     *
     * @return the inputs the fit drive stands on
     */
    public ClusterAnchorFitInputs createFitInputs() {

        return new ClusterAnchorFitInputs(
            SystemOwner.mapOwnerIdBySystemKey(ownerBySystemKey),
            labelFace,
            paints.getContentInputs().nameFormat().areNamesDrawn(),
            KmuOwnerMapDiagnosticsSettings.getOwnerMapShowClusterAnchors(),
            () -> ClusterLabelStyling.createLabelResolvers(this));
    }

    /**
     * The snapshot the debug border-tracing view styles by: the owners it traced, under the
     * scheme its outlines were traced under, with nothing spotlit.
     *
     * <p>The view never filters, so the pick is dropped from the picks it goes in under: it recedes
     * nothing and names no synthetic spotlight key. Dropped rather than the whole picks being
     * replaced, since the names are still drawn in the format the player asked for. The view paints
     * no fills, so its paints are resolved here for its names alone.
     *
     * @param ownerBySystemKey the owners the view traced, so its names and its borders name one
     *                         owner per cell
     * @param mapStyling       the scheme the overlay was traced under, so its names and its
     *                         outlines are styled from one reading of the knobs
     * @param reading          the active view's reading of its owners
     * @param contentInputs    the sidebar preferences the rebuild sampled
     * @param labelFace        the face the sector settled its labels on
     * @return the styling snapshot the traced names are fitted and drawn under
     */
    public static ClusterLabelStylingSnapshot resolveForTracing(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            MapStyling mapStyling,
            OwnerReading reading,
            ContentInputs contentInputs,
            FontAtlas labelFace) {

        return new ClusterLabelStylingSnapshot(
            ownerBySystemKey,
            new MapPaints(mapStyling, reading, contentInputs.clearFilterPick()),
            labelFace);
    }
}
