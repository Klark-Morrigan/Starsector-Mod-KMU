package kmu.politicalmap.domain.geometry;

import kmu.politicalmap.domain.politics.DominantOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins {@link EdgeClassifier}: the core rule that only a same-faction pairing is
 * an interior seam, while differing owners, an unowned side, or a frontier into
 * empty space are boundaries - and the {@link EdgeClassifier#classifyAcross}
 * convenience that resolves the neighbour's faction from the owner map before
 * applying that rule.
 */
final class EdgeClassifierTest {

    private static final DominantOwner FACTION_F =
            new DominantOwner("F", Color.RED, Color.RED.darker());
    private static final DominantOwner FACTION_G =
            new DominantOwner("G", Color.BLUE, Color.BLUE.darker());

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
        void classifyReturnsBoundaryWhenTheNeighbourIsUnowned() {
            // A null neighbour faction is unowned space or a frontier - no owner
            // to match, so the edge is a boundary.
            assertThat(EdgeClassifier.classify("hegemony", null)).isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyReturnsBoundaryWhenThisSideIsUnowned() {
            assertThat(EdgeClassifier.classify(null, "hegemony")).isEqualTo(EdgeClass.BOUNDARY);
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

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", FACTION_F)))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyAcrossReturnsBoundaryWhenTheNeighbourHasADifferentOwner() {
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of("B", FACTION_G)))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsBoundaryWhenTheNeighbourIsUnowned() {
            // B has a cell but no entry in the owner map, so the edge faces unowned
            // space - a boundary.
            var edge = edgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of()))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossTreatsAFrontierEdgeAsABoundaryWithoutANullKeyLookup() {
            // A frontier edge (null neighbour) must be classed a boundary without
            // probing the owner map for a null key, which an immutable Map.of rejects.
            var frontierEdge = edgeTo(null);

            assertThatCode(() -> assertThat(
                    EdgeClassifier.classifyAcross(frontierEdge, "F", Map.of("B", FACTION_F)))
                    .isEqualTo(EdgeClass.BOUNDARY))
                    .doesNotThrowAnyException();
        }
    }
}
