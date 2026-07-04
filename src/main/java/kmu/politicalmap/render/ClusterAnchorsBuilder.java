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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fits the debug label anchors: one {@link ClusterAnchor} per contiguous same-faction
 * cluster, its label line clipped inside the national border, trimmed clear of system
 * icons, leaned horizontal, and pulled short of the border at both ends.
 *
 * <p>Its own builder, apart from {@link DrawablesBuilder}, because the anchors are an
 * independent overlay, not part of the production draw lists: they draw over the normal
 * render and the debug border-tracing overlay alike, so they cannot live inside either
 * view's build. The overlay list is owned by the terrain plugin and rebuilt in place
 * here, whichever base view a rebuild produced.
 */
final class ClusterAnchorsBuilder {
    // Two unit directions whose dot product clears this count as the same line for
    // display purposes - the tolerance only absorbs floating-point drift in the
    // renormalisation, so any real lean the bias applies lands far below it.
    private static final double SAME_DIRECTION_MIN_DOT_PRODUCT = 1.0 - 1e-9;

    // Builds only; never instantiated.
    private ClusterAnchorsBuilder() {
    }

    // Rebuilds the anchor overlay in place: clears the standing list, then - only when
    // the dev toggle is on - splits the owned systems into contiguous clusters and fits
    // one anchor to each. Shared by the full rebuild and the incremental refresh so an
    // ownership change keeps the anchors in step with the fills and borders. Reads the
    // toggle here (not at the call sites) so all paths gate identically; the fit's
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

    // Fits one label anchor to each contiguous cluster: resolveClusterAxis gives the
    // centre to hang the label on and the raw direction (falling back to the member
    // cells' own shape when the site cloud alone has no spread, so a single-system
    // cluster still gets a real direction to try); leaning that horizontal by the
    // tuning's bias and refitting it against the border, the icons, and the end inset
    // (fitAlongDirection) gives the accepted line. When that collapses, the same fit's
    // best rejected candidate becomes the red diagnostic line (if its toggle is on); a
    // second fit with no bias applied becomes the yellow one (if its toggle is on and
    // the bias actually changed the direction). The owning faction's bright shade
    // colours the centroid dot so it reads against the fill; the lines use a fixed
    // diagnostic palette instead, painted by the renderer.
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
            var centroidX = (float) axis.centroidX();
            var centroidY = (float) axis.centroidY();
            if (axis.length() < Limits.MIN_EDGE_LENGTH) {
                // Nothing survives even the cell-shape fallback (missing or fully
                // degenerate geometry) - the true dead end where only the dot can show.
                anchors.add(new ClusterAnchor(centroidX, centroidY, color, null, null, null));
                continue;
            }

            var rings = tuning.borderTrace().traceRings(memberSystemIds, edgesBySystemId,
                    ownerBySystemId);
            var biasedDirection = leanTowardHorizontal(axis, tuning.horizontalBias());
            var biasedFit = fitAlongDirection(rings, siteBySystemId, axis.centroidX(),
                    axis.centroidY(), biasedDirection, tuning);

            ClusterAnchor.AxisSegment unbiasedAxis = null;
            if (tuning.showUnbiasedAxis()) {
                var unbiasedDirection = leanTowardHorizontal(axis, 1.0);
                if (!isSameDirection(biasedDirection, unbiasedDirection)) {
                    var unbiasedFit = fitAlongDirection(rings, siteBySystemId, axis.centroidX(),
                            axis.centroidY(), unbiasedDirection, tuning);
                    if (unbiasedFit.acceptedSpan() != null) {
                        unbiasedAxis = toSegment(axis, unbiasedDirection,
                                unbiasedFit.acceptedSpan());
                    }
                }
            }

