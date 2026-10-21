package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit coverage for cutting a bag of lines wherever they cross, which is what lets the face walk
 * turn at every place two lines meet.
 *
 * <p>Each case is a handful of lines on round coordinates, so where every piece starts and ends
 * is a number read off the drawing rather than worked out.
 */
class SegmentCrossingsTests {

    private static final int ACROSS = 1;
    private static final int UP = 2;
    private static final int SECOND_UP = 3;

    private static final double SAME_POINT = 1e-9;

    // Ten long, along the x axis.
    private static final LabelledWall ACROSS_LINE =
        new LabelledWall(new Segment(0, 0, 10, 0), ACROSS);

    @Nested
    class SplitAtCrossings {

        @Test
        void twoLinesCrossingAreEachCutInTwoAtTheCrossing() {

            var pieces = SegmentCrossings.splitAtCrossings(List.of(
                ACROSS_LINE,
                new LabelledWall(new Segment(5, -5, 5, 5), UP)));

            assertThat(pieces)
                .hasSize(4);

            assertThatPieceIs(pieces.get(0), 0, 0, 5, 0, ACROSS);
            assertThatPieceIs(pieces.get(1), 5, 0, 10, 0, ACROSS);
            assertThatPieceIs(pieces.get(2), 5, -5, 5, 0, UP);
            assertThatPieceIs(pieces.get(3), 5, 0, 5, 5, UP);
        }

        @Test
        void aLineCrossedTwiceIsCutInThreeInOrderAlongIt() {
            // The crossing further along is offered first, so the pieces come out in order only
            // if the cuts are sorted along the line.
            var pieces = SegmentCrossings.splitAtCrossings(List.of(
                ACROSS_LINE,
                new LabelledWall(new Segment(7, -5, 7, 5), UP),
                new LabelledWall(new Segment(3, -5, 3, 5), SECOND_UP)));

            var across = pieces
                .stream()
                .filter(piece -> piece.label() == ACROSS)
                .toList();

            assertThat(across)
                .hasSize(3);

            assertThatPieceIs(across.get(0), 0, 0, 3, 0, ACROSS);
            assertThatPieceIs(across.get(1), 3, 0, 7, 0, ACROSS);
            assertThatPieceIs(across.get(2), 7, 0, 10, 0, ACROSS);
        }

        @Test
        void aLineNothingCrossesComesBackWhole() {

            var pieces = SegmentCrossings.splitAtCrossings(List.of(
                ACROSS_LINE,
                new LabelledWall(new Segment(0, 5, 10, 5), UP)));

            assertThat(pieces)
                .hasSize(2);

            assertThatPieceIs(pieces.get(0), 0, 0, 10, 0, ACROSS);
            assertThatPieceIs(pieces.get(1), 0, 5, 10, 5, UP);
        }

        @Test
        void linesMeetingAtAnEndComeBackWholeWithNoEmptyPiece() {
            // The two meet at (10, 0), which is an end of both: a cut there leaves nothing on
            // one side of it, and nothing is what is dropped.
            var pieces = SegmentCrossings.splitAtCrossings(List.of(
                ACROSS_LINE,
                new LabelledWall(new Segment(10, 0, 10, 10), UP)));

            assertThat(pieces)
                .hasSize(2);

            assertThatPieceIs(pieces.get(0), 0, 0, 10, 0, ACROSS);
            assertThatPieceIs(pieces.get(1), 10, 0, 10, 10, UP);
        }

        @Test
        void aLineTooShortToHaveADirectionIsDropped() {

            var pieces = SegmentCrossings.splitAtCrossings(List.of(
                ACROSS_LINE,
                new LabelledWall(new Segment(20, 20, 20, 20), UP)));

            assertThat(pieces)
                .hasSize(1);

            assertThatPieceIs(pieces.get(0), 0, 0, 10, 0, ACROSS);
        }

        @Test
        void linesLyingAlongEachOtherAreNotCut() {
            // An overlap has no single crossing point, so neither line is cut where the other
            // begins or ends.
            var pieces = SegmentCrossings.splitAtCrossings(List.of(
                ACROSS_LINE,
                new LabelledWall(new Segment(5, 0, 15, 0), UP)));

            assertThat(pieces)
                .hasSize(2);

            assertThatPieceIs(pieces.get(0), 0, 0, 10, 0, ACROSS);
            assertThatPieceIs(pieces.get(1), 5, 0, 15, 0, UP);
        }

        @Test
        void noLinesCutToNothing() {

            assertThat(SegmentCrossings.splitAtCrossings(List.of()))
                .isEmpty();
        }
    }

    private static void assertThatPieceIs(
            LabelledWall piece,
            double fromX,
            double fromY,
            double toX,
            double toY,
            int label) {

        assertThat(piece.label())
            .isEqualTo(label);
        assertThat(piece.segment().readStart()[0])
            .isCloseTo(fromX, within(SAME_POINT));
        assertThat(piece.segment().readStart()[1])
            .isCloseTo(fromY, within(SAME_POINT));
        assertThat(piece.segment().readEnd()[0])
            .isCloseTo(toX, within(SAME_POINT));
        assertThat(piece.segment().readEnd()[1])
            .isCloseTo(toY, within(SAME_POINT));
    }
}
