package kmu.politicalmap.domain.geometry;

import kmu.politicalmap.domain.politics.DominantOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link SystemClusters#findClusters}:
 *  - adjacent same-faction systems fuse into one cluster, transitively along a chain,
 *  - same-faction systems with no shared border stay separate clusters,
 *  - adjacent systems of different factions never fuse,
 *  - unowned and cell-less systems carry no cluster.
 *
 * <p>Clustering reads only the adjacency tags and the owners, never the edge geometry,
 * so the cells here are bare edges tagged with their neighbour - the coordinates are
 * left at zero as they play no part in which systems fuse.
 */
final class SystemClustersTest {

    private static final DominantOwner FACTION_F =
            new DominantOwner("F", Color.RED, Color.RED.darker());
    private static final DominantOwner FACTION_G =
            new DominantOwner("G", Color.BLUE, Color.BLUE.darker());

    // One cell edge that borders the given neighbour (null for a frontier into empty
    // space). Geometry is irrelevant to clustering, so the segment is left at the origin.
    private static CellEdge edgeTo(String neighbour) {
        return new CellEdge(0, 0, 0, 0, neighbour);
    }

    @Nested
    class FindClusters {
        @Test
        void adjacent_same_faction_systems_fuse_into_one_cluster() {
            // A and B share a border and both belong to F, so their shared seam fuses
            // them into a single cluster.
            var edges = Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A")));
            var owners = Map.of("A", FACTION_F, "B", FACTION_F);

            var clusters = SystemClusters.findClusters(edges, owners);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactlyInAnyOrder("A", "B");
        }

        @Test
        void a_chain_of_same_faction_systems_fuses_transitively() {
            // A-B and B-C border pairs, all held by F: A and C never touch directly but
            // fuse through B into one cluster.
            var edges = Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A"), edgeTo("C")),
                    "C", List.of(edgeTo("B")));
            var owners = Map.of("A", FACTION_F, "B", FACTION_F, "C", FACTION_F);

            var clusters = SystemClusters.findClusters(edges, owners);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactlyInAnyOrder("A", "B", "C");
        }

        @Test
        void same_faction_systems_with_no_shared_border_stay_separate() {
            // Two F systems that face only empty space (a disjoint pocket each) get their
            // own cluster - one label each, not a name stranded between them.
            var edges = Map.of(
                    "A", List.of(edgeTo(null)),
                    "B", List.of(edgeTo(null)));
            var owners = Map.of("A", FACTION_F, "B", FACTION_F);

            var clusters = SystemClusters.findClusters(edges, owners);

            assertThat(clusters).hasSize(2);
        }

        @Test
        void adjacent_systems_of_different_factions_do_not_fuse() {
            // A and B share a border but belong to F and G, so the seam is a national
            // boundary, not a fusing interior seam: two clusters.
            var edges = Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A")));
            var owners = Map.of("A", FACTION_F, "B", FACTION_G);

            var clusters = SystemClusters.findClusters(edges, owners);

            assertThat(clusters).hasSize(2);
            assertThat(clusters).allSatisfy(cluster -> assertThat(cluster).hasSize(1));
        }

        @Test
        void an_unowned_system_carries_no_cluster() {
            // B has a cell but no owner, so it never seeds a cluster; only owned A does,
            // and the seam into unowned B does not fuse.
            var edges = Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A")));
            var owners = Map.of("A", FACTION_F);

            var clusters = SystemClusters.findClusters(edges, owners);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).containsExactly("A");
        }

        @Test
        void nothing_owned_yields_no_clusters() {
            var edges = Map.of("A", List.of(edgeTo(null)));

            var clusters = SystemClusters.findClusters(edges, Map.of());

            assertThat(clusters).isEmpty();
        }
    }
}
