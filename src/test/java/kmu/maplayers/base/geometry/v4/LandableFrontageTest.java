package kmu.maplayers.base.geometry.v4;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the frontage of a piece is, on one square.
 *
 * <p>The rule has no arithmetic in it, so what is pinned is the bookkeeping: every EDGE on a
 * cell is covered by exactly one run, a run carries both ends of every edge it covers and so
 * meets the next where one cell gives way to it, the frame is nobody's, and a piece's holes
 * are shore as much as its outline is.
 */
class LandableFrontageTest {

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
                    CORNERS, new int[] {0, BareVoid.THE_FRAME, 2, 3})));

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
    }
}
