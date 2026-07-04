package kmu.politicalmap.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PrincipalAxis;
import kmlib.math.solving.Picks;

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
 * cluster, its label box the highest-scoring of many candidate lines swept across the
 * cluster. This class generates the candidates (a direction fan crossed at parallel
 * offsets) and selects among them; {@link LabelBoxFitter} sizes each into the largest
 * name-holding box - clipped inside the national border, trimmed clear of system icons,
 * pulled short of the border at both ends, and stacked into extra lines where girth is
 * spare. Boxes that lean along the cluster's own axis are favoured over ones that stray
 * from it by a font-height-versus-slope score ({@link LabelSlantPreference}) rather than
 * by bending any direction before the fit; the preferred lean is capped short of vertical
 * and fades to level for round clusters whose axis carries no real direction.
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

    // Rebuilds the cluster-label placements in place: clears the standing list, then -
    // only when the placements are needed - splits the owned systems into contiguous
    // clusters and fits one anchor to each. The placements feed two consumers: the
    // faction-name labels and the debug anchor overlay. Building whenever either is on
    // keeps them a single computation (an SSOT the labels and the overlay share), so the
    // search never runs twice; each consumer then draws only under its own toggle. Shared
    // by the full rebuild and the incremental refresh so an ownership change keeps the
    // placements in step with the fills and borders. Reads the toggles here (not at the
    // call sites) so all paths gate identically; the search's tuning is read here too, so
    // a settings change re-fits on the rebuild it triggers.
    static void rebuildClusterAnchors(List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache,
            Map<String, DominantOwner> ownerBySystemId) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowFactionNames()
                && !KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
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
            var owner = ownerBySystemId.get(memberSystemIds.get(0));
            var axis = resolveClusterAxis(memberSystemIds, edgesBySystemId, sites);
            var rings = tuning.borderTrace().traceRings(memberSystemIds, edgesBySystemId,
                    ownerBySystemId);
            anchors.add(searchClusterAnchor(rings, siteBySystemId, axis, owner.factionId(),
                    owner.primaryColor(), tuning));
        }
        return anchors;
    }

    // Searches one cluster's candidate lines and assembles its anchor as a fitted label
    // box. Every candidate is a direction (a fan over the half-circle, plus pure
    // horizontal, the cluster's own axis, and the preferred slant) crossed at a parallel
    // offset (a sweep across the cluster's perpendicular extent). At each, the box solver
    // sizes the largest stand-in name that fits - growing the band's girth against the
    // border, spending spare girth on extra lines - and the candidate is scored by that
    // fitted font height docked for straying from the cluster's preferred lean. The
    // best-scoring box wins; its clear span is the accepted
    // line and its girth and line count the band a name will fill, and the span's midpoint
    // the point the name hangs on. When no box fits anywhere the anchor collapses to the
    // site centroid dot, optionally carrying the best near-miss span for the red
    // diagnostic. With the unbiased toggle on, the box that wins on raw font height (no
    // slope penalty) rides along as the yellow diagnostic whenever the penalty moved the pick.
    private static ClusterAnchor searchClusterAnchor(List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId, PrincipalAxis axis, String factionId,
            Color color, AnchorTuning tuning) {
        var centroidX = (float) axis.centroidX();
        var centroidY = (float) axis.centroidY();
        if (rings.isEmpty()) {
            // No traceable border leaves nothing to prove a candidate interior - the
            // one dead end the search cannot work around, so only the dot can show.
            return new ClusterAnchor(centroidX, centroidY, color, factionId,
                    null, null, null, 0f, 0);
        }

        var fitter = newBoxFitter(tuning);
        var icons = siteBySystemId.values();
        var slant = LabelSlantPreference.resolveFrom(axis, tuning.maxSlantDegrees());
        var directions = buildCandidateDirections(axis, slant, tuning.directionCount());
        LabelBoxFitter.BoxFit bestAccepted = null;
        var bestScore = 0.0;
        LabelBoxFitter.BoxFit longestAccepted = null;
        RejectedSpan bestRejected = null;
        for (var direction : directions) {
            var extent = projectRingsExtent(rings, -direction[1], direction[0]);
            for (var offsetIndex = 1; offsetIndex <= tuning.offsetCount(); offsetIndex++) {
                var through = offsetThroughPoint(axis, direction, extent, offsetIndex,
                        tuning.offsetCount());
                var placement = new Placement(rings, icons, through[0], through[1], direction);
                var box = fitter.fitLargestBox(placement);
                if (box != null) {
                    // Selection docks the fitted font height for lines that stray from the
                    // cluster's preferred slant - a sizing-blind choice, so it lives here,
                    // not in the fitter; the unbiased pick keeps the raw-height winner for
                    // the yellow diagnostic.
                    var score = box.fontHeight() * slant.computePenaltyMultiplier(direction,
                            tuning.verticalPenaltyStrength(), tuning.verticalPenaltyExponent());
                    if (bestAccepted == null || score > bestScore) {
                        bestAccepted = box;
                        bestScore = score;
                    }
                    longestAccepted = Picks.pickHigher(longestAccepted, box,
                            LabelBoxFitter.BoxFit::fontHeight);
                } else if (tuning.showRejectedAxis()) {
                    bestRejected = Picks.pickHigher(bestRejected,
                            findRejectedSpan(fitter, placement, tuning.bandMinThickness()),
                            RejectedSpan::length);
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
            return new ClusterAnchor(midX, midY, color, factionId, accepted, null, unbiased,
                    (float) bestAccepted.thickness(), bestAccepted.lineCount());
        }
        // Collapse: no box fit anywhere, so the dot marks the site centroid; the best
        // near-miss span rides along only when the rejected toggle asked for it.
        var rejected = bestRejected != null ? bestRejected.segment() : null;
        return new ClusterAnchor(centroidX, centroidY, color, factionId,
                null, rejected, null, 0f, 0);
    }

    // Builds the box fitter from the tuning: the thickness clamp, line count, and spacing
    // that shape the girth growth, the icon clearance and end inset every band trim reads,
    // and the stand-in name model the fit sizes against. The one place the aspect stand-in
    // is wired in, so a font-backed model later swaps in here alone.
    private static LabelBoxFitter newBoxFitter(AnchorTuning tuning) {
        return new LabelBoxFitter(tuning.bandMinThickness(), tuning.bandMaxThickness(),
                tuning.bandMaxLines(), tuning.bandLineSpacing(), tuning.iconClearance(),
                tuning.endInsetDistance(), new AspectNameLengthModel(tuning.bandAspect()));
    }

    // The candidate directions for one cluster: an even fan of unit directions over the
    // half-circle (index 0 is exactly horizontal, so horizontal is always searched),
    // plus the cluster's own principal axis so an elongated cluster can fit along its long
    // dimension between two fan spokes, plus the preferred slant so the winner can land
    // exactly on the cluster's capped lean rather than the nearest spoke. Directions are
    // lines, not arrows - the half-circle covers every slope, and the search treats a
    // direction and its opposite as one line.
    private static List<double[]> buildCandidateDirections(PrincipalAxis axis,
            LabelSlantPreference slant, int directionCount) {
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

    // The best near-miss line for the red diagnostic: a candidate's clear span before the
    // end-margin trim, kept with its length so the longest across candidates wins.
    private record RejectedSpan(ClusterAnchor.AxisSegment segment, double length) {
    }

    // A candidate's near-miss span for the red diagnostic: the pre-margin clear span of a
    // minimum-thickness band - the furthest a name-holding line got before the border,
    // icon, or end-margin trim discarded it. Null when even the thin band finds no clear
    // interior at all.
    private static RejectedSpan findRejectedSpan(LabelBoxFitter fitter, Placement placement,
            double minThickness) {
        var band = fitter.fitBand(placement, minThickness / 2.0);
        if (band.clearSpan() == null) {
            return null;
        }
        return new RejectedSpan(placement.toSegment(band.clearSpan()),
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
        // The vertex cloud supplies the direction, so it also supplies the minor extent -
        // the slant gate reads the elongation of whichever cloud gave the axis, not the
        // site cloud's (which had no usable spread here).
        return new PrincipalAxis(siteAxis.centroidX(), siteAxis.centroidY(),
                vertexAxis.axisX(), vertexAxis.axisY(), vertexAxis.length(),
                vertexAxis.minorLength());
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
     * @param verticalPenaltyStrength how much font height a line straying from the
     *                               cluster's preferred lean may give up and still win,
     *                               0 (pure longest) to 1 (a perpendicular line scores
     *                               zero)
     * @param verticalPenaltyExponent the exponent on the line's deviation from the lean
     *                               in the score, concentrating the penalty toward the
     *                               perpendicular
     * @param maxSlantDegrees        the ceiling on the cluster-axis lean the score
     *                               prefers, in degrees; 0 forces level labels, 90 lets
     *                               the lean follow a tall cluster's axis to vertical
     * @param showRejectedAxis       whether a cluster whose accepted line collapsed also
     *                               carries the best rejected candidate the search found,
     *                               for the red diagnostic line
     * @param showUnbiasedAxis       whether each cluster also carries the pure-longest
     *                               accepted line (the winner with no vertical penalty),
     *                               for the yellow diagnostic line
     * @param bandAspect             the stand-in name's length as a multiple of one line's
     *                               height, sized against before real fonts exist
     * @param bandMinThickness       the smallest band girth a fit will accept, world units
     *                               - below it a placement collapses to the dot
     * @param bandMaxThickness       the largest band girth a fit will grow to, world units,
     *                               so a roomy cluster does not mint an oversized label
     * @param bandMaxLines           the most lines a name may stack into, spending girth to
     *                               shorten the length it needs
     * @param bandLineSpacing        the line-height multiple a multi-line band leaves
     *                               between lines, at least 1
     */
    record AnchorTuning(BorderTrace borderTrace, double endInsetDistance, double iconClearance,
            int directionCount, int offsetCount, double verticalPenaltyStrength,
            double verticalPenaltyExponent, double maxSlantDegrees, boolean showRejectedAxis,
            boolean showUnbiasedAxis, double bandAspect, double bandMinThickness,
            double bandMaxThickness, int bandMaxLines, double bandLineSpacing) {

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
                    KmuLunaSettings.getPoliticalMapAnchorMaxSlantDegrees(),
                    KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                    KmuLunaSettings.getPoliticalMapShowUnbiasedAxes(),
                    KmuLunaSettings.getPoliticalMapAnchorBandAspect(),
                    KmuLunaSettings.getPoliticalMapAnchorBandMinThickness(),
                    KmuLunaSettings.getPoliticalMapAnchorBandMaxThickness(),
                    KmuLunaSettings.getPoliticalMapAnchorBandMaxLines(),
                    KmuLunaSettings.getPoliticalMapAnchorBandLineSpacing());
        }
    }
}
