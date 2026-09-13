package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeTo;
import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeToCell;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins {@link EdgeClassifier}: the core rule that a same-owner pairing is an interior
 * seam, a key on exactly one side is an open frontier, and two differing keys or two
 * unowned sides are a plain boundary - and the {@link EdgeClassifier#classifyAcross}
 * convenience that resolves what an edge's {@link EdgeTarget} names before applying that
 * rule: a system's key read from the map, the reach bound a plain boundary, and
 * same-owner an interior seam whatever the cell's own key.
 */
final class EdgeClassifierTest {

    // One cell edge facing the given systemless target - the reach bound or same-owner.
    private static CellEdge buildEdgeFacing(EdgeTarget target) {

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
        void classifyReturnsOpenFrontierWhenTheNeighbourIsUnowned() {
            // A owned cell facing unowned space is the
            // open frontier the owned cell can reach toward, not a plain boundary.
            assertThat(EdgeClassifier.classify("hegemony", null))
                .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyReturnsOpenFrontierWhenThisSideIsUnowned() {
            // Symmetric: the unowned cell facing a grouped neighbour sees the same
            // frontier from its side, so it too can pull its edge toward the star.
            assertThat(EdgeClassifier.classify(null, "hegemony"))
                .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyReturnsBoundaryWhenBothSidesAreUnowned() {
            // Two unowned cells do not fuse into a fused interior; an
            // unowned-to-unowned edge stays a boundary.
            assertThat(EdgeClassifier.classify(null, null))
                .isEqualTo(EdgeClass.BOUNDARY);
        }
    }

    @Nested
    class ClassifyAcross {

        @Test
        void classifyAcrossReturnsInteriorSeamWhenTheNeighbourSharesTheKey() {

            var edge = buildEdgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", buildKeyedValues(Map.of("B", "F"))))
                .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyAcrossReadsTheNeighbourOfTwoSystemsSharingAnIdUnderItsOwnKey() {
            // The collision the one shared address survives: both systems answer to "B", and the
            // edge names the one held by a rival - so the edge is a boundary, where a lookup by
            // ID alone would have found the other's owner and fused the two cells.
            var neighbour = new SystemKey("B", "", "8b3");
            var twin = new SystemKey("B", "", "38d53");
            var edge = buildEdgeToCell(neighbour);

            assertThat(EdgeClassifier.classifyAcross(
                    edge,
                    "F",
                    Map.of(neighbour, "G", twin, "F")))
                .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsBoundaryWhenTheNeighbourHasADifferentKey() {

            var edge = buildEdgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", buildKeyedValues(Map.of("B", "G"))))
                .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsOpenFrontierWhenTheNeighbourHasACellButNoKey() {
            // B has a cell (a system across the edge) but no entry in the grouping-key map, so
            // the owned cell faces an unowned star it can reach toward - an open frontier.
            var edge = buildEdgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, "F", Map.of()))
                .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyAcrossReturnsBoundaryBetweenTwoUnownedCells() {
            // Two unowned cells (own null, neighbour has a cell but no key) do not form
            // a frontier - there is no key reaching toward the star - so the edge stays a
            // plain boundary and the shaper leaves it at the normal inset.
            var edge = buildEdgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, null, Map.of()))
                .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyAcrossReturnsOpenFrontierFromAnUnownedCellFacingAGroupedOne() {
            // The empty/deciv cell's own side: an unowned cell (cellOwner null) facing
            // a grouped neighbour sees the same frontier, so the shaper can pull its edge in
            // toward the star. Pins the null-own direction through classifyAcross itself.
            var edge = buildEdgeTo("B");

            assertThat(EdgeClassifier.classifyAcross(edge, null, buildKeyedValues(Map.of("B", "F"))))
                .isEqualTo(EdgeClass.OPEN_FRONTIER);
        }

        @Test
        void classifyAcrossTreatsTheReachBoundAsABoundaryWithoutAKeyLookup() {
            // The reach bound has no star across it, so it stays a plain boundary - not an
            // open frontier - and must not probe the grouping-key map for a system it does not name,
            // which an immutable Map.of would reject on a null key.
            var boundEdge = buildEdgeFacing(EdgeTarget.REACH_BOUND);

            assertThatCode(() -> assertThat(
                        EdgeClassifier.classifyAcross(boundEdge, "F", buildKeyedValues(Map.of("B", "F"))))
                    .isEqualTo(EdgeClass.BOUNDARY))
                .doesNotThrowAnyException();
        }

        @Test
        void classifyAcrossReturnsInteriorSeamForTheSameCellUnderAKey() {
            // A cut interior to one key's absorbed cluster: the far side is that key's own
            // cell, so it fuses whatever key sits either side, with no system to look up.
            var edge = buildEdgeFacing(EdgeTarget.SAME_OWNER);

            assertThat(EdgeClassifier.classifyAcross(edge, "F", buildKeyedValues(Map.of("B", "F"))))
                .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyAcrossReturnsInteriorSeamForTheSameCellWhenUnowned() {
            // Same-owner fuses even an unowned cell's own cut - a null-keyed shard of one
            // dead star's leftover space - which "the same owner both sides" could not express for a
            // null key, and which must not read a null system out of the map.
            var edge = buildEdgeFacing(EdgeTarget.SAME_OWNER);

            assertThatCode(() -> assertThat(
                        EdgeClassifier.classifyAcross(edge, null, buildKeyedValues(Map.of("B", "F"))))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM))
                .doesNotThrowAnyException();
        }
    }
}