            // The fit yields at most one of the two spans (a rejected candidate exists
            // only when nothing was accepted), so the anchor carries an accepted line,
            // or a rejected line when its toggle asks for it, or - both null - the dot.
            var acceptedAxis = biasedFit.acceptedSpan() != null
                    ? toSegment(axis, biasedDirection, biasedFit.acceptedSpan())
                    : null;
            var rejectedAxis = tuning.showRejectedAxis() && biasedFit.rejectedSpan() != null
                    ? toSegment(axis, biasedDirection, biasedFit.rejectedSpan())
                    : null;
            anchors.add(new ClusterAnchor(centroidX, centroidY, color,
                    acceptedAxis, rejectedAxis, unbiasedAxis));
        }
        return anchors;
    }

    // The direction and length to fit a cluster's label line along: the principal axis
    // of its member system positions when that cloud has real spread (the usual case
    // for a multi-system cluster), or - when it does not, a single-system cluster or
    // coincident sites - the principal axis of the member cells' own raw Voronoi
    // vertices instead, so even a lone system's cell shape gives it a real direction
    // rather than defaulting straight to the dot. The site centroid is always kept as
    // the anchor point regardless of which cloud supplied the direction, so the anchor
    // still marks the system's own position, not the cell's vertex-cloud mean.
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

    // Leans the fitted unit axis toward horizontal: its vertical component is scaled by
    // the bias (1 no bias, 0 flat) and the result renormalised, so an ambiguous cluster
    // tips horizontal while a strongly vertical one keeps its slope - a soft lean, not a
    // hard clamp. A fully vertical axis under a flat (zero) bias has no direction left,
    // so it falls back to horizontal outright - which is what a flat bias asks for.
    private static double[] leanTowardHorizontal(PrincipalAxis axis, double horizontalBias) {
        var biasedX = axis.axisX();
        var biasedY = axis.axisY() * horizontalBias;
        var length = Points.computeVectorLength(biasedX, biasedY);
        if (length < Limits.MIN_EDGE_LENGTH) {
            return new double[] {1.0, 0.0};
        }
        return new double[] {biasedX / length, biasedY / length};
    }

    // Whether two unit directions are, for display purposes, the same line: their dot
    // product sits at (effectively) 1. Catches both a no-op bias setting and an axis
    // that was already horizontal (where leaning changes nothing regardless of the
    // bias value) - either way there is nothing distinct for the unbiased line to add
    // over the accepted one.
    private static boolean isSameDirection(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] > SAME_DIRECTION_MIN_DOT_PRODUCT;
    }

    // One directional fit's outcome: the accepted {tStart, tEnd} interval the name may
    // occupy, or - only when nothing was accepted - the best rejected candidate found
    // along the way, for the red diagnostic line. Exactly one of the two is non-null;
    // both are null only when the direction found no interior geometry at all to work
    // with (no border ring, or the line misses the region entirely).
    private record DirectionalFit(double[] acceptedSpan, double[] rejectedSpan) {
    }

    // Fits one direction through a cluster's centroid against its border rings and the
    // system icons: the rings - the same ones the national border strokes, so the
    // anchor clips against what the player sees - bound the line to its genuinely
    // interior pieces (a chord across a concavity or an enclave is never kept); every
    // system icon then carves its keep-out interval from what remains; the longest
    // survivor is pulled in from both ends by the end inset so the name stops short of
    // the border. All sites act as icon blockers, not just the cluster's own: a
    // neighbour faction's icon just across the border can still overhang the inset
    // interior. Falls through three ways a direction can fail to produce an accepted
    // line, keeping the furthest-along candidate as the rejected span: no border ring
    // to clip against (nothing to report), the line missing the interior entirely
    // (nothing to report), the icons consuming every interior span (the longest
    // interior span reported), or the end inset consuming what the icons left (the
    // pre-inset clear span reported).
    private static DirectionalFit fitAlongDirection(List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId, double centroidX, double centroidY,
            double[] direction, AnchorTuning tuning) {
        if (rings.isEmpty()) {
            return new DirectionalFit(null, null);
        }
        var interiorSpans = Polygons.findLineInteriorSpans(rings, centroidX, centroidY,
                direction[0], direction[1]);
        if (interiorSpans.isEmpty()) {
            return new DirectionalFit(null, null);
        }
        var clear = Spans.findLongestClearSubsegment(interiorSpans, centroidX, centroidY,
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
    // the given direction from the centroid - the last step shared by the accepted,
    // rejected, and unbiased lines alike.
    private static ClusterAnchor.AxisSegment toSegment(PrincipalAxis axis, double[] direction,
            double[] span) {
        return new ClusterAnchor.AxisSegment(
                (float) (axis.centroidX() + direction[0] * span[0]),
                (float) (axis.centroidY() + direction[1] * span[0]),
                (float) (axis.centroidX() + direction[0] * span[1]),
                (float) (axis.centroidY() + direction[1] * span[1]));
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
     * The modifiers the cluster-anchor fit reads, gathered into one value so the fit
     * takes its whole tuning surface as data rather than reaching into the settings
     * mid-computation.
     *
     * @param borderTrace      the national-border trace the anchor clips against -
     *                         shared with the territory build, so the anchor sees
     *                         the same rings the player does by construction
     * @param horizontalBias   the scale applied to the anchor axis's vertical
     *                         component before the fit; 1 no bias, 0 flat
     * @param endInsetDistance how far each end of the clear interval pulls inward,
     *                         in world units - the border-inset multiple already
     *                         resolved to a distance
     * @param iconClearance    the keep-out radius around each system icon, world
     *                         units
     * @param showRejectedAxis whether a cluster whose accepted line collapsed also
     *                         carries the best rejected candidate the fit found,
     *                         for the red diagnostic line
     * @param showUnbiasedAxis whether each cluster also carries the line the fit
     *                         would produce with no horizontal bias, for the
     *                         yellow diagnostic line
     */
    record AnchorTuning(BorderTrace borderTrace, double horizontalBias,
            double endInsetDistance, double iconClearance,
            boolean showRejectedAxis, boolean showUnbiasedAxis) {

        // Reads the live tuning: the anchor knobs from the Dev "Label anchors" section
        // plus the same border trace the national border renders with. The end-inset
        // multiple is resolved against the fixed border channel here, so the fit works
        // in plain distances. The two diagnostic-line toggles ride along so the fit
        // only computes the extra candidates while someone is looking at them.
        static AnchorTuning readFromSettings() {
            return new AnchorTuning(
                    BorderTrace.readFromSettings(),
                    KmuLunaSettings.getPoliticalMapAnchorHorizontalBias(),
                    KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                            * PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    KmuLunaSettings.getPoliticalMapAnchorIconClearance(),
                    KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                    KmuLunaSettings.getPoliticalMapShowUnbiasedAxes());
        }
    }
}
