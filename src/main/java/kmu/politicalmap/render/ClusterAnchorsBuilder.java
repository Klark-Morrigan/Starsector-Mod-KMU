package kmu.politicalmap.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.PrincipalAxis;
import kmlib.math.geometry.Spans;

import kmu.politicalmap.domain.geometry.CellEdge;
import kmu.politicalmap.domain.geometry.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.geometry.SystemClusters;
import kmu.politicalmap.domain.politics.DominantOwner;
import kmu.politicalmap.domain.politics.SectorPolitics;
import kmu.politicalmap.render.model.ClusterAnchor;
import kmu.settings.KmuLunaSettings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fits the debug label anchors: one {@link ClusterAnchor} per contiguous same-faction
 * cluster, its label line the highest-scoring of many candidate lines swept across the
 * cluster - each clipped inside the national border, trimmed clear of system icons, and
 * pulled short of the border at both ends - with shallower (more horizontal) lines
 * favoured over steep ones by a length-versus-slope score rather than by bending any
 * direction before the fit.
 *
 * <p>Its own builder, apart from {@link DrawablesBuilder}, because the anchors are an
 * independent overlay, not part of the production draw lists: they draw over the normal
 * render and the debug border-tracing overlay alike, so they cannot live inside either
 * view's build. The overlay list is owned by the terrain plugin and rebuilt in place
 * here, whichever base view a rebuild produced.
 */
final class ClusterAnchorsBuilder {

    // Builds only; never instantiated.
    private ClusterAnchorsBuilder() {
    }

