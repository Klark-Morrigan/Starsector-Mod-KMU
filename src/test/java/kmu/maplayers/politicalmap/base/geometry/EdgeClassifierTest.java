package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins {@link EdgeClassifier}: the core rule that a same-key pairing is an interior
 * seam, a key on exactly one side is an open frontier, and two differing keys or two
 * ungrouped sides are a plain boundary - and the {@link EdgeClassifier#classifyAcross}
 * convenience that reads the neighbour's grouping key from the key map before applying
 * that rule, treating the map-reach bound (a null neighbour) as a plain boundary.
 */
final class EdgeClassifierTest {

    // One cell edge bordering the given neighbour (null for a frontier); geometry is
    // irrelevant to classification, so the segment is left at the origin.
    private static CellEdge edgeTo(String neighbour) {
        return new CellEdge(0, 0, 0, 0, neighbour);
    }

    @Nested
    class Classify {

        @Test
        void classifyReturnsInteriorSeamWhenBothSidesShareAnOwner() {
            assertThat(EdgeClassifier.classify("hegemony", "hegemony"))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyReturnsBoundaryForDifferentOwners() {
            assertThat(EdgeClassifier.classify("hegemony", "tritachyon"))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyReturnsOpenFrontierWhenTheNeighbourIsUnowned() {
            // An owned cell facing unowned space (a dead or decivilised star) is the
            // open frontier the faction can reach toward, not a plain boundary.
            assertThat(EdgeClassifier.classify("hegemony", null))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyReturnsOpenFrontierWhenThisSideIsUnowned() {
            // Symmetric: the unowned cell facing an owned neighbour sees the same
            // frontier from its side, so it too can pull its edge toward the star.
            assertThat(EdgeClassifier.classify(null, "hegemony"))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyReturnsBoundaryWhenBothSidesAreUnowned() {
            // Two unowned cells do not fuse into a faction interior; an
            // unowned-to-unowned edge stays a boundary.
            assertThat(EdgeClassifier.classify(null, null)).isEqualTo(EdgeClass.BOUNDARY);
        }
    }

    @Nested
    class ClassifyAcross {

        @Test
        void classifyAcrossReturnsInteriorSeamWhenTheNeighbourSharesTheOwner() {
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", "F")))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyAcrossReturnsBoundaryWhenTheNeighbourHasADifferentOwner() {
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", "G")))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsOpenFrontierWhenTheNeighbourHasACellButNoOwner() {
            // B has a cell (a system across the edge) but no entry in the owner map, so
            // the owned cell faces an unowned star it can reach toward - an open frontier.
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of()))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyAcrossReturnsBoundaryBetweenTwoFactionlessCells() {
            // Two ungrouped cells (own null, neighbour has a cell but no owner) do not form
            // a frontier - there is no owner reaching toward the star - so the edge stays a
            // plain boundary and the shaper leaves it at the normal inset.
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, null, Map.of()))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsOpenFrontierFromAFactionlessCellFacingAnOwner() {
            // The empty/deciv cell's own side: an ungrouped cell (ownGroupKey null) facing
            // an owned neighbour sees the same frontier, so the shaper can pull its edge in
            // toward the star. Pins the null-own direction through classifyAcross itself.
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, null, Map.of("B", "F")))
                    .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyAcrossTreatsTheMapReachBoundAsABoundaryWithoutANullKeyLookup() {
            // The map-reach bound (null neighbour) has no star across it, so it stays a
            // plain boundary - not an open frontier - and must not probe the owner map for
            // a null key, which an immutable Map.of rejects.
            var boundEdge = edgeTo(null);

            assertThatCode(() -> assertThat(
                    EdgeClassifier.classifyAcross(boundEdge, "F", Map.of("B", "F")))
                    .isEqualTo(EdgeClass.BOUNDARY))
                    .doesNotThrowAnyException();
        }
    }
}
