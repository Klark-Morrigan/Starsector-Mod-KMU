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
 *  - a system in one of a key's disjoint pockets resolves to that pocket alone,
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
        void anIndexedClusterCannotBeChangedThroughTheListItWasBuiltFrom() {
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
        void aReturnedClusterCannotBeChangedByItsReader() {
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A", "B")));

            assertThatThrownBy(() -> index.findClusterMembersOf("A").add("C"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class FindClusterMembersOf {
        @Test
        void aMemberOfATwoSystemClusterResolvesToBothMembers() {
            // Two same-owner neighbours fuse into one cluster, so hovering either lights the
            // pair - the whole contiguous cluster, not the one cell under the cursor.
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A", "B")));

            assertThat(index.findClusterMembersOf("A")).containsExactly("A", "B");
            assertThat(index.findClusterMembersOf("B")).containsExactly("A", "B");
        }

        @Test
        void aMemberOfADisjointPocketResolvesToThatPocketAlone() {
            // One key, two unconnected pockets: separate clusters, so hovering the
            // colony must not light the homeland across the sector.
            var index = SystemClusterIndex.indexClusters(
                    List.of(List.of("HOME_A", "HOME_B"), List.of("COLONY")));

            assertThat(index.findClusterMembersOf("COLONY")).containsExactly("COLONY");
        }

        @Test
        void aLoneSystemResolvesToItself() {
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A")));

            assertThat(index.findClusterMembersOf("A")).containsExactly("A");
        }

        @Test
        void aSystemInNoClusterResolvesToNothing() {
            // A differently-keyed neighbour is a cluster of its own and never a member of
            // this one; an unowned or cell-less system carries no cluster at all, and
            // neither is an error - the hover simply has nothing to highlight.
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A")));

            assertThat(index.findClusterMembersOf("UNGROUPED")).isEmpty();
        }

        @Test
        void nothingHoveredResolvesToNothing() {
            // The hit test returns null off any cell, so that null flows straight in here.
            var index = SystemClusterIndex.indexClusters(List.of(List.of("A")));

            assertThat(index.findClusterMembersOf(null)).isEmpty();
        }

        @Test
        void noClustersResolveToNothing() {
            var index = SystemClusterIndex.indexClusters(List.of());

            assertThat(index.findClusterMembersOf("A")).isEmpty();
        }
    }
}
