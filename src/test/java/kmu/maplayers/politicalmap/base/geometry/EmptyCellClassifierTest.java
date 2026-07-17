package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link EmptyCellClassifier}: only an unowned cell that touches somebody's territory
 * counts as frontier-empty, so a frontier pass reaches into the dead stars against a border
 * and leaves distant void alone.
 */
final class EmptyCellClassifierTest {

    @Nested
    class IsFrontierEmpty {

        @Test
        void isFrontierEmptyReturnsTrueForAnUnownedCellTouchingAnOwnedNeighbour() {
            // The dead star "empty" borders hegemony-held "a": its space is reachable.
            var isFrontierEmpty = EmptyCellClassifier.isFrontierEmpty(
                    null, cellNeighbouring("a", "b"), Map.of("a", "hegemony"));

            assertThat(isFrontierEmpty).isTrue();
        }

        @Test
        void isFrontierEmptyReturnsFalseForAnUnownedCellSurroundedByEmptyCells() {
            // Void interior: neither neighbour is held, so nobody borders this star.
            var isFrontierEmpty = EmptyCellClassifier.isFrontierEmpty(
                    null, cellNeighbouring("a", "b"), Map.of());

            assertThat(isFrontierEmpty).isFalse();
        }

        @Test
        void isFrontierEmptyReturnsFalseForAnOwnedCellNextToAnEmptyOne() {
            // The owned side of a frontier edge is not itself redistributed.
            var isFrontierEmpty = EmptyCellClassifier.isFrontierEmpty(
                    "hegemony", cellNeighbouring("a", "b"), Map.of("a", "hegemony"));

            assertThat(isFrontierEmpty).isFalse();
        }

        @Test
        void isFrontierEmptyReturnsFalseForAnUnownedCellTouchingOnlyTheMapReachBound() {
            // A bound edge has no star across it, so it can never make a cell frontier-empty.
            var isFrontierEmpty = EmptyCellClassifier.isFrontierEmpty(
                    null, cellNeighbouring(null, null), Map.of("a", "hegemony"));

            assertThat(isFrontierEmpty).isFalse();
        }

        @Test
        void isFrontierEmptyReturnsTrueWhenOnlyOneOfSeveralNeighboursIsOwned() {
            // Mixed surroundings: one owned neighbour among empty ones is enough.
            var isFrontierEmpty = EmptyCellClassifier.isFrontierEmpty(
                    null, cellNeighbouring("a", "b", null), Map.of("b", "tritachyon"));

            assertThat(isFrontierEmpty).isTrue();
        }
    }

    @Nested
    class CollectFrontierEmptyCellIds {

        @Test
        void collectFrontierEmptyCellIdsKeepsOnlyTheUnownedCellsAgainstTerritory() {
            // "near" touches owned "a"; "far" touches only "near"; "a" is owned. One id out.
            var cells = Map.of(
                    "a", cellNeighbouring("near"),
                    "near", cellNeighbouring("a", "far"),
                    "far", cellNeighbouring("near"));

            var frontierEmptyCellIds = EmptyCellClassifier.collectFrontierEmptyCellIds(
                    cells, grouping(cells, Map.of("a", "hegemony")));

            assertThat(frontierEmptyCellIds).containsExactly("near");
        }

        @Test
        void collectFrontierEmptyCellIdsReturnsEveryEmptyCellBorderingRivalOwners() {
            // A dead star between two rivals is frontier-empty once, not once per owner.
            var cells = Map.of(
                    "a", cellNeighbouring("empty"),
                    "b", cellNeighbouring("empty"),
                    "empty", cellNeighbouring("a", "b"));

            var frontierEmptyCellIds = EmptyCellClassifier.collectFrontierEmptyCellIds(
                    cells, grouping(cells, Map.of("a", "hegemony", "b", "tritachyon")));

            assertThat(frontierEmptyCellIds).containsExactly("empty");
        }

        @Test
        void collectFrontierEmptyCellIdsIsEmptyWhenNoSystemIsOwned() {
            // An unclaimed sector redistributes nothing: every cell draws as today.
            var cells = Map.of(
                    "a", cellNeighbouring("b"),
                    "b", cellNeighbouring("a"));

            var frontierEmptyCellIds = EmptyCellClassifier.collectFrontierEmptyCellIds(
                    cells, grouping(cells, Map.of()));

            assertThat(frontierEmptyCellIds).isEmpty();
        }
    }

    /**
     * A cell whose edges face the given neighbours in turn, one edge each; a null neighbour is
     * the reach bound. Only the adjacency tags matter here, so the outline is a unit square
     * walked in order and the coordinates carry no meaning beyond keeping the edges distinct.
     */
    private static List<CellEdge> cellNeighbouring(String... neighbourSystemIds) {
        var edges = new ArrayList<CellEdge>();
        for (var i = 0; i < neighbourSystemIds.length; i++) {
            var target = neighbourSystemIds[i] == null
                    ? EdgeTarget.REACH_BOUND
                    : new EdgeTarget.AcrossSystem(neighbourSystemIds[i]);
            edges.add(new CellEdge(i, 0, i + 1, 0, target));
        }
        return edges;
    }

    // The grouping to classify under: each cell drawing as its own star (identity draws-as over
    // the cell set), keyed by the given owners.
    private static CellGrouping grouping(
            Map<String, List<CellEdge>> cells, Map<String, String> owners) {
        var systemIdByCellId = new java.util.LinkedHashMap<String, String>();
        for (var cellId : cells.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        return new CellGrouping(systemIdByCellId, owners);
    }
}
