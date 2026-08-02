package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.DirectedLine;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PrincipalAxis;
import kmlib.math.geometry.RegionChord;
import kmlib.math.geometry.Segment;
import kmlib.math.solving.Picks;
import kmlib.starsector.ui.label.LabelBoxFitter;
import kmlib.starsector.ui.label.LabelLengthEstimator;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fits the label placements: one {@link ClusterAnchor} per contiguous same-owner cluster, its
 * label box the highest-scoring of many candidate lines swept across the cluster. This class
 * generates the candidates (a direction fan crossed at parallel offsets) and selects among
 * them; {@link LabelBoxFitter} sizes each into the largest box holding the cluster's name -
 * clipped inside the cluster's border, trimmed clear of system icons, pulled short of the
 * border at both ends, and stacked into extra lines where that buys a bigger font. The name is
 * measured with the label font's own metrics, so the accepted box is sized for the glyphs that
 * will actually fill it; where the font or the name will not resolve, an aspect stand-in keeps
 * the debug band meaningful and no name draws. Boxes that lean along the cluster's own axis are
 * favoured over ones that stray from it by a font-height-versus-slope score
 * ({@link LabelSlantPreference}) rather than by bending any direction before the fit; the
 * preferred lean is capped short of vertical and fades to level for round clusters whose axis
 * carries no real direction.
 *
 * <p>Pure geometry over opaque owner ids: what a key means, what its name reads and what
 * shade it draws in all arrive injected as {@link ClusterLabelResolvers}, so the search reads
 * no settings, ownership, or filter state, names nothing on the map, and is exercised on
 * hand-built clusters.
 */
public final class ClusterAnchorPlacement {

    // The two directions every fan carries beyond its configured width: the cluster's own
    // principal axis and the preferred slant. Fixed, so a fan's configured width and the
    // count actually swept differ by exactly this.
    private static final int FIXED_EXTRA_DIRECTIONS = 2;

    // Searches only; never instantiated.
    private ClusterAnchorPlacement() {
    }

    /**
     * How many candidate directions a configured fan width resolves to, once the fixed
     * extras are folded in. Exposed because the configured width alone reads as the swept
     * count and is not it - the gap is this search's own business, so reporting the two
     * apart takes the resolution from here rather than restating the arithmetic elsewhere.
     *
     * @param directionCount the configured fan width
     * @return the directions a cluster's sweep actually visits
     */
    public static int countCandidateDirections(int directionCount) {
        return directionCount + FIXED_EXTRA_DIRECTIONS;
    }

    /**
     * Fits one label anchor to each contiguous cluster by searching over candidate lines. The
     * site centroid supplies the cluster's own principal axis - one candidate direction among
     * the fan, and the dot's fallback position - but has no privileged pull on the accepted
     * line, which is free to sit off-centre wherever the cluster is roomiest.
     *
     * <p>A cluster's two non-geometric attributes are looked up by its owner, taken
     * from the first member's key since every member of a cluster shares one: the name the fit
     * sizes and wraps against, and the shade the name (and its debug dot) draws in. Both are
     * plain functions of the key, so the search never asks what a key means; the candidate
     * lines use a fixed diagnostic palette instead, painted by the renderer.
     *
     * @param clusters        each contiguous cluster's member system ids
     * @param edgesByCellId   each cell's raw edges - the geometry lines are clipped against
     * @param siteBySystemId  each system's world position, for the axis fit and the icon
     *                        keep-outs
     * @param grouping        which system each cell draws as and each system's owner
     * @param spec            the search's whole tuning surface
     * @param labelResolvers  the shade and the name measurement, both by owner
     * @return one anchor per cluster with a site to fit, in cluster order, with what the
     *         sweep cost to produce them
     */
    public static ClusterAnchorFit computeClusterAnchors(
            List<List<String>> clusters,
            Map<String, List<CellEdge>> edgesByCellId,
            Map<String, double[]> siteBySystemId,
            CellGrouping grouping,
            LabelAnchorSpecification spec,
            ClusterLabelResolvers labelResolvers) {

        var anchors = new ArrayList<ClusterAnchor>(clusters.size());
        var candidateCount = 0;
        var bandFitCount = 0;

        for (var memberSystemIds : clusters) {
            var sites = collectClusterSites(memberSystemIds, siteBySystemId);
            if (sites.isEmpty()) {
                continue;
            }
            var owner = grouping.ownerBySystemId().get(memberSystemIds.get(0));
            var axis = resolveClusterAxis(memberSystemIds, edgesByCellId, sites);
            var rings = spec.search().borderTrace().traceRings(
                memberSystemIds,
                edgesByCellId,
                grouping);

            var search = searchClusterAnchor(
                rings,
                siteBySystemId,
                axis,
                labelResolvers.resolveLabelColourOf(owner),
                spec,
                labelResolvers.resolveNameEstimatorOf(owner));

            // Accumulated over the clusters actually swept, so a cluster the search bailed
            // out of early contributes the nothing it cost rather than its share of a
            // product taken over every cluster.
            anchors.add(search.anchor());
            candidateCount += search.candidateCount();
            bandFitCount += search.bandFitCount();
        }
        return new ClusterAnchorFit(anchors, candidateCount, bandFitCount);
    }

