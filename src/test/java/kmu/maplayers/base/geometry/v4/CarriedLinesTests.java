package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit coverage for how a line becomes a wall: one segment, each end moved onto its shore.
 *
 * <p>The ends are the subject. The line itself is copied to the unit, so what can go wrong is
 * where an end goes - which shore it is moved to, how far past it, and what happens when no
 * shore is found - each of which decides whether the walk finds a junction at the shore or a
 * wall floating beside it.
 */
class CarriedLinesTests {

    // Cells drawn with three sides round the circle, so the sagitta is exactly half the cell
    // radius: 100 * (1 - cos 60 degrees) = 50. The shore is looked for within two of those, 100,
    // and the wall ends a twentieth of one past it, 2.5.
    private static final SectorGeometryParameters PARAMETERS =
        new SectorGeometryParameters(100, 3, 10, 1, 8);

    private static final int LEFT_CELL = 0;

    private static final int RIGHT_CELL = 1;

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {1000, 0});

    // A line 300 above the sites, running from directly above the left one to directly above
    // the right one, so each end is moved straight down and lands on a round number.
    private static final CellGap LINE =
        new CellGap(LEFT_CELL, RIGHT_CELL, new double[] {0, 300}, new double[] {1000, 300}, 1000);

    // Each cell's shore under its end of the line: the left one 40 below the line, the right
    // one 20 below, both well within the search.
    private static final LabelledWall LEFT_SHORE =
        new LabelledWall(new Segment(-50, 260, 50, 260), LEFT_CELL);

    private static final LabelledWall RIGHT_SHORE =
        new LabelledWall(new Segment(950, 280, 1050, 280), RIGHT_CELL);

    // A second edge of the left cell further down, as a corner would leave beyond the first.
    private static final LabelledWall LEFT_SHORE_FURTHER_DOWN =
        new LabelledWall(new Segment(-50, 230, 50, 230), LEFT_CELL);

    // The right cell's edge lying under the LEFT end, which the left end must not take for
    // its own shore.
    private static final LabelledWall RIGHT_CELLS_EDGE_UNDER_THE_LEFT_END =
        new LabelledWall(new Segment(-50, 260, 50, 260), RIGHT_CELL);

    private static final List<LabelledWall> BOTH_SHORES = List.of(LEFT_SHORE, RIGHT_SHORE);

    private static final List<LabelledWall> NO_SHORE = List.of();

    // Any negative a tier might lay under; the walls take whatever they are handed.
    private static final int SOME_TIER = -7;

    private static final double SAME_POINT = 1e-9;

    @Nested
    class LayCarriedLines {

        @Test
        void aLineIsOneWallEndingJustInsideEachShore() {
            // From (0, 300) down to the left shore at (0, 260) and 2.5 past it, (0, 257.5);
            // from (1000, 300) down to the right shore at (1000, 280) and 2.5 past, (1000, 277.5).
            var laid = CarriedLines.layCarriedLines(
                List.of(LINE), BOTH_SHORES, SITES, PARAMETERS, SOME_TIER);

            assertThat(laid.walls())
                .hasSize(1);
            assertThatPointIs(laid.walls().get(0).segment().readStart(), 0, 257.5);
            assertThatPointIs(laid.walls().get(0).segment().readEnd(), 1000, 277.5);
        }

        @Test
        void anEndWithNoShoreInReachStepsTheSameDistanceTowardsItsSite() {
            // Already inside, or on a corner: the same step puts it inside either way.
            var laid = CarriedLines.layCarriedLines(
                List.of(LINE), NO_SHORE, SITES, PARAMETERS, SOME_TIER);

            assertThatPointIs(laid.walls().get(0).segment().readStart(), 0, 297.5);
            assertThatPointIs(laid.walls().get(0).segment().readEnd(), 1000, 297.5);
        }

        @Test
        void anEndLooksOnlyForItsOwnCellsShore() {
            // The edge under the left end belongs to the right cell, so the left end passes
            // through it as if it were not there.
            var laid = CarriedLines.layCarriedLines(
                List.of(LINE),
                List.of(RIGHT_CELLS_EDGE_UNDER_THE_LEFT_END, RIGHT_SHORE),
                SITES,
                PARAMETERS,
                SOME_TIER);

            assertThatPointIs(laid.walls().get(0).segment().readStart(), 0, 297.5);
        }

        @Test
        void theNearestShoreWins() {
            // Two edges of the left cell under its end: the wall stops at the first.
            var laid = CarriedLines.layCarriedLines(
                List.of(LINE),
                List.of(LEFT_SHORE_FURTHER_DOWN, LEFT_SHORE, RIGHT_SHORE),
                SITES,
                PARAMETERS,
                SOME_TIER);

            assertThatPointIs(laid.walls().get(0).segment().readStart(), 0, 257.5);
        }

        @Test
        void everyWallCarriesTheLabelItWasHanded() {

            var laid = CarriedLines.layCarriedLines(
                List.of(LINE), BOTH_SHORES, SITES, PARAMETERS, SOME_TIER);

            assertThat(laid.walls())
                .extracting(LabelledWall::label)
                .containsOnly(SOME_TIER);
        }

        @Test
        void theLineIsDrawnAsItsTierFoundIt() {
            // The wall's ends moved; the drawn line's did not, since what a reader compares
            // against the other construction is the line that construction found.
            var laid = CarriedLines.layCarriedLines(
                List.of(LINE), BOTH_SHORES, SITES, PARAMETERS, SOME_TIER);

            assertThat(laid.lines())
                .hasSize(1);
            assertThat(laid.lines().get(0).get(0))
                .containsExactly(0, 300);
            assertThat(laid.lines().get(0).get(1))
                .containsExactly(1000, 300);
        }

        @Test
        void noLinesLayNothing() {

            var laid = CarriedLines.layCarriedLines(
                List.of(), BOTH_SHORES, SITES, PARAMETERS, SOME_TIER);

            assertThat(laid.walls())
                .isEmpty();
            assertThat(laid.lines())
                .isEmpty();
        }
    }

    private static void assertThatPointIs(double[] point, double x, double y) {

        assertThat(point[0])
            .isCloseTo(x, within(SAME_POINT));
        assertThat(point[1])
            .isCloseTo(y, within(SAME_POINT));
    }
}
