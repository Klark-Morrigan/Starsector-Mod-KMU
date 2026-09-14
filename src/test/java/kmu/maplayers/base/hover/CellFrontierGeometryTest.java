package kmu.maplayers.base.hover;

import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquare;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquareRun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the rule every highlight settles a cell by: which of a group's traced loops is the cluster
 * the cell belongs to, and what that cell covers once clamped to it.
 *
 * <p>Both are read off the polygons and runs handed in, so no shaping, styling or GL is involved
 * in the answers - hand-built squares are the whole fixture.
 */
final class CellFrontierGeometryTest {

    @Nested
    class FindEnclosingLoop {

        @Test
        void findEnclosingLoopAnswersTheLoopThatEnclosesTheCell() {

            var loop = buildSquareRun(0, 0, 100);

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(loop),
                    buildSquare(10, 10, 80)))
                .isSameAs(loop);
        }

        @Test
        void findEnclosingLoopPassesOverALoopTheCellIsOutside() {
            // One group, two disjoint clusters - the whole reason the loop is searched for rather
            // than taken as "the cluster's border".
            var hoveredLoop = buildSquareRun(0, 0, 100);
            var distantLoop = buildSquareRun(500, 0, 100);

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(distantLoop, hoveredLoop),
                    buildSquare(10, 10, 80)))
                .isSameAs(hoveredLoop);
        }

        @Test
        void findEnclosingLoopAnswersTheInnermostOfNestedLoops() {
            // A group's enclave, walled inside a rival that is itself walled inside another cluster
            // of that same group: three of its loops enclose the cell, and only the tightest is the
            // cluster the cell actually belongs to.
            var outerCluster = buildSquareRun(0, 0, 1000);
            var enclaveInsideRival = buildSquareRun(400, 400, 100);

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(outerCluster, enclaveInsideRival),
                    buildSquare(410, 410, 80)))
                .isSameAs(enclaveInsideRival);
        }

        @Test
        void findEnclosingLoopAnswersTheInnermostWhicheverOrderTheLoopsArriveIn() {
            // The same nesting with the tight loop met first: a group hands its loops over in
            // whatever order it traced them, so the rule has to be "the smallest that encloses"
            // rather than "the last one found to".
            var outerCluster = buildSquareRun(0, 0, 1000);
            var enclaveInsideRival = buildSquareRun(400, 400, 100);

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(enclaveInsideRival, outerCluster),
                    buildSquare(410, 410, 80)))
                .isSameAs(enclaveInsideRival);
        }

        @Test
        void findEnclosingLoopAnswersNothingForACellNoLoopEncloses() {
            // Candidates exist but the cell sits outside every one of them, which reads the same as
            // a cell that fused into no cluster at all: it belongs to no frontier.
            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(buildSquareRun(500, 500, 100)),
                    buildSquare(10, 10, 80)))
                .isNull();
        }

        @Test
        void findEnclosingLoopAnswersNothingWhenTheGroupTracedNoBorder() {

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(),
                    buildSquare(10, 10, 80)))
                .isNull();
        }
    }

    @Nested
    class ClipCellsToFrontier {

        @Test
        void clipCellsToFrontierJoinsAbuttingCellsIntoOneBoundary() {
            // Two cells of one cluster lit together: the edge they share is interior to the region
            // they cover, so it is dropped rather than left as a seam inside a lit shape.
            var loops = CellFrontierGeometry.clipCellsToFrontier(
                List.of(buildSquare(10, 10, 40), buildSquare(50, 10, 40)),
                buildSquareRun(0, 0, 200));

            assertThat(loops)
                .hasSize(1);
            assertThat(computeArea(loops.get(0)))
                .isCloseTo(3200.0, within(1e-2));
        }

        @Test
        void clipCellsToFrontierClampsACellPokingPastTheFrontier() {
            // The shaped cell reaches past the frontier that encloses its centre - the corner the
            // border's rounding cut, which the raw cell keeps. Only the overlap
            // [50,100]x[50,100] survives, so nothing washes past the line the border strokes.
            var loops = CellFrontierGeometry.clipCellsToFrontier(
                List.of(buildSquare(50, 50, 80)),
                buildSquareRun(0, 0, 100));

            assertThat(loops)
                .hasSize(1);
            assertThat(computeArea(loops.get(0)))
                .isCloseTo(2500.0, within(1e-2));
        }

        @Test
        void clipCellsToFrontierAnswersTheCellsOwnBoundaryWithoutAFrontier() {
            // A cell no loop encloses has nothing to be clamped to, so it covers itself whole.
            var loops = CellFrontierGeometry.clipCellsToFrontier(
                List.of(buildSquare(10, 10, 40)),
                null);

            assertThat(loops)
                .hasSize(1);
            assertThat(computeArea(loops.get(0)))
                .isCloseTo(1600.0, within(1e-2));
        }
    }

    // A boundary loop's unsigned area - what it covers, whichever way the tessellator wound it.
    private static double computeArea(List<double[]> loop) {
        return Math.abs(PolygonRegions.computeSignedArea(loop));
    }
}
