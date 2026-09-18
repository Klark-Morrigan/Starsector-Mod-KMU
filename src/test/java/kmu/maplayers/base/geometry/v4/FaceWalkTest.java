package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit coverage for the division itself, on shapes small enough to hold in the head.
 *
 * <p>The areas are the subject, not the outlines. What a walk gets wrong is not where it put a
 * corner but WHICH pieces it thinks there are - a junction it ran past leaves two pieces welded
 * into one, and every corner in the outline that comes back is still in the right place. So the
 * assertions are counts and areas, which are what a missed junction moves and a moved corner
 * barely touches.
 *
 * <p>One square with things laid across it throughout, so every expected area is a half, a
 * quarter or the whole of one number a reader already has in front of them.
 */
class FaceWalkTest {

    // A square 100 across from the origin, wound counter-clockwise, enclosing 10000.
    private static final List<double[]> SQUARE = List.of(
        new double[] {0, 0},
        new double[] {100, 0},
        new double[] {100, 100},
        new double[] {0, 100});

    // How far two reports of one corner may stand apart and still be welded into one. Far below
    // anything these fixtures contain: every shared corner here is reported from the same
    // literal, so the welding joins what genuinely coincides and reaches nothing else.
    private static final double WELD_TOLERANCE = 1e-3;

    // How far a reported area may sit from the stated one. The shoelace sum over coordinates
    // this round is exact in binary, so this is room against a change in the arithmetic rather
    // than room the shapes need.
    private static final double AREA_SLACK = 1e-6;

    // How far a reported corner may sit from where it was laid, for the same reason.
    private static final double SAME_POINT = 1e-6;

    @Nested
    class WalkFaces {

        @Test
        void aSquareAloneLeavesItselfAndTheOutsideAroundIt() {
            // The floor everything below is read against. Two faces rather than one, because
            // the outside of a shape is a piece of the division like any other - and it is the
            // only piece whose winding runs the other way, which is how it is told apart with
            // nothing labelled.
            var faces = FaceWalk.walkFaces(List.of(SQUARE), List.of(), WELD_TOLERANCE);

            assertThat(faces).hasSize(2);

            assertThat(measureBoundedAreas(faces)).hasSize(1);

            assertThat(measureBoundedAreas(faces).get(0))
                .isCloseTo(10000.0, within(AREA_SLACK));

            assertThat(findTheOuterFace(faces).measureArea())
                .isCloseTo(10000.0, within(AREA_SLACK));
        }

        @Test
        void aChordAcrossASquareLeavesAPieceEitherSideOfIt() {
            // The plainest division there is. What it pins is that the walk turns where the
            // chord meets the ring rather than running past it: a walk blind to those two
            // junctions hands the square back whole, correct in every corner and wrong about
            // the one thing asked.
            var faces = FaceWalk.walkFaces(
                List.of(SQUARE),
                List.of(new Segment(0, 50, 100, 50)),
                WELD_TOLERANCE);

            assertThat(measureBoundedAreas(faces))
                .hasSize(2)
                .allSatisfy(area -> assertThat(area).isCloseTo(5000.0, within(AREA_SLACK)));
        }

        @Test
        void twoChordsCrossingLeaveAPieceInEachQuarter() {
            // Where the two chords cross is a vertex four edges leave, and the walk has to take
            // the next one round rather than the one straight on. Taking the one straight on is
            // the natural mistake, and it comes back as two pieces of 5000 instead of four of
            // 2500 - which is why the areas are asserted and not just the count.
            var faces = FaceWalk.walkFaces(
                List.of(SQUARE),
                List.of(
                    new Segment(0, 50, 100, 50),
                    new Segment(50, 0, 50, 100)),
                WELD_TOLERANCE);

            assertThat(measureBoundedAreas(faces))
                .hasSize(4)
                .allSatisfy(area -> assertThat(area).isCloseTo(2500.0, within(AREA_SLACK)));
        }

        @Test
        void twoWallsMeetingAtAPointCutOffTheCornerBetweenThem() {
            // Neither wall divides anything by itself - each has a loose end in the middle of
            // the square. Together they reach from one side to another and take the corner off,
            // which is the thing no per-wall judgement can see and a division answers without
            // being asked.
            var faces = FaceWalk.walkFaces(
                List.of(SQUARE),
                List.of(
                    new Segment(0, 50, 50, 50),
                    new Segment(50, 50, 50, 0)),
                WELD_TOLERANCE);

            var areas = measureBoundedAreas(faces);

            assertThat(areas).hasSize(2);

            assertThat(areas.get(0)).isCloseTo(2500.0, within(AREA_SLACK));
            assertThat(areas.get(1)).isCloseTo(7500.0, within(AREA_SLACK));
        }

        @Test
        void aWallWithALooseEndDividesNothing() {
            // A line laid across a piece that does not reach the far side leaves that piece
            // whole. Nothing decides this about the wall - it is laid exactly as the two above
            // were, and what differs is only what the rest of the lines do.
            var faces = FaceWalk.walkFaces(
                List.of(SQUARE),
                List.of(new Segment(0, 50, 40, 50)),
                WELD_TOLERANCE);

            var areas = measureBoundedAreas(faces);

            assertThat(areas).hasSize(1);

            assertThat(areas.get(0)).isCloseTo(10000.0, within(AREA_SLACK));
        }

        @Test
        void aWallWithALooseEndIsStillWalkedAsASlitInThePieceAroundIt() {
            // The other half of the case above, and the one that tells "divides nothing" from
            // "was dropped". The wall is in the piece's boundary, walked out to its loose end
            // and back, so a later line laid onto that end has something to join.
            var faces = FaceWalk.walkFaces(
                List.of(SQUARE),
                List.of(new Segment(0, 50, 40, 50)),
                WELD_TOLERANCE);

            assertThat(findTheBoundedFace(faces).boundary())
                .anySatisfy(corner -> assertThat(
                        Points.computeDistance(corner, new double[] {40, 50}))
                    .isLessThan(SAME_POINT));
        }
    }

    // Every piece that is not the outside, by area, smallest first. Sorted so a fixture whose
    // pieces differ can name them in an order a reader can follow rather than in whichever
    // order the walk happened to close them.
    private static List<Double> measureBoundedAreas(List<Face> faces) {

        return collectBoundedFaces(faces).stream()
            .map(Face::measureArea)
            .sorted()
            .toList();
    }

    // The one piece outside everything, insisted on rather than picked out of what came back: a
    // run with two of them is a fixture whose lines fell into separate groups, which is a
    // different fixture from the one being asserted about.
    private static Face findTheOuterFace(List<Face> faces) {

        var outer = faces.stream().filter(Face::isOuterFace).toList();

        assertThat(outer)
            .as("one group of lines has one outside")
            .hasSize(1);

        return outer.get(0);
    }

    private static Face findTheBoundedFace(List<Face> faces) {

        var bounded = collectBoundedFaces(faces);

        assertThat(bounded)
            .as("this fixture leaves one piece")
            .hasSize(1);

        return bounded.get(0);
    }

    private static List<Face> collectBoundedFaces(List<Face> faces) {

        return faces.stream().filter(face -> !face.isOuterFace()).toList();
    }
}