    // Searches one cluster's candidate lines and assembles its anchor as a fitted label
    // box. Every candidate is a direction (a fan over the half-circle, plus pure
    // horizontal, the cluster's own axis, and the preferred slant) crossed at a parallel
    // offset (a sweep across the cluster's perpendicular extent). At each, the box solver
    // sizes the largest name that fits - growing the font against the border, spending
    // spare girth on extra lines only where that buys a bigger font - and the candidate
    // is scored by that fitted font height docked for straying from the cluster's
    // preferred lean. The best-scoring box wins; its clear span is the accepted line, its
    // girth and line count the band the name fills, the span's midpoint the point the
    // name block centres on, and the name estimator's wrap at the winning line count the
    // lines the label draws. When no box fits anywhere the anchor collapses to the site
    // centroid dot, optionally carrying the best near-miss span for the red diagnostic.
    // With the unbiased toggle on, the box that wins on raw font height (no slope
    // penalty) rides along as the yellow diagnostic whenever the penalty moved the pick.
    private static ClusterSearch searchClusterAnchor(
            List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId,
            PrincipalAxis axis,
            Color colour,
            LabelAnchorSpecification spec,
            LabelLengthEstimator nameEstimator) {

        var centroidX = (float) axis.centroidX();
        var centroidY = (float) axis.centroidY();

        if (rings.isEmpty()) {
            // No traceable border leaves nothing to prove a candidate interior - the
            // one dead end the search cannot work around, so only the dot can show.
            return new ClusterSearch(
                new ClusterAnchor(
                    centroidX,
                    centroidY,
                    colour,
                    List.of(),
                    0f,
                    null,
                    null,
                    null,
                    0f,
                    0),
                0,
                0);
        }

        var fitter = newBoxFitter(spec, nameEstimator);
        var icons = siteBySystemId.values();
        var slant = LabelSlantPreference.resolveFrom(axis, spec.scoring().maxSlantDegrees());
        var directions = buildCandidateDirections(axis, slant, spec.search().directionCount());
        var bestScore = 0.0;
        var candidateCount = 0;

        LabelBoxFitter.BoxFit bestAccepted = null;
        LabelBoxFitter.BoxFit longestAccepted = null;
        RejectedSpan bestRejected = null;

        for (var direction : directions) {
            var extent = Points.projectCombinedExtentOnto(rings, -direction[1], direction[0]);
            for (var offsetIndex = 1; offsetIndex <= spec.search().offsetCount(); offsetIndex++) {

                candidateCount++;
                var through = offsetThroughPoint(
                    axis,
                    direction,
                    extent,
                    offsetIndex,
                    spec.search().offsetCount());

                var chord = new RegionChord(
                    rings,
                    icons,
                    new DirectedLine(through[0], through[1], direction[0], direction[1]));

                var box = fitter.fitLargestBox(chord);
                if (box != null) {
                    // Selection docks the fitted font height for lines that stray from the
                    // cluster's preferred slant - a sizing-blind choice, so it lives here,
                    // not in the fitter; the unbiased pick keeps the raw-height winner for
                    // the yellow diagnostic.
                    var score = box.fontHeight() * slant.computePenaltyMultiplier(
                        direction,
                        spec.scoring().verticalPenaltyStrength(),
                        spec.scoring().verticalPenaltyExponent());

                    if (bestAccepted == null || score > bestScore) {
                        bestAccepted = box;
                        bestScore = score;
                    }
                    longestAccepted = Picks.pickHigher(
                        longestAccepted,
                        box,
                        LabelBoxFitter.BoxFit::fontHeight);

                } else if (spec.diagnostics().showRejectedAxis()) {
                    bestRejected = Picks.pickHigher(
                        bestRejected,
                        findRejectedSpan(fitter, chord, spec.nameFit().minFontHeight()),
                        RejectedSpan::length);
                }
            }
        }

        if (bestAccepted != null) {
            var accepted = bestAccepted.segment();

            // The accepted interval has no tie to the centroid any more, so the dot and
            // the label's hang-point are the line's own midpoint.
            var midX = (float) ((accepted.startX() + accepted.endX()) / 2.0);
            var midY = (float) ((accepted.startY() + accepted.endY()) / 2.0);

            var unbiased = spec.diagnostics().showUnbiasedAxis()
                    && longestAccepted != null
                    && !longestAccepted.segment().equals(accepted)
                ? longestAccepted.segment()
                : null;

            return new ClusterSearch(
                new ClusterAnchor(
                    midX,
                    midY,
                    colour,
                    nameEstimator.wrapIntoLines(bestAccepted.lineCount()),
                    (float) bestAccepted.fontHeight(),
                    accepted,
                    null, // Rejected axis.
                    unbiased,
                    (float) bestAccepted.thickness(),
                    bestAccepted.lineCount()),
                candidateCount,
                fitter.getBandFitCount());
        }

        // Collapse: no box fit anywhere, so the dot marks the site centroid; the best
        // near-miss span rides along only when the rejected toggle asked for it.
        var rejected = bestRejected != null ? bestRejected.segment() : null;

        return new ClusterSearch(
            new ClusterAnchor(
                centroidX,
                centroidY,
                colour,
                List.of(),
                0f,
                null,
                rejected,
                null,
                0f,
                0),
            candidateCount,
            fitter.getBandFitCount());
    }

