package kmu.politicalmap.domain.geometry;

import kmu.politicalmap.domain.politics.DominantOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups owned systems into contiguous same-faction clusters - the connected
 * components of the cell-adjacency graph.
 *
 * <p>Where {@link DrawablesBuilder}'s faction grouping (in the render layer) gathers
 * every system a faction holds regardless of where it sits, this splits a faction into
 * its separate clusters: two same-faction systems land in one cluster only when a chain
 * of shared cell borders connects them. So a faction with a homeland and a far-flung
 * colony comes back as two clusters, each the natural home for its own label - one name
 * per cluster rather than one name stranded between disjoint pockets.
 *
 * <p>Pure graph work over the adjacency edges and the resolved owners, using the same
 * {@link EdgeClassifier} rule the fills merge on: a cell fuses with a neighbour into one
 * cluster in exactly the cases their fills fuse into one region. Unowned and cell-less
 * systems carry no label, so they are left out entirely. Kept free of geometry maths
 * and GL - it decides membership only; the render layer fits an anchor to each cluster.
 */
public final class SystemClusters {

    private SystemClusters() {
    }

    /**
     * Splits the owned systems into their contiguous same-faction clusters.
     *
     * @param edgesBySystemId each system's raw cell edges, tagged with the neighbour
     *                        across them - the adjacency graph
     * @param ownerBySystemId the dominant owner per system; a system absent here (or
     *                        absent from the graph) is unowned or cell-less and is
     *                        excluded, as it carries no label
     * @return one member list per contiguous cluster, in first-seen order (members
     *         likewise); empty when nothing is owned
     */
    public static List<List<String>> findClusters(Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, DominantOwner> ownerBySystemId) {
        // Union-find keyed by system id: seed every owned, cell-bearing system as its
        // own singleton, then fuse across each interior seam. A cell-less system seeds
        // nothing, so an edge into it never fuses.
        var parentBySystemId = new LinkedHashMap<String, String>();
        for (var systemId : edgesBySystemId.keySet()) {
            if (ownerBySystemId.get(systemId) != null) {
                parentBySystemId.put(systemId, systemId);
            }
        }
        for (var entry : edgesBySystemId.entrySet()) {
            var systemId = entry.getKey();
            if (!parentBySystemId.containsKey(systemId)) {
                continue;
            }
            var ownerFactionId = DominantOwner.factionIdOf(ownerBySystemId.get(systemId));
            for (var edge : entry.getValue()) {
                fuseAcrossSeam(parentBySystemId, ownerBySystemId, systemId, ownerFactionId, edge);
            }
        }
        return collectComponentsInFirstSeenOrder(parentBySystemId);
    }

    // Fuses this system with the neighbour across one edge when that edge is an interior
    // seam - both cells present and held by the same faction. A frontier edge (no
    // neighbour) or one into a cell-less or differently-owned system leaves them apart.
    private static void fuseAcrossSeam(Map<String, String> parentBySystemId,
            Map<String, DominantOwner> ownerBySystemId, String systemId, String ownerFactionId,
            CellEdge edge) {
        var neighbourId = edge.neighbourSystemId();
        if (neighbourId == null || !parentBySystemId.containsKey(neighbourId)) {
            return;
        }
        if (EdgeClassifier.classifyAcross(edge, ownerFactionId, ownerBySystemId)
                == EdgeClass.INTERIOR_SEAM) {
            union(parentBySystemId, systemId, neighbourId);
        }
    }

    // Buckets each system under its component root, preserving the order roots are first
    // reached so the clusters (and their members) come back deterministically.
    private static List<List<String>> collectComponentsInFirstSeenOrder(
            Map<String, String> parentBySystemId) {
        var membersByRoot = new LinkedHashMap<String, List<String>>();
        for (var systemId : parentBySystemId.keySet()) {
            membersByRoot.computeIfAbsent(find(parentBySystemId, systemId), root -> new ArrayList<>())
                    .add(systemId);
        }
        return new ArrayList<>(membersByRoot.values());
    }

    // The representative of a system's component, compressing the path to the root as it
    // climbs so repeat lookups on a long chain stay near-flat.
    private static String find(Map<String, String> parentBySystemId, String systemId) {
        var root = systemId;
        while (!root.equals(parentBySystemId.get(root))) {
            root = parentBySystemId.get(root);
        }
        for (var walk = systemId; !walk.equals(root);) {
            var next = parentBySystemId.get(walk);
            parentBySystemId.put(walk, root);
            walk = next;
        }
        return root;
    }

    // Merges the two systems' components by pointing one root at the other.
    private static void union(Map<String, String> parentBySystemId, String a, String b) {
        parentBySystemId.put(find(parentBySystemId, a), find(parentBySystemId, b));
    }
}
