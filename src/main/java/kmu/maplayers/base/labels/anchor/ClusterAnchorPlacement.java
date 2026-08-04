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
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * <p>That owner and the cluster's members are also what name the cluster, so each anchor
     * comes back carrying the {@link ClusterIdentity} it was fitted to. It is handed down into
     * the search rather than stamped on afterwards, so every path out - including the two that
     * collapse to a dot - names its cluster by construction.
     *
     * <p>That naming is what lets a pass skip the search entirely for a cluster it has already
     * been run for. Standing placements handed in under their identities are matched against
     * this pass's clusters, and a match is carried over rather than re-searched - restyled to
     * the shade its owner resolves to now, and only while the name it was sized against still
     * wraps the same way. The caller decides what may be offered here at all: the tuning and
     * the cell geometry each invalidate every placement at once rather than any one in
     * particular, so a pass made under different rules hands in nothing and every cluster is
     * fitted afresh.
     *
     * @param partition        the clusters to place a name in and the cells they were cut from
     * @param spec             the search's whole tuning surface
     * @param labelResolvers   the shade and the name measurement, both by owner
     * @param reusableAnchors  the standing placements a match may be carried over from, by the
     *                         cluster each was fitted to; empty to fit every cluster afresh
     * @return one anchor per cluster with a site to fit, in cluster order, with what the
     *         sweep cost to produce them
     */
    public static ClusterAnchorFit computeClusterAnchors(
            ClusterPartition partition,
            LabelAnchorSpecification spec,
            ClusterLabelResolvers labelResolvers,
            Map<ClusterIdentity, ClusterAnchor> reusableAnchors) {

        var clusters = partition.clusterMemberSystemIds();
        var anchors = new ArrayList<ClusterAnchor>(clusters.size());
        var candidateCount = 0;
        var bandFitCount = 0;

        for (var memberSystemIds : clusters) {
            var sites = collectClusterSites(memberSystemIds, partition.siteBySystemId());
            if (sites.isEmpty()) {
                continue;
            }
            var owner = partition.grouping().ownerBySystemId().get(memberSystemIds.get(0));
            var subject = labelResolvers.resolveLabelSubjectFor(
                // Set.copyOf here is where the member list stops being ordered: the sweep
                // walks the members in whatever order the grouping gave them, and that
                // order is not part of which cluster this is.
                new ClusterIdentity(owner, Set.copyOf(memberSystemIds)));

            var carried = carryOverAnchor(reusableAnchors.get(subject.identity()), subject);
            if (carried != null) {
                // A carried placement is the saving itself, so it contributes to neither
                // count: no candidate was generated for it and no band was fitted.
                anchors.add(carried);
                continue;
            }
            var axis = resolveClusterAxis(memberSystemIds, partition.edgesByCellId(), sites);
            var rings = spec.search().borderTrace().traceRings(
                memberSystemIds,
                partition.edgesByCellId(),
                partition.grouping());

            var search = searchClusterAnchor(
                subject,
                rings,
                partition.siteBySystemId(),
                axis,
                spec);

            // Accumulated over the clusters actually swept, so a cluster the search bailed
            // out of early contributes the nothing it cost rather than its share of a
            // product taken over every cluster.
            anchors.add(search.anchor());
            candidateCount += search.candidateCount();
            bandFitCount += search.bandFitCount();
        }
        return new ClusterAnchorFit(anchors, candidateCount, bandFitCount);
    }

    // The standing placement for a cluster when it can stand as this pass's, restyled to the
    // shade its owner resolves to now - or null when the cluster has to be searched again.
    //
    // Matching on identity has already settled the geometry: the same members over the same
    // cells trace the same border rings and fit inside the same keep-outs, and everything that
    // could move those without any membership changing is the caller's to rule out before
    // offering a placement here at all. What is left is the one per-cluster input that is not
    // geometry. The box was sized against the measured name at the line count that won, so it
    // holds exactly while that wrap is unchanged - which is what stops a renamed owner from
    // drawing a box cut for the name it used to have.
    //
    // A collapsed placement is never carried. It fitted no box, so it recorded no measured
    // name to check the current one against, and a name that has since grown shorter is
    // precisely the case where a cluster that had no room now does.
    private static ClusterAnchor carryOverAnchor(
            ClusterAnchor standing,
            ClusterLabelSubject subject) {

        if (standing == null || standing.lineCount() == 0) {
            return null;
        }
        var nameEstimator = subject.nameEstimator();

        // Asked first because an empty wrap is ambiguous on its own: it is what a stand-in
        // with no text behind it answers at every line count, and equally what a real name
        // answers at a line count it has too few words to fill. The first is a measurement
        // that has not moved, the second is a different measurement entirely, and comparing
        // the wraps alone would read them alike - carrying a stand-in's box onto a name that
        // cannot fill it. Whether the box's line count is still fillable at all separates
        // them, and can only fail where the estimator changed, since a placement proved its
        // own line count fillable by winning it.
        if (!Double.isFinite(
                nameEstimator.requiredLengthFor(standing.fontHeight(), standing.lineCount()))) {
            return null;
        }
        if (!standing.nameLines().equals(nameEstimator.wrapIntoLines(standing.lineCount()))) {
            return null;
        }
        return standing.copyWithColour(subject.colour());
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
            ClusterLabelSubject subject,
            List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId,
            PrincipalAxis axis,
            LabelAnchorSpecification spec) {

        var centroidX = (float) axis.centroidX();
        var centroidY = (float) axis.centroidY();

        if (rings.isEmpty()) {
            // No traceable border leaves nothing to prove a candidate interior - the
            // one dead end the search cannot work around, so only the dot can show.
            return new ClusterSearch(
                new ClusterAnchor(
                    subject.identity(),
                    centroidX,
                    centroidY,
                    subject.colour(),
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

        var fitter = newBoxFitter(spec, subject.nameEstimator());
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
                    subject.identity(),
                    midX,
                    midY,
                    subject.colour(),
                    subject.nameEstimator().wrapIntoLines(bestAccepted.lineCount()),
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
                subject.identity(),
                centroidX,
                centroidY,
                subject.colour(),
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

    // Builds the box fitter from the tuning and the cluster's name estimator. Both halves
    // of the fit's tuning are handed over as they were read - the text's own clamp and the
    // room it is measured within - so this seam carries no arithmetic of its own and a
    // knob's meaning cannot drift on the way through.
    private static LabelBoxFitter newBoxFitter(
            LabelAnchorSpecification spec,
            LabelLengthEstimator nameEstimator) {

        return new LabelBoxFitter(
            spec.nameFit(),
            spec.bandFit(),
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
