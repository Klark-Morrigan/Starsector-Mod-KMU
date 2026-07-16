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
    class CollectFrontierEmptySystemIds {

        @Test
        void collectFrontierEmptySystemIdsKeepsOnlyTheUnownedCellsAgainstTerritory() {
            // "near" touches owned "a"; "far" touches only "near"; "a" is owned. One id out.
            var cells = Map.of(
                    "a", cellNeighbouring("near"),
                    "near", cellNeighbouring("a", "far"),
                    "far", cellNeighbouring("near"));

            var frontierEmptySystemIds = EmptyCellClassifier.collectFrontierEmptySystemIds(
                    cells, Map.of("a", "hegemony"));

            assertThat(frontierEmptySystemIds).containsExactly("near");
        }

        @Test
        void collectFrontierEmptySystemIdsReturnsEveryEmptyCellBorderingRivalOwners() {
            // A dead star between two rivals is frontier-empty once, not once per owner.
            var cells = Map.of(
                    "a", cellNeighbouring("empty"),
                    "b", cellNeighbouring("empty"),
                    "empty", cellNeighbouring("a", "b"));

            var frontierEmptySystemIds = EmptyCellClassifier.collectFrontierEmptySystemIds(
                    cells, Map.of("a", "hegemony", "b", "tritachyon"));

            assertThat(frontierEmptySystemIds).containsExactly("empty");
        }

        @Test
        void collectFrontierEmptySystemIdsIsEmptyWhenNoSystemIsOwned() {
            // An unclaimed sector redistributes nothing: every cell draws as today.
            var cells = Map.of(
                    "a", cellNeighbouring("b"),
                    "b", cellNeighbouring("a"));

            var frontierEmptySystemIds = EmptyCellClassifier.collectFrontierEmptySystemIds(
                    cells, Map.of());

            assertThat(frontierEmptySystemIds).isEmpty();
        }
    }

    /**
     * A cell whose edges face the given neighbours in turn, one edge each; a null neighbour is
     * the map-reach bound. Only the adjacency tags matter here, so the outline is a unit square
     * walked in order and the coordinates carry no meaning beyond keeping the edges distinct.
     */
    private static List<CellEdge> cellNeighbouring(String... neighbourSystemIds) {
        var edges = new ArrayList<CellEdge>();
        for (var i = 0; i < neighbourSystemIds.length; i++) {
            edges.add(new CellEdge(i, 0, i + 1, 0, neighbourSystemIds[i]));
        }
        return edges;
    }
}
