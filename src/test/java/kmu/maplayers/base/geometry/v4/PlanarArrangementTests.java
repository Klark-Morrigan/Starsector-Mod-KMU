package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for reading a welded graph back out as lines.
 *
 * <p>What the second stage of a walk is built on: the laid lines are cut against the base as
 * these lines give it, so a line missing, doubled, or ending off the corner it was welded to
 * is a base the laid lines are cut against wrongly.
 */
class PlanarArrangementTests {

    // Far above the gap the near-miss fixture below leaves, and far below anything else in it.
    private static final double WELD_TOLERANCE = 1;

    private static final int BOTTOM = 0;
    private static final int RIGHT = 1;
    private static final int TOP = 2;
    private static final int LEFT = 3;

    // A square 100 across, its sides labelled, and its bottom side laid a second time exactly
    // along the first.
    private static final List<LabelledWall> SQUARE_WITH_BOTTOM_TWICE = List.of(
        new LabelledWall(new Segment(0, 0, 100, 0), BOTTOM),
        new LabelledWall(new Segment(100, 0, 100, 100), RIGHT),
        new LabelledWall(new Segment(100, 100, 0, 100), TOP),
        new LabelledWall(new Segment(0, 100, 0, 0), LEFT),
        new LabelledWall(new Segment(100, 0, 0, 0), BOTTOM));

    // Two lines meant to meet at (50, 0), the second reporting that corner half a unit off.
    private static final List<LabelledWall> NEAR_MISS = List.of(
        new LabelledWall(new Segment(0, 0, 50, 0), BOTTOM),
        new LabelledWall(new Segment(50.5, 0, 100, 0), RIGHT));

    @Nested
    class CollectLines {

        @Test
        void eachEdgeComesBackOnceHoweverOftenItWasLaid() {

            var lines = PlanarArrangement.weldArrangement(SQUARE_WITH_BOTTOM_TWICE, WELD_TOLERANCE)
                .collectLines();

            assertThat(lines)
                .hasSize(4);
        }

        @Test
        void everyEdgeKeepsItsLabel() {

            var lines = PlanarArrangement.weldArrangement(SQUARE_WITH_BOTTOM_TWICE, WELD_TOLERANCE)
                .collectLines();

            assertThat(lines)
                .extracting(LabelledWall::label)
                .containsExactlyInAnyOrder(BOTTOM, RIGHT, TOP, LEFT);
        }

        @Test
        void linesMeetingWithinTheWeldShareOneCornerExactly() {
            // The first report of a corner is the one kept, so the second line now starts at
            // (50, 0) and not at (50.5, 0): the two meet to the bit, which is what lets a later
            // stage join what is laid against them at rounding.
            var lines = PlanarArrangement.weldArrangement(NEAR_MISS, WELD_TOLERANCE)
                .collectLines();

            assertThat(lines)
                .filteredOn(line -> line.label() == RIGHT)
                .singleElement()
                .satisfies(line -> assertThat(
                        List.of(line.segment().readStart(), line.segment().readEnd()))
                    .anySatisfy(end -> assertThat(end)
                        .containsExactly(50, 0)));
        }
    }
}
