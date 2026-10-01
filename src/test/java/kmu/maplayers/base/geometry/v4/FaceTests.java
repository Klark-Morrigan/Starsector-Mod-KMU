package kmu.maplayers.base.geometry.v4;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins that a piece knows its own size and which way round it is, holes and all.
 *
 * <p>The area is the load-bearing part. It is what the partition check adds up, and a piece
 * that runs around another - the open sea around a group of cells - reports the area it
 * actually covers only if what it runs around is taken off. Reported whole, every such piece
 * counts the thing inside it twice, and the check that the void and the cells cover the map
 * exactly once passes or fails on the arithmetic rather than on the geometry.
 */
class FaceTests {

    // A square 100 across from the origin, wound counter-clockwise, enclosing 10000.
    private static final LabelledRing SQUARE = new LabelledRing(
        List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 100},
            new double[] {0, 100}),
        new int[] {0, 1, 2, 3});

    // The same four corners walked the other way round, which is how the outside of
    // everything comes back from a walk.
    private static final LabelledRing SQUARE_WOUND_BACKWARDS = new LabelledRing(
        List.of(
            new double[] {0, 100},
            new double[] {100, 100},
            new double[] {100, 0},
            new double[] {0, 0}),
        new int[] {0, 1, 2, 3});

    // A square 20 across inside it, wound clockwise as a hole is, enclosing 400.
    private static final LabelledRing HOLE = new LabelledRing(
        List.of(
            new double[] {40, 40},
            new double[] {40, 60},
            new double[] {60, 60},
            new double[] {60, 40}),
        new int[] {7, 7, 7, 7});

    private static final double AREA_SLACK = 1e-6;

    @Nested
    class EncloseFace {

        @Test
        void aPieceCoversWhatItsRingEncloses() {

            assertThat(Face.encloseFace(SQUARE).measureArea())
                .isCloseTo(10000.0, within(AREA_SLACK));
        }

        @Test
        void aPieceWoundToFillIsNotTheOutsideOfEverything() {

            assertThat(Face.encloseFace(SQUARE).isOuterFace())
                .isFalse();
        }

        @Test
        void aRingWoundTheOtherWayIsTheOutsideOfEverything() {
            // Which of the two a face is, is read off its own ring rather than carried beside
            // it, so no caller can label it one way and wind it the other.
            assertThat(Face.encloseFace(SQUARE_WOUND_BACKWARDS).isOuterFace())
                .isTrue();
        }

        @Test
        void aPieceKeepsWhatEachOfItsEdgesLiesOn() {

            assertThat(Face.encloseFace(SQUARE).edgeLabels())
                .containsExactly(0, 1, 2, 3);
        }
    }

    @Nested
    class CutOut {

        @Test
        void aPieceLosesTheAreaOfWhatIsCutFromIt() {

            assertThat(Face.encloseFace(SQUARE).cutOut(HOLE).measureArea())
                .isCloseTo(9600.0, within(AREA_SLACK));
        }

        @Test
        void aPieceKeepsItsOwnRingWhenSomethingIsCutFromIt() {

            assertThat(Face.encloseFace(SQUARE).cutOut(HOLE).boundary())
                .isEqualTo(SQUARE.vertices());
        }

        @Test
        void aHoleIsShoreLikeAnyOther() {
            // Carried with its labels, because a hole's boundary bounds the piece as much as
            // the outline does - the sea's shore IS its holes, and a hole stripped of them
            // would leave that shore belonging to nobody.
            var cut = Face.encloseFace(SQUARE).cutOut(HOLE);

            assertThat(cut.holes()).hasSize(1);

            assertThat(cut.holes().get(0).edgeLabels()).containsOnly(7);
        }

        @Test
        void twoHolesBothComeOff() {

            var twice = Face.encloseFace(SQUARE)
                .cutOut(HOLE)
                .cutOut(new LabelledRing(
                    List.of(
                        new double[] {10, 10},
                        new double[] {10, 20},
                        new double[] {20, 20},
                        new double[] {20, 10}),
                    new int[] {8, 8, 8, 8}));

            assertThat(twice.holes()).hasSize(2);

            assertThat(twice.measureArea()).isCloseTo(9500.0, within(AREA_SLACK));
        }
    }

    @Nested
    class CollectRings {

        @Test
        void theOutlineComesFirstThenEachHole() {

            var cut = Face.encloseFace(SQUARE).cutOut(HOLE);

            assertThat(cut.collectRings())
                .containsExactly(SQUARE, HOLE);
        }

        @Test
        void aSolidPieceIsItsOutlineAlone() {

            assertThat(Face.encloseFace(SQUARE).collectRings())
                .containsExactly(SQUARE);
        }
    }

    @Nested
    class CollectCells {

        @Test
        void everyCellOnTheOutlineAndTheHolesComesBackOnceAscending() {
            // Cell 3 twice on the outline and once more on the hole: once in the answer, and
            // in order rather than in the order the walk met them.
            var piece = Face.encloseFace(new LabelledRing(SQUARE.vertices(), new int[] {3, 1, 3, 2}))
                .cutOut(new LabelledRing(HOLE.vertices(), new int[] {7, 3, 7, 7}));

            assertThat(piece.collectCells())
                .containsExactly(1, 2, 3, 7);
        }

        @Test
        void aLaidLineIsNoCell() {
            // The frame and a tier's line name no cell a piece could be called after.
            var piece = Face.encloseFace(new LabelledRing(
                SQUARE.vertices(), new int[] {VoidPartition.THE_FRAME, 4, LakeTier.THE_LAKE_COAST, 4}));

            assertThat(piece.collectCells())
                .containsExactly(4);
        }
    }
}
