package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the frontage of a piece is, on one square.
 *
 * <p>The rule has no arithmetic in it, so what is pinned is the bookkeeping: every EDGE on a
 * cell is covered by exactly one run, a run carries both ends of every edge it covers and so
 * meets the next where one cell gives way to it, the frame is nobody's, a piece's holes are
 * shore as much as its outline is, and a piece a layer captured faces nothing.
 */
class LandableFrontageTests {

    // A square 100 across from the origin, wound counter-clockwise, one cell per side.
    private static final List<double[]> CORNERS = List.of(
        new double[] {0, 0},
        new double[] {100, 0},
        new double[] {100, 100},
        new double[] {0, 100});

    private static final int[] ONE_CELL_PER_SIDE = {0, 1, 2, 3};

    // The same square with a corner in the middle of each side, so each cell offers two.
    private static final List<double[]> CORNERS_AND_MIDDLES = List.of(
        new double[] {0, 0},
        new double[] {50, 0},
        new double[] {100, 0},
        new double[] {100, 50},
        new double[] {100, 100},
        new double[] {50, 100},
        new double[] {0, 100},
        new double[] {0, 50});

    private static final int[] TWO_CORNERS_PER_SIDE = {0, 0, 1, 1, 2, 2, 3, 3};

    // A smaller square inside the first, wound the other way, as a hole is.
    private static final List<double[]> HOLE = List.of(
        new double[] {25, 25},
        new double[] {25, 75},
        new double[] {75, 75},
        new double[] {75, 25});

    private static final int[] HOLE_ON_ONE_CELL = {7, 7, 7, 7};

    @Nested
    class CollectLandableRuns {

        @Test
        void everyEdgeOnACellIsFrontageOnThatCell() {
            // One edge per cell, and a run covering one edge is the two corners it joins.
            // Carrying only the edge's start would stop each run a whole edge short of the
            // junction it runs up to, which is a notch in the frontage at every corner.
            var runs = LandableFrontage.collectLandableRuns(
                Face.encloseFace(new LabelledRing(CORNERS, ONE_CELL_PER_SIDE)));

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(0, 1, 2, 3);

            assertThat(runs)
                .allSatisfy(run -> assertThat(run.points()).hasSize(2));
        }

        @Test
        void aRunEndsWhereTheNextBegins() {
            // The two share that corner, so the frontage reads as one unbroken line round the
            // piece rather than as four stretches with gaps between them.
            var runs = LandableFrontage.collectLandableRuns(
                Face.encloseFace(new LabelledRing(CORNERS, ONE_CELL_PER_SIDE)));

            for (var index = 0; index < runs.size(); index++) {

                var ends = runs.get(index).points().get(1);
                var begins = runs.get((index + 1) % runs.size()).points().get(0);

                assertThat(ends).containsExactly(begins);
            }
        }

        @Test
        void consecutiveCornersOnOneCellAreOneRun() {
            // Two corners per side come back together as one run rather than as two runs of
            // one, and the runs stand in walk order.
            var runs = LandableFrontage.collectLandableRuns(
                Face.encloseFace(new LabelledRing(CORNERS_AND_MIDDLES, TWO_CORNERS_PER_SIDE)));

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(0, 1, 2, 3);

            assertThat(runs)
                .allSatisfy(run -> assertThat(run.points()).hasSize(3));
        }

        @Test
        void aRunStraddlingTheRingsStartIsOneRun() {
            // The ring begins in the middle of cell 3's stretch. Walked from index 0 that
            // stretch would come back as two runs, one at each end of the list.
            var runs = LandableFrontage.collectLandableRuns(
                Face.encloseFace(new LabelledRing(CORNERS, new int[] {3, 1, 2, 3})));

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(1, 2, 3);

            assertThat(runs.get(2).points()).hasSize(3);
        }

        @Test
        void theFrameIsNobodysFrontage() {
            // A side on the edge of the sector is not a cell's border, so it joins no run and
            // starts none - and it breaks the run either side of it.
            var runs = LandableFrontage.collectLandableRuns(
                Face.encloseFace(new LabelledRing(
                    CORNERS, new int[] {0, VoidPartition.THE_FRAME, 2, 3})));

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(0, 2, 3);
        }

        @Test
        void aPiecesHolesAreItsShoreToo() {
            // The sea's shore is what it runs around. A hole's corners are frontage on the
            // cell the hole's edges lie on, after the outline's.
            var runs = LandableFrontage.collectLandableRuns(
                Face.encloseFace(new LabelledRing(CORNERS, ONE_CELL_PER_SIDE))
                    .cutOut(new LabelledRing(HOLE, HOLE_ON_ONE_CELL)));

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(0, 1, 2, 3, 7);

            assertThat(runs.get(4).points()).hasSize(5);
        }

        @Test
        void aCapturedPieceFacesNothing() {
            // The same square twice over, on different cells, and the first captured: its shore
            // faces water nothing can arrive in, so only the second's cells come back.
            var captured = Face.encloseFace(new LabelledRing(CORNERS, ONE_CELL_PER_SIDE));
            var open = Face.encloseFace(new LabelledRing(CORNERS, new int[] {4, 5, 6, 7}));

            var runs = LandableFrontage.collectLandableRuns(
                List.of(captured, open), piece -> piece == captured);

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(4, 5, 6, 7);
        }

