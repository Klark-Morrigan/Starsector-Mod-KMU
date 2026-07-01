package kmu.politicalmap.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link EdgeClassifier}: the core rule that only a same-faction pairing is
 * an interior seam, and the bulk pass that applies it across a system's edges
 * against the dominant owners (translating each edge's neighbour to its owner,
 * and treating a frontier or an unowned side as a boundary).
 */
final class EdgeClassifierTest {

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
    class ClassifyEdges {

        @Test
        void classifyEdgesTagsASharedBorderBetweenSameFactionSystemsAsInterior() {
            var owners = Map.of(
                    "a", new DominantOwner("hegemony", Color.RED),
                    "b", new DominantOwner("hegemony", Color.RED));
            var edges = Map.of(
                    "a", List.of(new CellEdge(0, 0, 0, 10, "b")),
                    "b", List.of(new CellEdge(0, 10, 0, 0, "a")));

            var classified = EdgeClassifier.classifyEdges(edges, owners);

            // Both sides of the shared border are Hegemony, so each system's copy
            // of the edge classifies as an interior seam.
            assertThat(classified).extracting(ClassifiedEdge::edgeClass)
                    .containsExactlyInAnyOrder(EdgeClass.INTERIOR_SEAM, EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyEdgesTagsABorderBetweenDifferentFactionsAsBoundary() {
            var owners = Map.of(
                    "a", new DominantOwner("hegemony", Color.RED),
                    "b", new DominantOwner("tritachyon", Color.CYAN));
            var edges = Map.of("a", List.of(new CellEdge(0, 0, 0, 10, "b")));

            var classified = EdgeClassifier.classifyEdges(edges, owners);

            assertThat(classified).extracting(ClassifiedEdge::edgeClass)
                    .containsExactly(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyEdgesTagsAFrontierEdgeAsBoundary() {
            var owners = Map.of("a", new DominantOwner("hegemony", Color.RED));
            // A null neighbour id is the cell's max-radius bound: a frontier into
            // empty space, classified as a boundary.
            var edges = Map.of("a", List.of(new CellEdge(0, 0, 0, 10, null)));

            var classified = EdgeClassifier.classifyEdges(edges, owners);

            assertThat(classified).extracting(ClassifiedEdge::edgeClass)
                    .containsExactly(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyEdgesTagsABorderAgainstAnUnownedNeighbourAsBoundary() {
            // System "b" is absent from the owner map (uninhabited), so the border
            // against it is a boundary, not an interior seam.
            var owners = Map.of("a", new DominantOwner("hegemony", Color.RED));
            var edges = Map.of("a", List.of(new CellEdge(0, 0, 0, 10, "b")));

            var classified = EdgeClassifier.classifyEdges(edges, owners);

            assertThat(classified).extracting(ClassifiedEdge::edgeClass)
                    .containsExactly(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyEdgesPreservesEachEdgeSegment() {
            var owners = Map.of("a", new DominantOwner("hegemony", Color.RED));
            var edges = Map.of("a", List.of(new CellEdge(1, 2, 3, 4, null)));

            var classified = EdgeClassifier.classifyEdges(edges, owners);

            // The classifier only tags edges; the segment coordinates pass through
            // untouched for the renderer to draw.
            assertThat(classified).singleElement().satisfies(edge -> {
                assertThat(edge.x1()).isEqualTo(1);
                assertThat(edge.y1()).isEqualTo(2);
                assertThat(edge.x2()).isEqualTo(3);
                assertThat(edge.y2()).isEqualTo(4);
            });
        }
    }
}
