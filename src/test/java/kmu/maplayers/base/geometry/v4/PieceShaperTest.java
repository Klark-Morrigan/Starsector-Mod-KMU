package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.EdgeInsetRule;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the inset over a piece, on one square.
 *
 * <p>Three things are pinned, and they are the three the rule can get wrong. What an edge faces
 * decides whether it moves, and only the frame faces nothing. What the rule says decides whether
 * any edge moves at all, which is what keeps the true partition drawable. And a hole moves the
 * other way from an outline under the one positive depth, which is the only place the winding
 * could quietly invert and still produce a plausible shape.
 *
 * <p>Every expectation is the square the shift lands on, written out, rather than the input with
 * the depth arithmetic done to it again.
 */
class PieceShaperTest {

    // A square 100 across from the origin, wound counter-clockwise as a bounded piece is.
    private static final List<double[]> SQUARE = List.of(
        new double[] {0, 0},
        new double[] {100, 0},
        new double[] {100, 100},
        new double[] {0, 100});

    // One cell per side, so every edge of the square faces something.
    private static final int[] ALL_AGAINST_CELLS = {0, 1, 2, 3};

    // The bottom edge is the sector's own rim and the other three face cells.
    private static final int[] BOTTOM_IS_THE_FRAME = {BareVoid.THE_FRAME, 1, 2, 3};

    private static final int[] ALL_THE_FRAME = {
        BareVoid.THE_FRAME, BareVoid.THE_FRAME, BareVoid.THE_FRAME, BareVoid.THE_FRAME};

    // A smaller square inside the first, wound the other way, as a hole is.
    private static final List<double[]> HOLE = List.of(
        new double[] {25, 25},
        new double[] {25, 75},
        new double[] {75, 75},
        new double[] {75, 25});

    // Round, so each expected corner below is the input corner plus or minus a number a reader
    // can do in their head.
    private static final double INSET = 10;

    // A strait 100 long and 20 across, wound counter-clockwise, with a small hole in it.
    private static final List<double[]> STRAIT = List.of(
        new double[] {0, 0},
        new double[] {100, 0},
        new double[] {100, 20},
        new double[] {0, 20});

    private static final List<double[]> STRAIT_HOLE = List.of(
        new double[] {40, 5},
        new double[] {40, 15},
        new double[] {60, 15},
        new double[] {60, 5});

    // More than half the strait's width: its two long edges cross before they meet, and what
    // the miter hands back is the strait turned inside out. Not the square at this depth,
    // whose four edges all cross and re-emerge as a smaller square wound the right way.
    private static final double DEEPER_THAN_HALF_THE_STRAIT = 15;

    // Well clear of any corner here, every one of which is a right angle: no corner in these
    // fixtures bevels for being sharp, so where one bevels anyway it is the winding saying so.
    private static final double MITER_SPIKE_LIMIT = 4;

    private static List<double[]> shapeSquare(int[] labels, EdgeInsetRule rule) {

        return PieceShaper.shapePiece(
            Face.encloseFace(new LabelledRing(SQUARE, labels)), rule, INSET, MITER_SPIKE_LIMIT)
            .outerRing();
    }

    @Nested
    class ShapePiece {

        @Test
        void anEdgeAgainstACellPullsIn() {

            assertThat(shapeSquare(ALL_AGAINST_CELLS, EdgeInsetRule.AT_EVERY_BORDER))
                .containsExactly(
                    new double[] {10, 10},
                    new double[] {90, 10},
                    new double[] {90, 90},
                    new double[] {10, 90});
        }

        @Test
        void anEdgeOnTheFrameStaysOnItsLine() {
            // The frame is the edge of the sector rather than the edge of anything, so a piece
            // running up to it has nothing to stand off from.
            assertThat(shapeSquare(ALL_THE_FRAME, EdgeInsetRule.AT_EVERY_BORDER))
                .containsExactly(
                    new double[] {0, 0},
                    new double[] {100, 0},
                    new double[] {100, 100},
                    new double[] {0, 100});
        }

