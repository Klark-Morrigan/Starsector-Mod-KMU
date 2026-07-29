package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the contract of {@link SystemClusterIndex}:
 *  - any member of a cluster resolves to that whole cluster, itself included,
 *  - a system in one of a faction's disjoint pockets resolves to that pocket alone,
 *  - a system in no cluster - and no system at all - resolves to nothing.
 *
 * <p>The index reads only the member ids of the clusters handed to it, never the graph they
 * were found in, so the fixtures here are the cluster shapes {@link SystemClusters} reports
 * for each scenario rather than the edges and keys that produced them.
 */
final class SystemClusterIndexTest {

    @Nested
    class IndexClusters {
        @Test
        void an_indexed_cluster_cannot_be_changed_through_the_list_it_was_built_from() {
            // The index is built once per rebuild and read every frame, so a caller reusing
            // its list must not be able to grow a hovered cluster underneath the highlight.
            var cluster = new ArrayList<>(List.of("A"));
            var clusters = List.<List<String>>of(cluster);
            var index = SystemClusterIndex.indexClusters(clusters);

            cluster.add("B");

            assertThat(index.findClusterMembersOf("A")).containsExactly("A");
            assertThat(index.findClusterMembersOf("B")).isEmpty();
        }

        @Test
        void a_returned_cluster_cannot_be_changed_by_its_reader() {
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A", "B")));

            assertThatThrownBy(() -> index.findClusterMembersOf("A").add("C"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class FindClusterMembersOf {
        @Test
        void a_member_of_a_two_system_cluster_resolves_to_both_members() {
            // Two same-key neighbours fuse into one cluster, so hovering either lights the
            // pair - the whole contiguous territory, not the one cell under the cursor.
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A", "B")));

            assertThat(index.findClusterMembersOf("A")).containsExactly("A", "B");
            assertThat(index.findClusterMembersOf("B")).containsExactly("A", "B");
        }

        @Test
        void a_member_of_a_disjoint_pocket_resolves_to_that_pocket_alone() {
            // One faction, two unconnected pockets: separate clusters, so hovering the
            // colony must not light the homeland across the sector.
            var index = SystemClusterIndex.indexClusters(
                    List.of(List.of("HOME_A", "HOME_B"), List.of("COLONY")));

            assertThat(index.findClusterMembersOf("COLONY")).containsExactly("COLONY");
        }

        @Test
        void a_lone_system_resolves_to_itself() {
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A")));

            assertThat(index.findClusterMembersOf("A")).containsExactly("A");
        }

        @Test
        void a_system_in_no_cluster_resolves_to_nothing() {
            // A differently-keyed neighbour is a cluster of its own and never a member of
            // this one; an ungrouped or cell-less system carries no cluster at all, and
            // neither is an error - the hover simply has nothing to highlight.
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A")));

            assertThat(index.findClusterMembersOf("UNGROUPED")).isEmpty();
        }

        @Test
        void nothing_hovered_resolves_to_nothing() {
            // The hit test returns null off any cell, so that null flows straight in here.
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A")));

            assertThat(index.findClusterMembersOf(null)).isEmpty();
        }

        @Test
        void no_clusters_resolve_to_nothing() {
            var index = SystemClusterIndex.indexClusters(List.of());

            assertThat(index.findClusterMembersOf("A")).isEmpty();
        }
    }
}