    // Builds the box fitter from the tuning and the cluster's name estimator: the
    // font-size clamp, line count, and spacing that shape the growth, the icon clearance
    // and end inset every band trim reads, and the name the fit sizes against.
    private static LabelBoxFitter newBoxFitter(
            LabelAnchorSpecification spec,
            LabelLengthEstimator nameEstimator) {

        var search = spec.search();
        return new LabelBoxFitter(
            spec.nameFit(),
            search.iconClearance(),
            search.endInsetDistance(),
            nameEstimator);
    }

    // The candidate directions for one cluster: an even fan of unit directions over the
    // half-circle (index 0 is exactly horizontal, so horizontal is always searched),
    // plus the cluster's own principal axis so an elongated cluster can fit along its long
    // dimension between two fan spokes, plus the preferred slant so the winner can land
    // exactly on the cluster's capped lean rather than the nearest spoke. Directions are
    // lines, not arrows - the half-circle covers every slope, and the search treats a
    // direction and its opposite as one line.
    private static List<double[]> buildCandidateDirections(
            PrincipalAxis axis,
            LabelSlantPreference slant,
            int directionCount) {

        var directions = new ArrayList<double[]>(directionCount + 2);
        for (var i = 0; i < directionCount; i++) {
            var angle = Math.PI * i / directionCount;
            directions.add(new double[] {Math.cos(angle), Math.sin(angle)});
        }
        directions.add(new double[] {axis.axisX(), axis.axisY()});
        directions.add(slant.toDirection());
        return directions;
    }

    // The through-point for one parallel-offset line: the cluster's perpendicular extent
    // (its span projected onto the direction's normal) is divided into evenly spaced
    // interior offsets, and this returns the foot of the perpendicular from the centroid
    // onto the chosen offset line, so the through-point sits near the cluster for stable
    // parameters. Offsets step strictly inside the extent (never on the grazing edges);
    // a single offset lands at the centre line.
    private static double[] offsetThroughPoint(
            PrincipalAxis axis,
            double[] direction,
            double[] extent,
            int offsetIndex,
            int offsetCount) {

        var normalX = -direction[1];
        var normalY = direction[0];
        var offset = extent[0] + (extent[1] - extent[0]) * offsetIndex / (offsetCount + 1.0);
        var centroidOffset = axis.centroidX() * normalX + axis.centroidY() * normalY;
        var shift = offset - centroidOffset;

        return new double[] {
            axis.centroidX() + shift * normalX,
            axis.centroidY() + shift * normalY};
    }

    // The best near-miss line for the red diagnostic: a candidate's clear span before the
    // end-margin trim, kept with its length so the longest across candidates wins.
    private record RejectedSpan(
        Segment segment,
        double length) {
    }

