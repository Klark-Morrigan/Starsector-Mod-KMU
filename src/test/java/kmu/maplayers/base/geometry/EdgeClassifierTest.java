package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins {@link EdgeClassifier}: the core rule that a same-key pairing is an interior
 * seam, a key on exactly one side is an open frontier, and two differing keys or two
 * ungrouped sides are a plain boundary - and the {@link EdgeClassifier#classifyAcross}
 * convenience that resolves what an edge's {@link EdgeTarget} names before applying that
 * rule: a system's key read from the map, the reach bound a plain boundary, and
 * same-territory an interior seam whatever the cell's own key.
 */
final class EdgeClassifierTest {

    // One cell edge facing another system's cell; geometry is irrelevant to
    // classification, so the segment is left at the origin.
    private static CellEdge edgeAcrossSystem(String systemId) {
        return new CellEdge(0, 0, 0, 0, new EdgeTarget.AcrossSystem(systemId));
    }

    // One cell edge facing the given systemless target - the reach bound or same-territory.
    private static CellEdge edgeFacing(EdgeTarget target) {
        return new CellEdge(0, 0, 0, 0, target);
    }

    @Nested
    class Classify {

        @Test
        void classifyReturnsInteriorSeamWhenBothSidesShareAKey() {
            assertThat(EdgeClassifier.classify("hegemony", "hegemony"))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyReturnsBoundaryForDifferentKeys() {
            assertThat(EdgeClassifier.classify("hegemony", "tritachyon"))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyReturnsOpenFrontierWhenTheNeighbourIsUngrouped() {
            // A grouped cell facing ungrouped space is the
            // open frontier the grouped cell can reach toward, not a plain boundary.
            assertThat(EdgeClassifier.classify("hegemony", null))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyReturnsOpenFrontierWhenThisSideIsUngrouped() {
            // Symmetric: the ungrouped cell facing a grouped neighbour sees the same
            // frontier from its side, so it too can pull its edge toward the star.
            assertThat(EdgeClassifier.classify(null, "hegemony"))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyReturnsBoundaryWhenBothSidesAreUngrouped() {
            // Two ungrouped cells do not fuse into a fused interior; an
            // ungrouped-to-ungrouped edge stays a boundary.
            assertThat(EdgeClassifier.classify(null, null)).isEqualTo(EdgeClass.BOUNDARY);
        }
    }

    @Nested
    class ClassifyAcross {

        @Test
        void classifyAcrossReturnsInteriorSeamWhenTheNeighbourSharesTheKey() {
            var edge = edgeAcrossSystem("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", "F")))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyAcrossReturnsBoundaryWhenTheNeighbourHasADifferentKey() {
            var edge = edgeAcrossSystem("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", "G")))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsOpenFrontierWhenTheNeighbourHasACellButNoKey() {
            // B has a cell (a system across the edge) but no entry in the grouping-key map, so
            // the grouped cell faces an ungrouped star it can reach toward - an open frontier.
            var edge = edgeAcrossSystem("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of()))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyAcrossReturnsBoundaryBetweenTwoUngroupedCells() {
            // Two ungrouped cells (own null, neighbour has a cell but no key) do not form
            // a frontier - there is no key reaching toward the star - so the edge stays a
            // plain boundary and the shaper leaves it at the normal inset.
            var edge = edgeAcrossSystem("B");

            assertThat(EdgeClassifier.classifyAcross(edge, null, Map.of()))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsOpenFrontierFromAnUngroupedCellFacingAGroupedOne() {
            // The empty/deciv cell's own side: an ungrouped cell (cellOwner null) facing
            // a grouped neighbour sees the same frontier, so the shaper can pull its edge in
            // toward the star. Pins the null-own direction through classifyAcross itself.
            var edge = edgeAcrossSystem("B");

            assertThat(EdgeClassifier.classifyAcross(edge, null, Map.of("B", "F")))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyAcrossTreatsTheReachBoundAsABoundaryWithoutAKeyLookup() {
            // The reach bound has no star across it, so it stays a plain boundary - not an
            // open frontier - and must not probe the grouping-key map for a system it does not name,
            // which an immutable Map.of would reject on a null key.
            var boundEdge = edgeFacing(EdgeTarget.REACH_BOUND);

            assertThatCode(() -> assertThat(
                    EdgeClassifier.classifyAcross(boundEdge, "F", Map.of("B", "F")))
                    .isEqualTo(EdgeClass.BOUNDARY))
                    .doesNotThrowAnyException();
        }

        @Test
        void classifyAcrossReturnsInteriorSeamForSameGroundUnderAKey() {
            // A cut interior to one key's absorbed region: the far side is that key's own
            // ground, so it fuses whatever key sits either side, with no system to look up.
            var edge = edgeFacing(EdgeTarget.SAME_OWNER);

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", "F")))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyAcrossReturnsInteriorSeamForSameGroundWhenUngrouped() {
            // Same-territory fuses even an ungrouped cell's own cut - a null-keyed shard of one
            // dead star's leftover space - which "same key both sides" could not express for a
            // null key, and which must not read a null system out of the map.
            var edge = edgeFacing(EdgeTarget.SAME_OWNER);

            assertThatCode(() -> assertThat(
                    EdgeClassifier.classifyAcross(edge, null, Map.of("B", "F")))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM))
                    .doesNotThrowAnyException();
        }
    }
}
