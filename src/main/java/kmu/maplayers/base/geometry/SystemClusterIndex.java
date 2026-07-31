package kmu.maplayers.base.geometry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers which cluster a single system belongs to - the reverse of
 * {@link SystemClusters#findClusters}, which reports the clusters but not where any one
 * system landed.
 *
 * <p>Hover asks the question from the other end: the cursor resolves to one system, and the
 * highlight needs the whole contiguous cluster around it. Scanning every cluster for the
 * hit system each frame would repeat the search the map already ran, so the clusters are
 * indexed by member once per rebuild and each hover is a lookup.
 *
 * <p>Deriving the index from {@link SystemClusters}' own output rather than walking the
 * adjacency graph again is what keeps the highlight and the labels agreeing on where one
 * cluster ends: the same components decide both, so a highlighted cluster is exactly the
 * cluster that carries one name. Disjoint pockets of one key stay distinct, since they
 * are distinct components.
 *
 * <p>Pure lookup over plain ids - no geometry, no GL, no Starsector types.
 */
public final class SystemClusterIndex {

    // Each member system's whole cluster, shared per component - every member of one cluster
    // maps to the same list instance, so the index costs one entry per system, not per pair.
    private final Map<String, List<String>> clusterMembersBySystemId;

    private SystemClusterIndex(Map<String, List<String>> clusterMembersBySystemId) {
        this.clusterMembersBySystemId = clusterMembersBySystemId;
    }

    /**
     * Indexes the clusters by their members, so any one of them resolves back to its whole
     * cluster.
     *
     * <p>A system belongs to at most one cluster (they are connected components of one graph),
     * so no member can be claimed twice; a system absent from every cluster - ungrouped or
     * cell-less - is simply absent from the index.
     *
     * @param clusters the contiguous same-key clusters, as {@link SystemClusters#findClusters}
     *                 returns them: one member list per cluster
     * @return an index over those clusters; lookups on it never see the caller's later edits,
     *         and the member lists it hands back cannot be modified
     */
    public static SystemClusterIndex indexClusters(List<List<String>> clusters) {
        var clusterMembersBySystemId = new LinkedHashMap<String, List<String>>();
        for (var cluster : clusters) {
            // Copied so the index (and every member list it hands out) stays fixed even if the
            // caller reuses or mutates the lists the search returned.
            var members = List.copyOf(cluster);
            for (var systemId : members) {
                clusterMembersBySystemId.put(systemId, members);
            }
        }
        return new SystemClusterIndex(clusterMembersBySystemId);
    }

    /**
     * The whole cluster the system belongs to, in the order the search reported its members.
     *
     * <p>Includes the system itself, so a lone system in its own cluster comes back as a
     * single-member list rather than an empty one - the caller need not add it back.
     *
     * @param systemId the system to resolve; null (nothing hovered) resolves to no cluster
     * @return the member ids of its cluster, or an empty list when the system carries no
     *         cluster - it is ungrouped, cell-less, or unknown to this index
     */
    public List<String> findClusterMembersOf(String systemId) {
        return clusterMembersBySystemId.getOrDefault(systemId, List.of());
    }
}