    // A candidate's near-miss span for the red diagnostic: the pre-margin clear span of a
    // band at the minimum font's single-line girth - the furthest a name-holding line got
    // before the border, icon, or end-margin trim discarded it. Null when even the thin
    // band finds no clear interior at all.
    private static RejectedSpan findRejectedSpan(
            LabelBoxFitter fitter,
            RegionChord chord,
            double minFontSize) {

        var band = fitter.fitBand(chord, minFontSize / 2.0);
        if (band.clearSpan() == null) {
            return null;
        }
        return new RejectedSpan(
            chord.toSegment(band.clearSpan()),
            band.clearSpan()[1] - band.clearSpan()[0]);
    }

    // The direction and length to fit a cluster's label line along: the principal axis
    // of its member system positions when that cloud has real spread (the usual case
    // for a multi-system cluster), or - when it does not, a single-system cluster or
    // coincident sites - the principal axis of the member cells' own raw Voronoi
    // vertices instead, so even a lone system's cell shape gives it a real direction to
    // add to the fan. The site centroid is always kept as the fallback dot position and
    // the sweep's origin regardless of which cloud supplied the direction, so the anchor
    // still falls back to the system's own position, not the cell's vertex-cloud mean.
    private static PrincipalAxis resolveClusterAxis(
            List<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesByCellId,
            List<double[]> sites) {

        var siteAxis = PrincipalAxis.fitTo(sites);
        if (siteAxis.length() >= Limits.MIN_EDGE_LENGTH) {
            return siteAxis;
        }
        var cellVertices = collectClusterCellVertices(memberSystemIds, edgesByCellId);
        if (cellVertices.size() < 2) {
            return siteAxis;
        }
        var vertexAxis = PrincipalAxis.fitTo(cellVertices);
        if (vertexAxis.length() < Limits.MIN_EDGE_LENGTH) {
            return siteAxis;
        }
        // The vertex cloud supplies the direction, so it also supplies the minor extent -
        // the slant gate reads the elongation of whichever cloud gave the axis, not the
        // site cloud's (which had no usable spread here).
        return new PrincipalAxis(
            siteAxis.centroidX(),
            siteAxis.centroidY(),
            vertexAxis.axisX(),
            vertexAxis.axisY(),
            vertexAxis.length(),
            vertexAxis.minorLength());
    }

    // Gathers every member cell's raw Voronoi edge endpoints as a point cloud - the
    // cluster's own footprint, fitted for a direction when its systems' site positions
    // alone have no spread to fit one to.
    private static List<double[]> collectClusterCellVertices(
            List<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesByCellId) {

        var vertices = new ArrayList<double[]>();
        for (var systemId : memberSystemIds) {
            var edges = edgesByCellId.get(systemId);
            if (edges == null) {
                continue;
            }
            for (var edge : edges) {
                vertices.add(new double[] {edge.x1(), edge.y1()});
                vertices.add(new double[] {edge.x2(), edge.y2()});
            }
        }
        return vertices;
    }

    /**
     * A whole sweep's anchors together with what producing them cost. The two counts ride
     * back with the anchors because only the sweep knows them: the candidates it generated
     * (which the tuning alone understates, the fan carrying fixed extras) and the band fits
     * those candidates spent (which the candidate count understates again, each candidate
     * costing many). Carried as measurements rather than as a product recomputed from the
     * tuning, so a cluster the sweep bailed out of early is counted as the nothing it cost.
     *
     * @param anchors        one anchor per cluster with a site to fit, in cluster order
     * @param candidateCount the candidate chords the sweep generated across every cluster
     * @param bandFitCount   the band fits those candidates spent across every cluster
     */
    public record ClusterAnchorFit(
        List<ClusterAnchor> anchors,
        int candidateCount,
        int bandFitCount) {
    }

    // One cluster's search result: its anchor and what that cluster alone cost, so the
    // caller can sum the cost over the clusters it swept without the search reaching out
    // to a counter it does not own.
    private record ClusterSearch(
        ClusterAnchor anchor,
        int candidateCount,
        int bandFitCount) {
    }

    // Gathers the {x, y} sites of a cluster's members, skipping any whose site is missing
    // - the point cloud the anchor's axis is fitted to.
    private static List<double[]> collectClusterSites(
            List<String> memberSystemIds,
            Map<String, double[]> siteBySystemId) {

        var sites = new ArrayList<double[]>(memberSystemIds.size());
        for (var systemId : memberSystemIds) {
            var site = siteBySystemId.get(systemId);
            if (site != null) {
                sites.add(site);
            }
        }
        return sites;
    }
}