    // Rebuilds the anchor overlay in place: clears the standing list, then - only when
    // the dev toggle is on - splits the owned systems into contiguous clusters and fits
    // one anchor to each. Shared by the full rebuild and the incremental refresh so an
    // ownership change keeps the anchors in step with the fills and borders. Reads the
    // toggle here (not at the call sites) so all paths gate identically; the search's
    // tuning is read here too, so a settings change re-fits on the rebuild it triggers.
    static void rebuildClusterAnchors(List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache,
            Map<String, DominantOwner> ownerBySystemId) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        var clusters = SystemClusters.findClusters(
                geometryCache.getCellEdgesBySystemId(), ownerBySystemId);
        anchors.addAll(computeClusterAnchors(clusters, geometryCache.getCellEdgesBySystemId(),
                geometryCache.getSiteBySystemId(), ownerBySystemId,
                AnchorTuning.readFromSettings()));
    }

    // The rebuild for a path with no owner map at hand - the debug border-tracing view,
    // which builds no production draw lists to borrow one from. Resolves ownership from
    // the sector itself, gated behind the toggle so the economy scan only runs while
    // someone is actually looking at the anchors.
    static void rebuildClusterAnchorsFromSector(List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache, SectorAPI sector) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        rebuildClusterAnchors(anchors, geometryCache,
                SectorPolitics.resolveDominantOwnerBySystemId(sector));
    }

    // Fits one label anchor to each contiguous cluster by searching over candidate lines
    // (searchClusterAnchor). The site centroid supplies the cluster's own principal axis
    // - one candidate direction among the fan, and the dot's fallback position - but has
    // no privileged pull on the accepted line, which is free to sit off-centre wherever
    // the cluster is roomiest. The owning faction's bright shade colours the anchor dot
    // so it reads against the fill; the candidate lines use a fixed diagnostic palette
    // instead, painted by the renderer.
    static List<ClusterAnchor> computeClusterAnchors(List<List<String>> clusters,
            Map<String, List<CellEdge>> edgesBySystemId, Map<String, double[]> siteBySystemId,
            Map<String, DominantOwner> ownerBySystemId, AnchorTuning tuning) {
        var anchors = new ArrayList<ClusterAnchor>(clusters.size());
        for (var memberSystemIds : clusters) {
            var sites = collectClusterSites(memberSystemIds, siteBySystemId);
            if (sites.isEmpty()) {
                continue;
            }
            var color = ownerBySystemId.get(memberSystemIds.get(0)).primaryColor();
            var axis = resolveClusterAxis(memberSystemIds, edgesBySystemId, sites);
            var rings = tuning.borderTrace().traceRings(memberSystemIds, edgesBySystemId,
                    ownerBySystemId);
            anchors.add(searchClusterAnchor(rings, siteBySystemId, axis, color, tuning));
        }
        return anchors;
    }

    // Searches one cluster's candidate lines and assembles its anchor. Every candidate is
    // a direction (a fan over the half-circle, plus pure horizontal and the cluster's own
    // axis) crossed at a parallel offset (a sweep across the cluster's perpendicular
    // extent), fit through that offset point against the border rings, the icons, and the
    // end inset. The best-scoring accepted candidate wins; its midpoint becomes the anchor
    // point a name will hang on. When nothing is accepted the anchor collapses to the site
    // centroid dot - the one point that always exists - optionally carrying the best
    // rejected candidate for the red diagnostic. With the unbiased toggle on, the pure
    // longest accepted line (the winner scored with no vertical penalty) rides along as
    // the yellow diagnostic whenever the penalty actually moved the pick.
    private static ClusterAnchor searchClusterAnchor(List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId, PrincipalAxis axis, Color color,
            AnchorTuning tuning) {
        var centroidX = (float) axis.centroidX();
        var centroidY = (float) axis.centroidY();
        if (rings.isEmpty()) {
            // No traceable border leaves nothing to prove a candidate interior - the
            // one dead end the search cannot work around, so only the dot can show.
            return new ClusterAnchor(centroidX, centroidY, color, null, null, null);
        }

        var directions = buildCandidateDirections(axis, tuning.directionCount());
        ScoredSegment bestAccepted = null;
        ScoredSegment longestAccepted = null;
        ScoredSegment bestRejected = null;
        for (var direction : directions) {
            var extent = projectRingsExtent(rings, -direction[1], direction[0]);
            for (var offsetIndex = 1; offsetIndex <= tuning.offsetCount(); offsetIndex++) {
                var through = offsetThroughPoint(axis, direction, extent, offsetIndex,
                        tuning.offsetCount());
                var fit = fitAlongDirection(rings, siteBySystemId, through[0], through[1],
                        direction, tuning);
                if (fit.acceptedSpan() != null) {
                    var candidate = scoreCandidate(through, direction, fit.acceptedSpan(), tuning);
                    bestAccepted = pickHigherScore(bestAccepted, candidate);
                    longestAccepted = pickLonger(longestAccepted, candidate);
                } else if (tuning.showRejectedAxis() && fit.rejectedSpan() != null) {
                    bestRejected = pickHigherScore(bestRejected,
                            scoreCandidate(through, direction, fit.rejectedSpan(), tuning));
                }
            }
        }

        if (bestAccepted != null) {
            var accepted = bestAccepted.segment();
            // The accepted interval has no tie to the centroid any more, so the dot and
            // the coming label's hang-point are the line's own midpoint.
            var midX = (accepted.startX() + accepted.endX()) / 2f;
            var midY = (accepted.startY() + accepted.endY()) / 2f;
            var unbiased = tuning.showUnbiasedAxis() && longestAccepted != null
                    && !longestAccepted.segment().equals(accepted)
                    ? longestAccepted.segment() : null;
            return new ClusterAnchor(midX, midY, color, accepted, null, unbiased);
        }
        // Collapse: no candidate survived anywhere, so the dot marks the site centroid;
        // the best rejected candidate rides along only when its toggle asked for it.
        var rejected = bestRejected != null ? bestRejected.segment() : null;
        return new ClusterAnchor(centroidX, centroidY, color, null, rejected, null);
    }

    // The candidate directions for one cluster: an even fan of unit directions over the
    // half-circle (index 0 is exactly horizontal, so horizontal is always searched),
    // plus the cluster's own principal axis so an elongated cluster can still fit along
    // its long dimension between two fan spokes. Directions are lines, not arrows - the
    // half-circle covers every slope, and the search treats a direction and its opposite
    // as one line.
    private static List<double[]> buildCandidateDirections(PrincipalAxis axis,
            int directionCount) {
        var directions = new ArrayList<double[]>(directionCount + 1);
        for (var i = 0; i < directionCount; i++) {
            var angle = Math.PI * i / directionCount;
            directions.add(new double[] {Math.cos(angle), Math.sin(angle)});
        }
        directions.add(new double[] {axis.axisX(), axis.axisY()});
        return directions;
    }

    // The through-point for one parallel-offset line: the cluster's perpendicular extent
    // (its span projected onto the direction's normal) is divided into evenly spaced
    // interior offsets, and this returns the foot of the perpendicular from the centroid
    // onto the chosen offset line, so the through-point sits near the cluster for stable
    // parameters. Offsets step strictly inside the extent (never on the grazing edges);
    // a single offset lands at the centre line.
    private static double[] offsetThroughPoint(PrincipalAxis axis, double[] direction,
            double[] extent, int offsetIndex, int offsetCount) {
        var normalX = -direction[1];
        var normalY = direction[0];
        var offset = extent[0] + (extent[1] - extent[0]) * offsetIndex / (offsetCount + 1.0);
        var centroidOffset = axis.centroidX() * normalX + axis.centroidY() * normalY;
        var shift = offset - centroidOffset;
        return new double[] {axis.centroidX() + shift * normalX, axis.centroidY() + shift * normalY};
    }

    // The extent of every ring vertex projected onto an axis, as {min, max} - the width
    // of the cluster along that axis, used to space the parallel offset lines across it.
    // Each ring's own extent comes from the shared point-cloud projection; merging their
    // bounds spans the whole cluster (outer ring and any holes) along the axis.
    private static double[] projectRingsExtent(List<List<double[]>> rings, double axisX,
            double axisY) {
        var min = Double.POSITIVE_INFINITY;
        var max = Double.NEGATIVE_INFINITY;
        for (var ring : rings) {
            var extent = Points.projectExtentOnto(ring, axisX, axisY);
            min = Math.min(min, extent[0]);
            max = Math.max(max, extent[1]);
        }
        return new double[] {min, max};
    }

    // Scores one accepted (or rejected) candidate: its clear length scaled by the
    // vertical penalty - length * (1 - strength * sin(angle)^exponent), with sin(angle)
    // the direction's rise (its unit vertical component) so a horizontal line keeps its
    // whole length and a steeper one is docked more the closer it runs to vertical. The
    // resulting world segment and its raw length ride along so the pick and the unbiased
    // (pure-longest) comparison read from the same record.
    private static ScoredSegment scoreCandidate(double[] through, double[] direction,
            double[] span, AnchorTuning tuning) {
        var length = span[1] - span[0];
        var rise = Math.abs(direction[1]);
        var penalty = tuning.verticalPenaltyStrength()
                * Math.pow(rise, tuning.verticalPenaltyExponent());
        var score = length * (1.0 - penalty);
        return new ScoredSegment(toSegment(through[0], through[1], direction, span), length, score);
    }

    // One scored candidate line: its world segment, its raw clear length (the tie-break
    // the unbiased pure-longest pick reads), and its penalised score (the tie-break the
    // accepted pick reads).
    private record ScoredSegment(ClusterAnchor.AxisSegment segment, double length, double score) {
    }

    // The higher-scoring of two candidates; the first seen wins a tie, keeping the pick
    // deterministic across a rebuild.
    private static ScoredSegment pickHigherScore(ScoredSegment current, ScoredSegment candidate) {
        return current == null || candidate.score() > current.score() ? candidate : current;
    }

    // The longer of two candidates by raw clear length - the pick a zero-penalty search
    // would make, which the yellow diagnostic contrasts against the penalised winner.
    private static ScoredSegment pickLonger(ScoredSegment current, ScoredSegment candidate) {
        return current == null || candidate.length() > current.length() ? candidate : current;
    }

    // The direction and length to fit a cluster's label line along: the principal axis
    // of its member system positions when that cloud has real spread (the usual case
    // for a multi-system cluster), or - when it does not, a single-system cluster or
    // coincident sites - the principal axis of the member cells' own raw Voronoi
    // vertices instead, so even a lone system's cell shape gives it a real direction to
    // add to the fan. The site centroid is always kept as the fallback dot position and
    // the sweep's origin regardless of which cloud supplied the direction, so the anchor
    // still falls back to the system's own position, not the cell's vertex-cloud mean.
    private static PrincipalAxis resolveClusterAxis(List<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId, List<double[]> sites) {
        var siteAxis = PrincipalAxis.fitTo(sites);
        if (siteAxis.length() >= Limits.MIN_EDGE_LENGTH) {
            return siteAxis;
        }
        var cellVertices = collectClusterCellVertices(memberSystemIds, edgesBySystemId);
        if (cellVertices.size() < 2) {
            return siteAxis;
        }
        var vertexAxis = PrincipalAxis.fitTo(cellVertices);
        if (vertexAxis.length() < Limits.MIN_EDGE_LENGTH) {
            return siteAxis;
        }
        return new PrincipalAxis(siteAxis.centroidX(), siteAxis.centroidY(),
                vertexAxis.axisX(), vertexAxis.axisY(), vertexAxis.length());
    }

    // Gathers every member cell's raw Voronoi edge endpoints as a point cloud - the
    // cluster's own footprint, fitted for a direction when its systems' site positions
    // alone have no spread to fit one to.
    private static List<double[]> collectClusterCellVertices(List<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId) {
        var vertices = new ArrayList<double[]>();
        for (var systemId : memberSystemIds) {
            var edges = edgesBySystemId.get(systemId);
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

    // One directional fit's outcome: the accepted {tStart, tEnd} interval the name may
    // occupy, or - only when nothing was accepted - the best rejected candidate found
    // along the way, for the red diagnostic line. Exactly one of the two is non-null;
    // both are null only when the direction found no interior geometry at all to work
    // with (the line misses the region entirely).
    private record DirectionalFit(double[] acceptedSpan, double[] rejectedSpan) {
    }

    // Fits one candidate line (a direction through a chosen offset point) against a
    // cluster's border rings and the system icons: the rings - the same ones the national
    // border strokes, so the anchor clips against what the player sees - bound the line to
    // its genuinely interior pieces (a chord across a concavity or an enclave is never
    // kept); every system icon then carves its keep-out interval from what remains; the
    // longest survivor is pulled in from both ends by the end inset so the name stops
    // short of the border. All sites act as icon blockers, not just the cluster's own: a
    // neighbour faction's icon just across the border can still overhang the inset
    // interior. Falls through the ways a candidate can fail to produce an accepted line,
    // keeping the furthest-along candidate as the rejected span: the line missing the
    // interior entirely (nothing to report), the icons consuming every interior span (the
    // longest interior span reported), or the end inset consuming what the icons left (the
    // pre-inset clear span reported).
    private static DirectionalFit fitAlongDirection(List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId, double throughX, double throughY,
            double[] direction, AnchorTuning tuning) {
        var interiorSpans = Polygons.findLineInteriorSpans(rings, throughX, throughY,
                direction[0], direction[1]);
        if (interiorSpans.isEmpty()) {
            return new DirectionalFit(null, null);
        }
        var clear = Spans.findLongestClearSubsegment(interiorSpans, throughX, throughY,
                direction[0], direction[1], siteBySystemId.values(), tuning.iconClearance());
        if (clear == null) {
            return new DirectionalFit(null, Spans.findLongestSpan(interiorSpans));
        }
        var start = clear[0] + tuning.endInsetDistance();
        var end = clear[1] - tuning.endInsetDistance();
        // An interval shorter than twice the end inset leaves no room for a name
        // between the margins; the pre-inset clear span is the best rejected candidate.
        return start < end ? new DirectionalFit(new double[] {start, end}, null)
                : new DirectionalFit(null, clear);
    }

    // Maps a {tStart, tEnd} parameter interval back to world-coordinate endpoints along
    // the given direction from the candidate's through-point - the last step shared by
    // the accepted, rejected, and unbiased lines alike.
    private static ClusterAnchor.AxisSegment toSegment(double throughX, double throughY,
            double[] direction, double[] span) {
        return new ClusterAnchor.AxisSegment(
                (float) (throughX + direction[0] * span[0]),
                (float) (throughY + direction[1] * span[0]),
                (float) (throughX + direction[0] * span[1]),
                (float) (throughY + direction[1] * span[1]));
    }

    // Gathers the {x, y} sites of a cluster's members, skipping any whose site is missing
    // - the point cloud the anchor's axis is fitted to.
    private static List<double[]> collectClusterSites(List<String> memberSystemIds,
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

    /**
     * The modifiers the cluster-anchor search reads, gathered into one value so the
     * search takes its whole tuning surface as data rather than reaching into the
     * settings mid-computation.
     *
     * @param borderTrace            the national-border trace the anchor clips against -
     *                               shared with the territory build, so the anchor sees
     *                               the same rings the player does by construction
     * @param endInsetDistance       how far each end of the clear interval pulls inward,
     *                               in world units - the border-inset multiple already
     *                               resolved to a distance
     * @param iconClearance          the keep-out radius around each system icon, world
     *                               units
     * @param directionCount         the number of directions the candidate fan spans
     *                               over the half-circle
     * @param offsetCount            the number of parallel lines swept per direction
     * @param verticalPenaltyStrength how much length a shallower line may give up and
     *                               still win, 0 (pure longest) to 1 (vertical scores
     *                               zero)
     * @param verticalPenaltyExponent the exponent on the direction's rise in the score,
     *                               concentrating the penalty toward vertical
     * @param showRejectedAxis       whether a cluster whose accepted line collapsed also
     *                               carries the best rejected candidate the search found,
     *                               for the red diagnostic line
     * @param showUnbiasedAxis       whether each cluster also carries the pure-longest
     *                               accepted line (the winner with no vertical penalty),
     *                               for the yellow diagnostic line
     */
    record AnchorTuning(BorderTrace borderTrace, double endInsetDistance, double iconClearance,
            int directionCount, int offsetCount, double verticalPenaltyStrength,
            double verticalPenaltyExponent, boolean showRejectedAxis, boolean showUnbiasedAxis) {

        // Reads the live tuning: the anchor knobs from the Dev "Label anchors" section
        // plus the same border trace the national border renders with. The end-inset
        // multiple is resolved against the fixed border channel here, so the search works
        // in plain distances. The two diagnostic-line toggles ride along so the search
        // only builds the extra candidates while someone is looking at them.
        static AnchorTuning readFromSettings() {
            return new AnchorTuning(
                    BorderTrace.readFromSettings(),
                    KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                            * PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    KmuLunaSettings.getPoliticalMapAnchorIconClearance(),
                    KmuLunaSettings.getPoliticalMapAnchorDirectionCount(),
                    KmuLunaSettings.getPoliticalMapAnchorOffsetCount(),
                    KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyStrength(),
                    KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyExponent(),
                    KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                    KmuLunaSettings.getPoliticalMapShowUnbiasedAxes());
        }
    }
}
