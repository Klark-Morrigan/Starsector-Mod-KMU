package kmu.maplayers.base.labels.anchor;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileScope;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.labels.anchor.ClusterAnchorPlacement.ClusterAnchorFit;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.profiling.RebuildStepTerms;

import java.util.List;
import java.util.Map;

/**
 * Drives the cluster-name fit: cuts the cells into clusters by the layer's keys, carries over every
 * placement a change did not disturb, fits the rest through the pure {@link ClusterAnchorPlacement}
 * search, and reports what the fit cost - then keeps the caller's placements current in place.
 *
 * <p>Any layer with clusters runs a fit this same way, so the drive sits below every layer and asks
 * a layer for one value only, {@link ClusterAnchorFitInputs}: its keys, its face, whether it wants a
 * fit, and the resolver pair that names and shades a cluster. What a key means stays the layer's;
 * everything from the resolved shade and name onward is the same for every layer.
 *
 * <p>Kept apart from whatever builds a layer's fills, because the placements are an independent
 * overlay rather than part of the production draw lists: they draw over the normal render and over
 * a debug view alike, so they cannot live inside either view's build.
 *
 * <p>The rebuild takes the caller's {@link StandingClusterAnchors} and leaves its own pass in it,
 * rather than reading a list and answering an {@link AnchorFitFingerprint} for it. The pair is the
 * caller's and not kept here, because a fingerprint parked in a static would describe whichever
 * caller's fit ran last.
 *
 * <p>That pair is what makes a rebuild partial. The standing pair is this rebuild's only record
 * of the previous pass, so it is indexed by the cluster each placement names before this pass
 * replaces it, and the search carries a placement over rather than searching its cluster again.
 * The gate on that reuse is the fingerprint, decided here rather than per cluster: the tuning and
 * the cell geometry each move every fit at once - the keep-out sites the boxes are trimmed clear
 * of are the whole sector's, so a system appearing anywhere moves fits no membership change would
 * touch - and neither shows up in any one cluster's identity. When the two do not match, nothing
 * is offered and every cluster is searched afresh.
 *
 * <p>That same comparison is what the rebuild reports back. The list it leaves says where every
 * name ended up and nothing about which of them are new, so a caller holding work laid around the
 * previous list would have to redo all of it; which names moved is the carry-over index read from
 * the other side, and this is the only point where both lists exist at once.
 */
public final class ClusterAnchorsBuilder {

    // Every fit writes its line: it runs on a rebuild rather than per frame, and it is the
    // rebuild's largest cost, so a reader following one through the log wants each of them.
    private static final ProfileSection FIT_ANCHORS_SECTION = ProfileSection.registerSection(
        "mapLayer.fitClusterAnchors", RebuildStepTerms.LOGGED_EVERY_CALL);

    // Drives only; never instantiated.
    private ClusterAnchorsBuilder() {
    }

    /**
     * Rebuilds the cluster-name placements in place, replacing the standing pair with this pass's.
     *
     * <p>The search runs only when the layer wants a fit; one that wants none still has its list
     * emptied and labelled. The search's tuning is read here rather than by the callers, so every
     * path fits under one reading of it and a settings change re-fits on the rebuild it triggers.
     *
     * <p>The standing pair that came in lets placements whose clusters still stand carry over
     * rather than be searched again, and the pair left behind states the rules this fit ran under,
     * which only this build knows.
     *
     * @param standingAnchors the caller's placements and what they were fitted under, replaced
     *                        here with this pass's
     * @param cellGeometry    the cells the fit clips and trims against, carrying the revision that
     *                        names them, so no path can fit against one reading of the geometry
     *                        and label its placements with another
     * @param fitInputs       the layer's half of the fit: its keys, its face, whether it wants a
     *                        fit, and how a cluster is named and shaded
     * @return which names this pass moved: the list left behind says where every name ended up
     *         and nothing about which are new, and this is the only point where both lists exist
     *         at once. Reported by the gated-off path too, where every standing name vanishing is
     *         the disturbance
     */
    public static ClusterNameDisturbance rebuildClusterAnchors(
            StandingClusterAnchors standingAnchors,
            RevisedCellGeometry cellGeometry,
            ClusterAnchorFitInputs fitInputs) {

        // Read ahead of the gate rather than inside it, because the standing pair needs
        // labelling either way: a rebuild that fits nothing still leaves a list behind, and an
        // unlabelled one is indistinguishable from one fitted under rules that still hold.
        var fitFingerprint = readFitFingerprint(cellGeometry.revision(), fitInputs);

        // Indexed before this pass replaces the pair, since that is where the previous one
        // survives - the two move together, so there is no window where the list is emptied
        // ahead of the fit that fills it.
        var reusableAnchors = indexReusableAnchors(standingAnchors, fitFingerprint);

        // Copied for the same reason, and copied rather than held: the caller's list is a live
        // view of the placements this pass is about to replace, so a reference to it would report
        // this pass's own output as what stood before it.
        var standingNames = List.copyOf(standingAnchors.getAnchors());

        if (!fitInputs.isFitWanted()) {
            standingAnchors.replaceAnchors(List.of(), fitFingerprint);

            return ClusterNameDisturbance.compareFittedNames(standingNames, List.of());
        }
        // The tuning and the face come back off the fingerprint rather than from second reads, so
        // what the fit ran under and what it reports having run under are one value and cannot
        // drift apart on a rebuild that straddles a settings change.
        var fittedAnchors = fitClusterAnchors(
            cellGeometry,
            fitInputs,
            fitFingerprint,
            reusableAnchors);

        standingAnchors.replaceAnchors(fittedAnchors, fitFingerprint);

        return ClusterNameDisturbance.compareFittedNames(standingNames, fittedAnchors);
    }