        @Test
        void withNothingCapturedEveryPieceFacesItsShoreInTheOrderGiven() {

            var first = Face.encloseFace(new LabelledRing(CORNERS, ONE_CELL_PER_SIDE));
            var second = Face.encloseFace(new LabelledRing(CORNERS, new int[] {4, 5, 6, 7}));

            var runs = LandableFrontage.collectLandableRuns(
                List.of(first, second), piece -> false);

            assertThat(runs)
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        }
    }

    @Nested
    class CollectLandableFrontage {

        // The square's bottom and right sides on laid lines, so its corner at (100, 0) has a
        // laid line either side of it; the top and left on cells.
        private static final int[] LAID_EITHER_SIDE_OF_THE_BOTTOM_RIGHT = {LAID, LAID, 2, 3};

        // Below every run of the square, whose sides are 100 long, so none of them is a point.
        private static final double RESOLUTION = 10;

        // Above cell 2's run and below cell 3's, once the square is stretched: see the test.
        private static final double BETWEEN_THE_TWO_RUNS = 50;

        // Cell 7's frontier running through (100, 0), and cell 8's running nowhere near.
        private static final LabelledWall SHORE_THROUGH_THE_CORNER =
            new LabelledWall(new Segment(100, -50, 100, 50), 7);

        private static final LabelledWall SHORE_ELSEWHERE =
            new LabelledWall(new Segment(300, -50, 300, 50), 8);

        @Test
        void aCornerBetweenTwoLaidLinesOnAShoreIsThatCellsPoint() {

            var frontage = LandableFrontage.collectLandableFrontage(
                List.of(Face.encloseFace(
                    new LabelledRing(CORNERS, LAID_EITHER_SIDE_OF_THE_BOTTOM_RIGHT))),
                piece -> false,
                List.of(SHORE_ELSEWHERE, SHORE_THROUGH_THE_CORNER),
                RESOLUTION);

            assertThat(frontage.points())
                .hasSize(1);
            assertThat(frontage.points().get(0).cell())
                .isEqualTo(7);
            assertThat(frontage.points().get(0).point())
                .containsExactly(100, 0);
        }

        @Test
        void aCornerBetweenTwoLaidLinesOffEveryShoreIsNobodys() {
            // Two laid lines crossing in open void meet at a corner like any other.
            assertThat(LandableFrontage.collectLandableFrontage(
                    List.of(Face.encloseFace(
                        new LabelledRing(CORNERS, LAID_EITHER_SIDE_OF_THE_BOTTOM_RIGHT))),
                    piece -> false,
                    List.of(SHORE_ELSEWHERE),
                    RESOLUTION)
                .points())
                .isEmpty();
        }

        @Test
        void aCornerWithACellEdgeOnEitherSideIsARunsEndAndNotAPoint() {
            // The corner at (100, 100) has the laid right side on one side and cell 2's top on
            // the other: it is where cell 2's run starts, and a point there would draw the run's
            // end twice.
            assertThat(LandableFrontage.collectLandableFrontage(
                    List.of(Face.encloseFace(
                        new LabelledRing(CORNERS, LAID_EITHER_SIDE_OF_THE_BOTTOM_RIGHT))),
                    piece -> false,
                    List.of(new LabelledWall(new Segment(100, 50, 100, 150), 2)),
                    RESOLUTION)
                .points())
                .isEmpty();
        }

        @Test
        void aRunShorterThanTheResolutionIsAPointAtItsMiddle() {
            // The square's top, on cell 2, is 100 long and its left, on cell 3, is 100 long;
            // with the square squeezed to 20 wide the top is 20 long, under a resolution of 50,
            // and comes back as a point at (10, 100) while the left stays a run.
            var squeezed = List.of(
                new double[] {0, 0},
                new double[] {20, 0},
                new double[] {20, 100},
                new double[] {0, 100});

            var frontage = LandableFrontage.collectLandableFrontage(
                List.of(Face.encloseFace(
                    new LabelledRing(squeezed, LAID_EITHER_SIDE_OF_THE_BOTTOM_RIGHT))),
                piece -> false,
                List.of(),
                BETWEEN_THE_TWO_RUNS);

            assertThat(frontage.runs())
                .extracting(LandableFrontage.Run::cell)
                .containsExactly(3);
            assertThat(frontage.points())
                .hasSize(1);
            assertThat(frontage.points().get(0).cell())
                .isEqualTo(2);
            assertThat(frontage.points().get(0).point())
                .containsExactly(10, 100);
        }

        @Test
        void aCapturedPieceOffersNothing() {

            var frontage = LandableFrontage.collectLandableFrontage(
                List.of(Face.encloseFace(
                    new LabelledRing(CORNERS, LAID_EITHER_SIDE_OF_THE_BOTTOM_RIGHT))),
                piece -> true,
                List.of(SHORE_THROUGH_THE_CORNER),
                RESOLUTION);

            assertThat(frontage.runs())
                .isEmpty();
            assertThat(frontage.points())
                .isEmpty();
        }
    }

    // Any negative a tier might lay under.
    private static final int LAID = -9;
}