        @Test
        void aKeptEdgeIsTruncatedWhereItMeetsAPulledInOne() {
            // The bottom stays on y = 0 and still gives up its ends: a kept edge running out to
            // the original corner would poke through the channel the other three just opened.
            assertThat(shapeSquare(BOTTOM_IS_THE_FRAME, EdgeInsetRule.AT_EVERY_BORDER))
                .containsExactly(
                    new double[] {10, 0},
                    new double[] {90, 0},
                    new double[] {90, 90},
                    new double[] {10, 90});
        }

        @Test
        void nowhereDrawsThePieceItself() {
            // What makes the partition judgeable: the same call, and what comes back is the
            // input.
            assertThat(shapeSquare(ALL_AGAINST_CELLS, EdgeInsetRule.NOWHERE))
                .containsExactly(
                    new double[] {0, 0},
                    new double[] {100, 0},
                    new double[] {100, 100},
                    new double[] {0, 100});
        }

        @Test
        void everywherePullsInTheFrameToo() {

            assertThat(shapeSquare(ALL_THE_FRAME, EdgeInsetRule.EVERYWHERE))
                .containsExactly(
                    new double[] {10, 10},
                    new double[] {90, 10},
                    new double[] {90, 90},
                    new double[] {10, 90});
        }

        @Test
        void aHoleGrowsSoThePiecePullsBackFromIt() {
            // The one place the winding could invert unnoticed. A hole runs against its piece,
            // so the one positive depth that shrinks an outline has to widen this - 25 to 75
            // reaching out to 15 and 85 - where a sign taken the other way would draw it in to
            // 35 and 65 and look perfectly reasonable.
            //
            // And each corner comes back as two, which is the same fact told a second way.
            // Growing a hole is an OUTWARD offset, and a corner offset outward is an arc rather
            // than a point: the bevel across it is that arc's straight stand-in, where a miter
            // would run the corner out to 15,15 and cut through the piece it belongs to. So the
            // ring bevels at every corner without any of them being sharp, because on a ring
            // wound against the offset every corner is a reflex one.
            var piece = Face
                .encloseFace(new LabelledRing(SQUARE, ALL_THE_FRAME))
                .cutOut(new LabelledRing(HOLE, ALL_AGAINST_CELLS));

            var shaped = PieceShaper.shapePiece(
                piece, EdgeInsetRule.AT_EVERY_BORDER, INSET, MITER_SPIKE_LIMIT);

            assertThat(shaped.holeRings()).hasSize(1);
            assertThat(shaped.holeRings().get(0))
                .containsExactly(
                    new double[] {25, 15},
                    new double[] {15, 25},
                    new double[] {15, 75},
                    new double[] {25, 85},
                    new double[] {75, 85},
                    new double[] {85, 75},
                    new double[] {85, 25},
                    new double[] {75, 15});
        }

        @Test
        void aPieceWithNoHolesGetsNone() {

            assertThat(PieceShaper.shapePiece(
                Face.encloseFace(new LabelledRing(SQUARE, ALL_AGAINST_CELLS)),
                EdgeInsetRule.AT_EVERY_BORDER,
                INSET,
                MITER_SPIKE_LIMIT)
                .holeRings())
                .isEmpty();
        }

        @Test
        void aPieceNarrowerThanTwoChannelsIsGoneHolesAndAll() {
            // Not a thinner square: the ring folds over and winds the wrong way, and a folded
            // ring handed on with a hole beside it would be drawn as a fill by the resolve.
            var piece = Face
                .encloseFace(new LabelledRing(STRAIT, ALL_AGAINST_CELLS))
                .cutOut(new LabelledRing(STRAIT_HOLE, ALL_AGAINST_CELLS));

            var shaped = PieceShaper.shapePiece(
                piece, EdgeInsetRule.AT_EVERY_BORDER, DEEPER_THAN_HALF_THE_STRAIT, MITER_SPIKE_LIMIT);

            assertThat(shaped.outerRing()).isEmpty();
            assertThat(shaped.holeRings()).isEmpty();
        }
    }
}
