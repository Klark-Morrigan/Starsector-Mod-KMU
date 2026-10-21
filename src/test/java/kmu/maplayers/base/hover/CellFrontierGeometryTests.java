package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquare;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquareRun;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule every highlight settles a cell by: which of a group's traced loops is the cluster
 * the cell belongs to.
 *
 * <p>The answer is read off the polygons and runs handed in, so no shaping, styling or GL is
 * involved - hand-built squares are the whole fixture.
 */
final class CellFrontierGeometryTests {

    @Nested
    class FindEnclosingLoop {

        @Test
        void answersTheLoopThatEnclosesTheCell() {

            var loop = buildSquareRun(0, 0, 100);

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(loop),
                    buildSquare(10, 10, 80)))
                .isSameAs(loop);
        }

        @Test
        void passesOverALoopTheCellIsOutside() {
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
        void answersTheInnermostOfNestedLoops() {
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
        void answersTheInnermostWhicheverOrderTheLoopsArriveIn() {
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
        void answersNothingForACellNoLoopEncloses() {
            // Candidates exist but the cell sits outside every one of them, which reads the same as
            // a cell that fused into no cluster at all: it belongs to no frontier.
            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(buildSquareRun(500, 500, 100)),
                    buildSquare(10, 10, 80)))
                .isNull();
        }

        @Test
        void answersNothingWhenTheGroupTracedNoBorder() {

            assertThat(CellFrontierGeometry.findEnclosingLoop(
                    List.of(),
                    buildSquare(10, 10, 80)))
                .isNull();
        }
    }
}
