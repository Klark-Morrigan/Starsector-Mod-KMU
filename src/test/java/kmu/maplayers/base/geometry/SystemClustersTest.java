package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link SystemClusters#findClusters}:
 *  - adjacent same-owner cells fuse into one cluster, transitively along a chain,
 *  - same-owner cells with no shared border stay separate clusters,
 *  - adjacent cells of different keys never fuse,
 *  - unowned and cell-less systems carry no cluster,
 *  - a cluster's members are the systems its cells draw as, so a cell with no system of its
 *    own adds no member yet still connects the cells on either side of it.
 *
 * <p>Clustering reads only the adjacency tags and the grouping, never the edge geometry, so
 * the cells here are bare edges tagged with what lies across them - the coordinates are
 * left at zero as they play no part in which cells fuse.
 */
final class SystemClustersTest {

    // One cell edge that faces the given system's cell. Geometry is irrelevant to clustering,
    // so the segment is left at the origin.
    private static CellEdge buildEdgeTo(String neighbourSystemId) {
        return new CellEdge(0, 0, 0, 0, new EdgeTarget.AcrossSystem(neighbourSystemId));
    }

    // One cell edge facing the reach bound - a frontier into empty space.
    private static CellEdge buildBoundEdge() {
        return new CellEdge(0, 0, 0, 0, EdgeTarget.REACH_BOUND);
    }

    // The grouping the clustering runs over: each cell drawing as its own star (identity
    // draws-as over the cell set), keyed by the given owners.
    private static CellGrouping buildGrouping(
            Map<String, List<CellEdge>> edges, Map<String, String> owners) {
        var systemIdByCellId = new java.util.LinkedHashMap<String, String>();
        for (var cellId : edges.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        return new CellGrouping(systemIdByCellId, owners);
    }

    @Nested
    class FindClusters {
        @Test
        void adjacentSameKeySystemsFuseIntoOneCluster() {
            // A and B share a border and both belong to F, so their shared seam fuses
            // them into a single cluster.
            var edges = Map.of(
                    "A", List.of(buildEdgeTo("B")),
                    "B", List.of(buildEdgeTo("A")));
            var owners = Map.of("A", "F", "B", "F");

            var clusters = SystemClusters.findClusters(edges, buildGrouping(edges, owners));

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactlyInAnyOrder("A", "B");
        }

        @Test
        void aChainOfSameKeySystemsFusesTransitively() {
            // A-B and B-C border pairs, all held by F: A and C never touch directly but
            // fuse through B into one cluster.
            var edges = Map.of(
                    "A", List.of(buildEdgeTo("B")),
                    "B", List.of(buildEdgeTo("A"), buildEdgeTo("C")),
                    "C", List.of(buildEdgeTo("B")));
            var owners = Map.of("A", "F", "B", "F", "C", "F");

            var clusters = SystemClusters.findClusters(edges, buildGrouping(edges, owners));

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactlyInAnyOrder("A", "B", "C");
        }

        @Test
        void sameKeySystemsWithNoSharedBorderStaySeparate() {
            // Two F systems that face only empty space (a disjoint pocket each) get their
            // own cluster - one label each, not a name stranded between them.
            var edges = Map.of(
                    "A", List.of(buildBoundEdge()),
                    "B", List.of(buildBoundEdge()));
            var owners = Map.of("A", "F", "B", "F");

            var clusters = SystemClusters.findClusters(edges, buildGrouping(edges, owners));

            assertThat(clusters).hasSize(2);
        }

        @Test
        void adjacentSystemsOfDifferentKeysDoNotFuse() {
            // A and B share a border but belong to F and G, so the seam is a cluster
            // boundary, not a fusing interior seam: two clusters.
            var edges = Map.of(
                    "A", List.of(buildEdgeTo("B")),
                    "B", List.of(buildEdgeTo("A")));
            var owners = Map.of("A", "F", "B", "G");

            var clusters = SystemClusters.findClusters(edges, buildGrouping(edges, owners));

            assertThat(clusters).hasSize(2);
            assertThat(clusters).allSatisfy(cluster -> assertThat(cluster).hasSize(1));
        }

        @Test
        void anUnownedSystemCarriesNoCluster() {
            // B has a cell but no key, so it never seeds a cluster; only grouped A does,
            // and the seam into unowned B does not fuse.
            var edges = Map.of(
                    "A", List.of(buildEdgeTo("B")),
                    "B", List.of(buildEdgeTo("A")));
            var owners = Map.of("A", "F");

            var clusters = SystemClusters.findClusters(edges, buildGrouping(edges, owners));

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactly("A");
        }

        @Test
        void nothingGroupedYieldsNoClusters() {
            var edges = Map.of("A", List.of(buildBoundEdge()));

            var clusters = SystemClusters.findClusters(edges, buildGrouping(edges, Map.of()));

            assertThat(clusters).isEmpty();
        }

        @Test
        void aCellDrawingAsAnotherSystemsStarReportsThatStarNotItsOwnId() {
            // Cell "wedge" is an absorbed cell drawing as key F's star A - it has no star of
            // its own. It borders A's own cell, so it fuses into A's cluster, but the cluster's
            // members are the systems the cells draw as, so it reports A once, never "wedge".
            var edges = Map.of(
                    "A", List.of(buildEdgeTo("wedge")),
                    "wedge", List.of(buildEdgeTo("A")));
            var systemIdByCellId = Map.of("A", "A", "wedge", "A");
            var grouping = new CellGrouping(systemIdByCellId, Map.of("A", "F"));

            var clusters = SystemClusters.findClusters(edges, grouping);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactly("A");
        }
    }
}
