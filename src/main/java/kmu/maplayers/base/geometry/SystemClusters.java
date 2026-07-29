package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups grouped systems into contiguous same-key clusters - the connected
 * components of the cell-adjacency graph.
 *
 * <p>Where a layer's own grouping gathers every system that shares a key regardless of where
 * it sits, this splits a key into its separate clusters: two same-key systems land in one
 * cluster only when a chain of shared cell borders connects them. So a faction with a homeland
 * and a far-flung colony comes back as two clusters, each the natural home for its own label -
 * one name per cluster rather than one name stranded between disjoint pockets.
 *
 * <p>Pure graph work over the adjacency edges and the per-system grouping keys, using the same
 * {@link EdgeClassifier} rule the fills merge on: a cell fuses with a neighbour into one
 * cluster in exactly the cases their fills fuse into one region. Ungrouped and cell-less
 * systems carry no label, so they are left out entirely. Kept free of geometry maths
 * and GL - it decides membership only; the render layer fits an anchor to each cluster.
 */
public final class SystemClusters {

    private SystemClusters() {
    }

    /**
     * Splits the grouped systems into their contiguous same-key clusters.
     *
     * <p>Contiguity is a property of the cells - they are what share borders - so the walk
     * runs over cells and each component's members are then resolved back to the systems
     * they draw as. A cluster is therefore reported as the stars in it, which is what a
     * label names; ground held without a star of its own adds no member of its own but
     * still connects the cells on either side of it.
     *
     * @param edgesByCellId each cell's raw edges, tagged with what lies across them - the
     *                      adjacency graph
     * @param grouping      which system each cell draws as and each system's grouping key;
     *                      an ungrouped or cell-less system is excluded, as it carries no
     *                      label
     * @return one member list per contiguous cluster, in first-seen order (members
     *         likewise); empty when nothing is grouped
     */
    public static List<List<String>> findClusters(Map<String, List<CellEdge>> edgesByCellId,
            CellGrouping grouping) {
        // Union-find keyed by cell id: seed every grouped cell as its own singleton, then
        // fuse across each interior seam. An ungrouped cell seeds nothing, so an edge into
        // it never fuses.
        var parentByCellId = new LinkedHashMap<String, String>();
        for (var cellId : edgesByCellId.keySet()) {
            if (grouping.resolveGroupKeyOf(cellId) != null) {
                parentByCellId.put(cellId, cellId);
            }
        }
        for (var entry : edgesByCellId.entrySet()) {
            var cellId = entry.getKey();
            if (!parentByCellId.containsKey(cellId)) {
                continue;
            }
            var ownGroupKey = grouping.resolveGroupKeyOf(cellId);
            for (var edge : entry.getValue()) {
                fuseAcrossSeam(parentByCellId, grouping, cellId, ownGroupKey, edge);
            }
        }
        return collectComponentsInFirstSeenOrder(parentByCellId, grouping);
    }

    // Fuses this cell with the one across an edge when that edge is an interior seam -
    // both cells present and sharing a grouping key. An edge with no cell across it, or
    // one into an ungrouped or differently-keyed cell, leaves them apart.
    private static void fuseAcrossSeam(Map<String, String> parentByCellId,
            CellGrouping grouping, String cellId, String ownGroupKey, CellEdge edge) {
        // A system's own cell is keyed by that system's id, so the system an edge names
        // across it is also the cell across it. Same-territory needs no fusing - it is one
        // owner's own ground either side, already the same component.
        if (!(edge.target() instanceof EdgeTarget.AcrossSystem acrossSystem)
                || !parentByCellId.containsKey(acrossSystem.systemId())) {
            return;
        }
        if (EdgeClassifier.classifyAcross(edge, ownGroupKey, grouping.groupKeyBySystemId())
                == EdgeClass.INTERIOR_SEAM) {
            union(parentByCellId, cellId, acrossSystem.systemId());
        }
    }

    // Buckets each component's cells under its root and resolves them to the systems they
    // draw as, preserving the order roots are first reached so the clusters (and their
    // members) come back deterministically. A cell with no system of its own contributes no
    // member, and two cells drawing as one system contribute it once.
    private static List<List<String>> collectComponentsInFirstSeenOrder(
            Map<String, String> parentByCellId,
            CellGrouping grouping) {
        var membersByRoot = new LinkedHashMap<String, Set<String>>();
        for (var cellId : parentByCellId.keySet()) {
            var systemId = grouping.resolveDrawnSystemIdOf(cellId);
            if (systemId == null) {
                continue;
            }
            membersByRoot.computeIfAbsent(find(parentByCellId, cellId), root -> new LinkedHashSet<>())
                    .add(systemId);
        }
        var clusters = new ArrayList<List<String>>(membersByRoot.size());
        for (var members : membersByRoot.values()) {
            clusters.add(new ArrayList<>(members));
        }
        return clusters;
    }

    // The representative of a cell's component, compressing the path to the root as it
    // climbs so repeat lookups on a long chain stay near-flat.
    private static String find(Map<String, String> parentByCellId, String cellId) {
        var root = cellId;
        while (!root.equals(parentByCellId.get(root))) {
            root = parentByCellId.get(root);
        }
        for (var walk = cellId; !walk.equals(root);) {
            var next = parentByCellId.get(walk);
            parentByCellId.put(walk, root);
            walk = next;
        }
        return root;
    }

    // Merges the two cells' components by pointing one root at the other.
    private static void union(Map<String, String> parentByCellId, String a, String b) {
        parentByCellId.put(find(parentByCellId, a), find(parentByCellId, b));
    }
}
