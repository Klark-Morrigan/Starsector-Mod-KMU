package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.Disks;
import kmlib.math.geometry.HalfPlane;
import kmlib.math.geometry.LabelledPolygon;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.VoronoiCellBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Redistributes each frontier-empty cell into the owners that reach into it, a keep-out
 * pocket around its dead star, and the space capped beyond an owner's reach - the pass that
 * makes a faction flow around an unheld star and stop short of swallowing it.
 *
 * <p>Runs between the raw Voronoi partition and the shaping layer, gated on the toggle: with
 * it off the input passes through untouched, so the map keeps today's midline look; with it
 * on, only the cells that touch both a dead star and somebody's territory are rewritten, and
 * every distant void draws as before. An owned or deep-empty cell is never touched.
 *
 * <p>For each frontier-empty cell E the pass partitions E among its owned neighbours ALONE -
 * the Voronoi over the owned sites only, capped at each owner's reach - rather than splitting
 * E among all its neighbours and capping each wedge to its own owner's disk afterward. The
 * owned-only partition gives two same-owner wedges one shared bisector and an exact tiling,
 * where the after-the-fact cap would cut each wedge on a disk of its own and leave the shared
 * edge mixed, reopening the orphan-loop artifact the redistribution exists to kill. Each
 * owner's wedge is the owned-only cell clipped to E; the pocket, a disk withheld around the
 * dead star; the rest - the space past every owner's reach - stays neutral.
 *
 * <p>The pocket is carved out of the owned wedges - each owner's absorbed region has the disk
 * around the dead star withheld and re-emitted as neutral ground - so the pocket is a hole in
 * whoever's territory surrounds the star, and nothing more. Where the star sits in open space
 * no owner reaches (its owners capped far short), no wedge covers it, so no pocket is emitted
 * and the star is simply part of the neutral remainder - the pocket never lands on top of the
 * capped space, which is what keeps the pieces from overlapping. It is sized
 * {@code min(keepOut, half the spacing to the nearest owned neighbour)}, mirroring
 * {@link FrontierSetback}'s "never past the halfway line" so it stays clear of the shared
 * bisectors and each owner's slice of it lies within that owner's own wedge.
 *
 * <p>Pure geometry over the adjacency graph, the per-system grouping keys, and the frontier
 * snapshot; no cache, no GL. It leans on kmlib for every clip - the owned-only cell and its
 * cap ({@link VoronoiCellBuilder#buildLabelledCell}), the pocket and the reach cap
 * ({@link Disks}), and the half-plane splits ({@link LabelledPolygon}) - and only decides
 * which piece each clip produces, and what an emitted edge faces.
 */
public final class FrontierRedistribution {

    private FrontierRedistribution() {
    }

    /**
     * Rewrites the frontier-empty cells of a partition into owned wedges, pockets, and capped
     * neutral shards, leaving every other cell as it was.
     *
     * <p>The disk clips (pocket and reach cap) are approximated at {@code boundSegments}, the
     * same count the cells were seeded at, so a cap arc lands on the very chords the owned
     * cell's own bound carries and the two meet with no gap.
     *
     * @param cellEdgesByCellId the raw partition: each cell as its adjacency-tagged edges
     * @param grouping          which system each cell draws as and each system's grouping key
     * @param settings          the frontier snapshot: sites, keep-out and cell radii, toggle
     * @param boundSegments     sides of the polygon approximating each disk, matching the
     *                          count the cells' own bounds were seeded at
     * @return the rewritten cell set and draws-as map; the input unchanged when the toggle is
     *         off or no cell is frontier-empty
     */
    public static RedistributedCells redistribute(
            Map<String, List<CellEdge>> cellEdgesByCellId,
            CellGrouping grouping,
            FrontierSettings settings,
            int boundSegments) {
        // Toggle off leaves the partition exactly as it came in - the clean fallback the
        // whole design rests on, not a special case woven through the pass.
        if (!settings.isEnabled()) {
            return new RedistributedCells(cellEdgesByCellId, grouping);
        }
        var frontierEmptyCellIds =
                EmptyCellClassifier.collectFrontierEmptyCellIds(cellEdgesByCellId, grouping);
        if (frontierEmptyCellIds.isEmpty()) {
            return new RedistributedCells(cellEdgesByCellId, grouping);
        }

        var sink = new RedistributedCellSink();
        for (var cell : cellEdgesByCellId.entrySet()) {
            var cellId = cell.getKey();
            if (frontierEmptyCellIds.contains(cellId)) {
                redistributeCell(cellId, cell.getValue(), grouping, settings, boundSegments, sink);
            } else {
                sink.passThrough(cellId, cell.getValue(), grouping.resolveDrawnSystemIdOf(cellId));
            }
        }
        return sink.build(grouping.groupKeyBySystemId());
    }

    // Rewrites one frontier-empty cell E into its parts. Resolves E's dead-star site and its
    // owned neighbours; when E has no resolvable site or no resolvable owned neighbour it cannot
    // be redistributed, so it passes through as the plain neutral cell it already is. Otherwise
    // the resolved cell is emitted as owner wedges, keep-out pockets, and capped neutral shards.
    private static void redistributeCell(
            String cellId,
            List<CellEdge> edges,
            CellGrouping grouping,
            FrontierSettings settings,
            int boundSegments,
            RedistributedCellSink sink) {
        var deadStarSite = resolveDeadStarSite(cellId, grouping, settings);
        var ownedNeighbours = collectOwnedNeighbours(edges, grouping, settings);
        if (deadStarSite == null || ownedNeighbours.isEmpty()) {
            sink.passThrough(cellId, edges, grouping.resolveDrawnSystemIdOf(cellId));
            return;
        }
        var cell = new FrontierCell(
                cellId,
                edges,
                deadStarSite,
                ownedNeighbours,
                collectSites(ownedNeighbours),
                computePocketRadius(deadStarSite, ownedNeighbours, settings));
        emitOwnedWedgesAndPockets(cell, settings, boundSegments, sink);
        emitCappedShards(cell, settings, boundSegments, sink);
    }

    // The part of E each owner absorbs, and the pocket carved out of it. An owner's absorbed
    // region is its owned-only cell (capped at its reach) clipped to E; the disk around the
    // dead star is withheld from it as the owner's fill and re-emitted as the neutral pocket.
    // The absorbed piece draws as the owner's star, so it joins the owner's cluster: its
    // bisector with another owned wedge faces that owner (fusing if same faction, a border if
    // rival), its cap arc and pocket rim face the neutral space across them (a border), and its
    // cut against E's own boundary faces whatever E faced there - so an absorbed wedge fuses
    // back onto its owner's own cell along that shared edge.
    private static void emitOwnedWedgesAndPockets(
            FrontierCell cell, FrontierSettings settings, int boundSegments,
            RedistributedCellSink sink) {
        for (var ownerIndex = 0; ownerIndex < cell.ownedNeighbours().size(); ownerIndex++) {
            var owner = cell.ownedNeighbours().get(ownerIndex);
            var targets = new ArrayList<EdgeTarget>();
            var ownedCell = toOwnedCellPolygon(
                    VoronoiCellBuilder.buildLabelledCell(
                            ownerIndex, cell.ownedSites(), settings.cellRadius(), boundSegments),
                    cell.ownedNeighbours(),
                    targets);
            var absorbed = clipToPolygonInterior(ownedCell, cell.edges(), targets);
            if (absorbed.isEmpty()) {
                continue;
            }
            for (var piece : withholdPocket(
                    absorbed,
                    cell.deadStarSite(),
                    cell.pocketRadius(),
                    boundSegments,
                    targets)) {
                sink.addOwnedWedge(cell.cellId(), owner.systemId(), toCellEdges(piece, targets));
            }
            emitPocketSlice(cell, owner, absorbed, boundSegments, sink);
        }
    }

    // The owner's slice of the keep-out pocket: the part of its absorbed region inside the disk
    // around the dead star. Emitted as one convex neutral cell; its whole rim faces the owner's
    // fill (a border), so an unlabelled clip suffices. Where two owners surround the star their
    // slices meet on the owned bisector - each strokes that seam, a faint line across the
    // clearing, the one cosmetic cost of carving per owner rather than pooling the pocket.
    private static void emitPocketSlice(
            FrontierCell cell,
            OwnedNeighbour owner,
            LabelledPolygon absorbed,
            int boundSegments,
            RedistributedCellSink sink) {
        if (cell.pocketRadius() < Limits.MIN_EDGE_LENGTH) {
            return;
        }
        var pocket = Disks.intersectWithDisk(
                absorbed.getVertices(), cell.deadStarSite(), cell.pocketRadius(), boundSegments);
        sink.addPocketSlice(cell.cellId(), owner.systemId(), toReachBoundEdges(pocket));
    }

    // The space past every owner's reach: for each owner, its owned-only wedge of E with the
    // reach disk removed, so what is left lies beyond the cap. Emitted as neutral shards. The
    // wedge's bisectors are stamped SAME_TERRITORY so two owners' capped shards fuse into one
    // neutral region rather than stroking a border between them; the reach arc faces the owned
    // wedge just inside it (a border), and E's own edges keep facing whatever E faced.
    private static void emitCappedShards(
            FrontierCell cell,
            FrontierSettings settings,
            int boundSegments,
            RedistributedCellSink sink) {
        for (var ownerIndex = 0; ownerIndex < cell.ownedNeighbours().size(); ownerIndex++) {
            var owner = cell.ownedNeighbours().get(ownerIndex);
            var targets = new ArrayList<EdgeTarget>();
            var wedge = buildNeutralWedge(cell.edges(), cell.ownedSites(), ownerIndex, targets);
            if (wedge.isEmpty()) {
                continue;
            }
            for (var shard : Disks.subtractDiskWithLabels(
                    wedge,
                    owner.site(),
                    settings.cellRadius(),
                    boundSegments,
                    registerTarget(targets, new EdgeTarget.AcrossSystem(owner.systemId())),
                    registerTarget(targets, EdgeTarget.SAME_TERRITORY))) {
                sink.addCappedShard(cell.cellId(), toCellEdges(shard, targets));
            }
        }
    }

    // E clipped to the half-plane nearer owner {@code ownerIndex} than any other owned site -
    // the owned-only Voronoi wedge, uncapped, before the reach disk is removed. Seeded from E
    // (so it keeps E's own edge targets) and cut by each rival owner's bisector, every cut
    // stamped SAME_TERRITORY: two owners' capped shards on either side of that bisector are
    // both neutral, so the seam between them must fuse rather than stroke.
    private static LabelledPolygon buildNeutralWedge(
            List<CellEdge> edges,
            List<double[]> ownedSites,
            int ownerIndex,
            List<EdgeTarget> targets) {
        var wedge = toLabelledPolygon(edges, targets);
        var owner = ownedSites.get(ownerIndex);
        for (var other = 0; other < ownedSites.size() && !wedge.isEmpty(); other++) {
            if (other == ownerIndex) {
                continue;
            }
            wedge = wedge.clipToHalfPlane(
                    bisectorTowardOwner(owner, ownedSites.get(other)),
                    registerTarget(targets, EdgeTarget.SAME_TERRITORY));
        }
        return wedge;
    }

    // Intersects a subject with E's interior: E is convex and counter-clockwise, so each of
    // its edges is a half-plane whose inward (left) normal keeps E's side. A cut the clip makes
    // lies on E's boundary and faces whatever E faced there, so it is stamped with that edge's
    // own target.
    private static LabelledPolygon clipToPolygonInterior(
            LabelledPolygon subject,
            List<CellEdge> edges,
            List<EdgeTarget> targets) {
        var clipped = subject;
        for (var edge : edges) {
            if (clipped.isEmpty()) {
                break;
            }
            var halfPlane = new HalfPlane(edge.x1(), edge.y1(),
                    -(edge.y2() - edge.y1()), edge.x2() - edge.x1());
            clipped = clipped.clipToHalfPlane(halfPlane, registerTarget(targets, edge.target()));
        }
        return clipped;
    }

    // Withholds the keep-out pocket from an absorbed wedge: the wedge minus the disk around the
    // dead star, as convex pieces. The rim faces the neutral pocket (a border); a fan cut that
    // splits the wedge in two faces more of the same owner (fuse). A radius too small to
    // enclose area withholds nothing, so the wedge comes back whole.
    private static List<LabelledPolygon> withholdPocket(
            LabelledPolygon absorbed,
            double[] deadStarSite,
            double pocketRadius,
            int boundSegments,
            List<EdgeTarget> targets) {
        if (pocketRadius < Limits.MIN_EDGE_LENGTH) {
            return List.of(absorbed);
        }
        return Disks.subtractDiskWithLabels(
                absorbed,
                deadStarSite,
                pocketRadius,
                boundSegments,
                registerTarget(targets, EdgeTarget.REACH_BOUND),
                registerTarget(targets, EdgeTarget.SAME_TERRITORY));
    }

    // The half-plane of points at least as near {@code owner} as {@code rival}: the bisector
    // through their midpoint with the normal pointing at the owner, so a clip keeps the owner's
    // side. Mirrors the Voronoi builder's own bisector clip.
    private static HalfPlane bisectorTowardOwner(double[] owner, double[] rival) {
        return new HalfPlane(
                (owner[0] + rival[0]) * 0.5, (owner[1] + rival[1]) * 0.5,
                owner[0] - rival[0], owner[1] - rival[1]);
    }

    // Restates an owned-only cell as a labelled polygon in the pass's target vocabulary: an
    // edge cut by another owner's bisector faces that owner; an edge on the reach bound faces
    // the neutral space at the cap.
    private static LabelledPolygon toOwnedCellPolygon(
            VoronoiCellBuilder.LabelledCell cell,
            List<OwnedNeighbour> ownedNeighbours,
            List<EdgeTarget> targets) {
        var vertices = cell.vertices();
        var siteIndices = cell.edgeNeighbourSiteIndices();
        var labels = new int[vertices.size()];
        for (var i = 0; i < vertices.size(); i++) {
            var target = siteIndices[i] == VoronoiCellBuilder.BOUND_EDGE
                    ? EdgeTarget.REACH_BOUND
                    : new EdgeTarget.AcrossSystem(ownedNeighbours.get(siteIndices[i]).systemId());
            labels[i] = registerTarget(targets, target);
        }
        return LabelledPolygon.fromLabelledEdges(vertices, labels);
    }

    // E's edge ring as a labelled polygon, every edge carrying its own far-side target.
    private static LabelledPolygon toLabelledPolygon(
            List<CellEdge> edges, List<EdgeTarget> targets) {
        var vertices = new ArrayList<double[]>(edges.size());
        var labels = new int[edges.size()];
        for (var i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            vertices.add(new double[] {edge.x1(), edge.y1()});
            labels[i] = registerTarget(targets, edge.target());
        }
        return LabelledPolygon.fromLabelledEdges(vertices, labels);
    }

    // A labelled piece as cell edges, resolving each edge's integer label to the target it was
    // registered under.
    private static List<CellEdge> toCellEdges(LabelledPolygon piece, List<EdgeTarget> targets) {
        var labels = piece.getEdgeLabels();
        return buildRing(piece.getVertices(), i -> targets.get(labels[i]));
    }

    // A convex ring whose every edge is a border against the surrounding territory - the
    // pocket, which faces owned wedges all the way round.
    private static List<CellEdge> toReachBoundEdges(List<double[]> ring) {
        return buildRing(ring, i -> EdgeTarget.REACH_BOUND);
    }

    // Walks a vertex ring into closed cell edges, asking {@code edgeTarget} what edge {@code i}
    // (vertex i to vertex i + 1, modulo the count) faces - the one ring walk both edge builders
    // share, differing only in how they name each edge's far side.
    private static List<CellEdge> buildRing(
            List<double[]> vertices,
            IntFunction<EdgeTarget> edgeTarget) {
        var count = vertices.size();
        var cellEdges = new ArrayList<CellEdge>(count);
        for (var i = 0; i < count; i++) {
            var start = vertices.get(i);
            var end = vertices.get((i + 1) % count);
            cellEdges.add(new CellEdge(start[0], start[1], end[0], end[1], edgeTarget.apply(i)));
        }
        return cellEdges;
    }

    // The dead star E draws as, resolved to its site; null when E has no star or no site, in
    // which case E cannot be redistributed and passes through unchanged.
    private static double[] resolveDeadStarSite(
            String cellId, CellGrouping grouping, FrontierSettings settings) {
        var systemId = grouping.resolveDrawnSystemIdOf(cellId);
        return systemId == null ? null : settings.siteBySystemId().get(systemId);
    }

    // E's distinct owned neighbours: the systems across its edges that carry a grouping key and
    // whose site is known. First occurrence wins, so the order is E's own edge order.
    private static List<OwnedNeighbour> collectOwnedNeighbours(
            List<CellEdge> edges, CellGrouping grouping, FrontierSettings settings) {
        var seen = new LinkedHashSet<String>();
        var ownedNeighbours = new ArrayList<OwnedNeighbour>();
        for (var edge : edges) {
            if (!(edge.target() instanceof EdgeTarget.AcrossSystem across)) {
                continue;
            }
            var systemId = across.systemId();
            if (grouping.groupKeyBySystemId().get(systemId) == null) {
                continue;
            }
            var site = settings.siteBySystemId().get(systemId);
            if (site != null && seen.add(systemId)) {
                ownedNeighbours.add(new OwnedNeighbour(systemId, site));
            }
        }
        return ownedNeighbours;
    }

    private static List<double[]> collectSites(List<OwnedNeighbour> ownedNeighbours) {
        var sites = new ArrayList<double[]>(ownedNeighbours.size());
        for (var ownedNeighbour : ownedNeighbours) {
            sites.add(ownedNeighbour.site());
        }
        return sites;
    }

    // How large a pocket to withhold: the keep-out radius, clamped to half the spacing to the
    // nearest owned neighbour so the pocket never crosses a shared bisector.
    private static double computePocketRadius(
            double[] deadStarSite,
            List<OwnedNeighbour> ownedNeighbours,
            FrontierSettings settings) {
        var nearestSpacing = Double.POSITIVE_INFINITY;
        for (var ownedNeighbour : ownedNeighbours) {
            nearestSpacing = Math.min(nearestSpacing,
                    Points.computeDistance(deadStarSite, ownedNeighbour.site()));
        }
        return Math.min(settings.keepOutRadius(), nearestSpacing * 0.5);
    }

    private static int registerTarget(List<EdgeTarget> targets, EdgeTarget target) {
        targets.add(target);
        return targets.size() - 1;
    }

    // One frontier-empty cell resolved for redistribution: its id and outline, its dead star's
    // site, the owned neighbours reaching into it (and their sites), and the pocket radius.
    // Built once so the emitters share it rather than re-resolving or re-deriving per pass.
    private record FrontierCell(
            String cellId,
            List<CellEdge> edges,
            double[] deadStarSite,
            List<OwnedNeighbour> ownedNeighbours,
            List<double[]> ownedSites,
            double pocketRadius) {
    }

    // One owned neighbour of a frontier-empty cell: the system id it draws as, paired with the
    // site its wedge is carved around.
    private record OwnedNeighbour(String systemId, double[] site) {
    }

    // Accumulates the pass's output - the emitted cells and the system each draws as - with the
    // drop-if-degenerate guard and the unique-id scheme in one place, so every emitter just
    // names what it adds rather than restating either. The id scheme lives here alone: an owner
    // wedge under {@code E@owner#n}, its pocket slice under {@code E~pocket@owner#n}, a capped
    // shard under {@code E~void#n}, with {@code n} the running cell count for uniqueness.
    private static final class RedistributedCellSink {
        private final Map<String, List<CellEdge>> cellEdgesByCellId = new LinkedHashMap<>();
        private final Map<String, String> drawnSystemByCellId = new LinkedHashMap<>();

        // Copies a cell straight through, keeping the system it draws as. A cell with no drawn
        // system (a neutral cell with no star) stays absent from the draws-as map, so it stays
        // neutral.
        private void passThrough(String cellId, List<CellEdge> edges, String drawnSystemId) {
            cellEdgesByCellId.put(cellId, edges);
            if (drawnSystemId != null) {
                drawnSystemByCellId.put(cellId, drawnSystemId);
            }
        }

        // Adds one piece of an owner's absorbed ground, drawing as that owner's star so it joins
        // the owner's cluster. Dropped silently when it no longer encloses area.
        private void addOwnedWedge(String cellId, String ownerSystemId, List<CellEdge> edges) {
            var id = addPiece(cellId + "@" + ownerSystemId + "#", edges);
            if (id != null) {
                drawnSystemByCellId.put(id, ownerSystemId);
            }
        }

        // Adds one owner's slice of the keep-out pocket, neutral (no draws-as entry). Dropped
        // silently when it no longer encloses area.
        private void addPocketSlice(String cellId, String ownerSystemId, List<CellEdge> edges) {
            addPiece(cellId + "~pocket@" + ownerSystemId + "#", edges);
        }

        // Adds one shard of the space capped past an owner's reach, neutral (no draws-as entry).
        // Dropped silently when it no longer encloses area.
        private void addCappedShard(String cellId, List<CellEdge> edges) {
            addPiece(cellId + "~void#", edges);
        }

        private RedistributedCells build(Map<String, String> groupKeyBySystemId) {
            return new RedistributedCells(
                    cellEdgesByCellId, new CellGrouping(drawnSystemByCellId, groupKeyBySystemId));
        }

        // Puts a piece under a unique id built from the prefix, or drops it when it is too small
        // to enclose area (a sliver no consumer would draw); returns the id used, or null when
        // dropped.
        private String addPiece(String idPrefix, List<CellEdge> edges) {
            if (edges.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                return null;
            }
            var id = idPrefix + cellEdgesByCellId.size();
            cellEdgesByCellId.put(id, edges);
            return id;
        }
    }
}