    // The sweep itself, measured as its own row: past the gate the skipped path never reaches, and
    // the rebuild's largest cost, so it needs a duration of its own beside the layer's other
    // rebuild steps rather than only inside the whole-rebuild total. The placements go back to the
    // caller rather than into the standing pair here, so what a fit produced and what a rebuild
    // does with it stay two statements.
    private static List<ClusterAnchor> fitClusterAnchors(
            RevisedCellGeometry cellGeometry,
            ClusterAnchorFitInputs fitInputs,
            AnchorFitFingerprint fitFingerprint,
            Map<ClusterIdentity, ClusterAnchor> reusableAnchors) {

        try (var fitScope = ActiveProfiler.resolveProfiler().open(FIT_ANCHORS_SECTION)) {

            var spec = fitFingerprint.specification();
            var partition = cutClusterPartition(cellGeometry, fitInputs.ownerKeyBySystemKey());

            FontToleranceAnnouncement.announceIfMoved(spec);

            var fit = ClusterAnchorPlacement.computeClusterAnchors(
                partition,
                spec,
                fitInputs.labelResolversByFace().apply(fitFingerprint.labelFace()),
                reusableAnchors);

            reportFitCosts(fitScope, spec, partition, fit);

            return fit.anchors();
        }
    }

    // The clusters a fit names, with the cells they were cut from. The cells are grouped by the
    // system each draws as and that system's key, so whatever the layer keys a cluster by is what
    // the names trace: a layer that cuts one owner's systems into two keys gets two clusters, each
    // fitted inside its own border exactly as its fills are drawn.
    //
    // The grouping is composed here from the keys handed over, not asked of a layer's build: the
    // drive sits below every layer and knows no build to ask. The keys are the layer's own reading
    // of its build, handed over with the rest of the inputs, which is what keeps the two agreeing.
    //
    // The clusters and the cells travel on as one value: the sweep reads all of it against itself
    // per cluster, so a partition assembled from two passes would fit a name inside a border traced
    // from cells that no longer group that way. The edges are read once for both halves, so the
    // clustering and the geometry the fit clips against cannot be two readings of the cache.
    private static ClusterPartition cutClusterPartition(
            RevisedCellGeometry cellGeometry,
            Map<SystemKey, String> ownerKeyBySystemKey) {

        var cells = cellGeometry.cells();
        var edgesByCellKey = cells.getCellEdgesByCellKey();
        var cellGrouping = new CellGrouping(cells.getSystemKeyByCellKey(), ownerKeyBySystemKey);

        return new ClusterPartition(
            SystemClusters.findClusters(edgesByCellKey, cellGrouping),
            edgesByCellKey,
            cells.getSiteBySystemKey(),
            cellGrouping);
    }

    // The standing placements a fit made now may carry over, filed under the cluster each of
    // them names. Empty unless this rebuild would run under exactly what the standing pair says
    // its list was fitted under: the tuning and the geometry each invalidate every placement at
    // once rather than any one of them in particular, so the whole map is dropped on a mismatch
    // instead of any cluster being asked to notice a change it cannot see. That is also what
    // keeps a rebuild the caller has no record for - the first of a session, or one after a
    // discard - total, since a null fingerprint matches nothing.
    private static Map<ClusterIdentity, ClusterAnchor> indexReusableAnchors(
            StandingClusterAnchors standingAnchors,
            AnchorFitFingerprint fitFingerprint) {

        return fitFingerprint.equals(standingAnchors.getFitFingerprint())
            ? ClusterAnchor.mapAnchorsByIdentity(standingAnchors.getAnchors())
            : Map.of();
    }

    // Mints the fingerprint a fit made now would run under: the live tuning read off the
    // settings, against the revision the cells it clips and trims within stand at, in the face the
    // layer settled its names on. One point reads all three for both the fit and its fingerprint, so
    // no path can fit under one reading and report another.
    private static AnchorFitFingerprint readFitFingerprint(
            int geometryRevision,
            ClusterAnchorFitInputs fitInputs) {

        return new AnchorFitFingerprint(
            LabelAnchorSpecification.readFromLunaSettings(),
            geometryRevision,
            fitInputs.labelFace());
    }

    // Writes what a fit ran onto its scope, apart from the sweep so the sweep reads as the fit
    // alone. The placements are what the sweep is paid per, so they are the count. Its cost is the
    // product of its inputs, so the sweep knobs and the keep-out count ride on the call - a slow
    // fit is read off which multiplicand grew, not off the total alone. Three levels are named
    // because each understates the next: the direction count reads as swept but is a tuning knob
    // the fan adds fixed extras to, so it prints as swept-over-configured; the candidates are that
    // fan crossed with the offsets over every cluster; and the band fits are what those candidates
    // actually spent, many apiece, which is the level the duration tracks. Measured rather than
    // recomputed from the knobs here, so a sweep that bailed out early reads as cheap.
    private static void reportFitCosts(
            ProfileScope fitScope,
            LabelAnchorSpecification spec,
            ClusterPartition partition,
            ClusterAnchorFit fit) {

        fitScope.addCount(MapBuildCounters.LABELS, fit.anchors().size());
        fitScope.tagCall("clusters=" + partition.clusterMemberSystemKeys().size()
            + " directions="
            + ClusterAnchorPlacement.countCandidateDirections(spec.search().directionCount())
            + "/" + spec.search().directionCount()
            + " offsets=" + spec.search().offsetCount()
            + " candidates=" + fit.candidateCount()
            + " bandFits=" + fit.bandFitCount()
            + " keepOuts=" + partition.siteBySystemKey().size());
    }
}
