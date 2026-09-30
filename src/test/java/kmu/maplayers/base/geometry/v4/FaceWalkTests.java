package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>The labels are the other subject. A piece is read by what bounds it, and the walk is the
 * only place that can be got wrong silently: a label dropped at the cutting or swapped at the
 * welding leaves a piece with the right shape that says the wrong thing about itself.
 *
 * <p>One square with things laid across it throughout, so every expected area is a half, a
 * quarter or the whole of one number a reader already has in front of them.
 */
class FaceWalkTests {

    // A square 100 across from the origin, wound counter-clockwise, enclosing 10000. Its four
    // sides labelled 0 to 3 in walk order: bottom, right, top, left.
    private static final List<LabelledWall> SQUARE = layRingSides(
        List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 100},
            new double[] {0, 100}),
        new int[] {0, 1, 2, 3});

    // What every wall laid across the square is labelled. Well clear of the sides' labels, so
    // a wall's label turning up where a side's should be is a fault and not a coincidence.
    private static final int WALL = 9;

    // A square 20 across in the middle of the first, touching nothing - so the two are
    // separate groups of lines, and whether one is cut out of the other is a real question
    // rather than one about a shared corner.
    private static final int INNER = 5;

    private static final List<LabelledWall> INNER_SQUARE = layRingSides(
        List.of(
            new double[] {40, 40},
            new double[] {60, 40},
            new double[] {60, 60},
            new double[] {40, 60}),
        new int[] {INNER, INNER, INNER, INNER});

    // What a walk of the base alone lays onto it.
    private static final List<LabelledWall> NOTHING_LAID = List.of();

    // How far two reports of one corner may stand apart and still be welded into one. Far below
    // anything these fixtures contain: every shared corner here is reported from the same
    // literal, so the welding joins what genuinely coincides and reaches nothing else.
    private static final double WELD_TOLERANCE = 1e-3;

    // A weld coarse enough to reach across the fixtures below, for the cases about what a
    // coarse weld may and may not move.
    private static final double COARSE_WELD = 10;

    // The square with a notch in its top reaching down to (50, 46), and a corner on its left
    // side at (0, 50). Encloses 7300. A wall laid along y = 44 crosses the left side 6 below
    // that corner and passes 2 below the notch's tip - within the coarse weld of the one, and
    // not of the other.
    private static final List<LabelledWall> NOTCHED_SQUARE = layRingSides(
        List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 100},
            new double[] {50, 46},
            new double[] {0, 100},
            new double[] {0, 50}),
        new int[] {0, 1, 2, 2, 3, 3});

    // That wall, reaching 8 past each side so it crosses both outright.
    private static final LabelledWall WALL_UNDER_THE_NOTCH =
        new LabelledWall(new Segment(-8, 44, 108, 44), WALL);

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
            var faces = FaceWalk.walkFaces(SQUARE, WELD_TOLERANCE, NOTHING_LAID);

            assertThat(faces)
                .hasSize(2);

            assertThat(measureBoundedAreas(faces))
                .hasSize(1);
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
                joinWalls(SQUARE, List.of(new LabelledWall(new Segment(0, 50, 100, 50), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

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
                joinWalls(SQUARE, List.of(
                    new LabelledWall(new Segment(0, 50, 100, 50), WALL),
                    new LabelledWall(new Segment(50, 0, 50, 100), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

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
                joinWalls(SQUARE, List.of(
                    new LabelledWall(new Segment(0, 50, 50, 50), WALL),
                    new LabelledWall(new Segment(50, 50, 50, 0), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            var areas = measureBoundedAreas(faces);

            assertThat(areas)
                .hasSize(2);

            assertThat(areas.get(0))
                .isCloseTo(2500.0, within(AREA_SLACK));
            assertThat(areas.get(1))
                .isCloseTo(7500.0, within(AREA_SLACK));
        }

        @Test
        void aWallWithALooseEndDividesNothing() {
            // A line laid across a piece that does not reach the far side leaves that piece
            // whole. Nothing decides this about the wall - it is laid exactly as the two above
            // were, and what differs is only what the rest of the lines do.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, List.of(new LabelledWall(new Segment(0, 50, 40, 50), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            var areas = measureBoundedAreas(faces);

            assertThat(areas)
                .hasSize(1);
            assertThat(areas.get(0))
                .isCloseTo(10000.0, within(AREA_SLACK));
        }

        @Test
        void aWallWithALooseEndIsStillWalkedAsASlitInThePieceAroundIt() {
            // The other half of the case above, and the one that tells "divides nothing" from
            // "was dropped". The wall is in the piece's boundary, walked out to its loose end
            // and back, so a later line laid onto that end has something to join.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, List.of(new LabelledWall(new Segment(0, 50, 40, 50), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(findTheBoundedFace(faces).boundary())
                .anySatisfy(corner -> assertThat(
                        Points.computeDistance(corner, new double[] {40, 50}))
                    .isLessThan(SAME_POINT));
        }

        @Test
        void everyEdgeOfAPieceSaysWhichLineItLiesOn() {
            // The chord fixture read for its labels. The lower piece runs along the bottom,
            // the lower halves of the right and left sides, and the chord; the upper piece
            // along the upper halves, the top, and the chord. A label lost at the cut or
            // swapped at the weld shows here as a side's label missing or the wall's doubled.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, List.of(new LabelledWall(new Segment(0, 50, 100, 50), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(collectBoundedFaces(faces))
                .extracting(FaceWalkTests::sortLabels)
                .containsExactlyInAnyOrder(
                    new int[] {0, 1, 3, 9},
                    new int[] {1, 2, 3, 9});
        }

        @Test
        void aSlitCarriesItsWallsLabelBothWays() {
            // Out along the wall and back down its other side are two edges of the one piece,
            // and both lie on the wall. The label is a fact about the line, not about which
            // side of it the walk was on.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, List.of(new LabelledWall(new Segment(0, 50, 40, 50), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(Arrays.stream(findTheBoundedFace(faces).edgeLabels())
                    .filter(label -> label == WALL)
                    .count())
                .isEqualTo(2);
        }

        @Test
        void aRingInsideAnotherIsCutOutOfIt() {
            // Two rings that never touch are two groups, each closing an outside of its own.
            // The inner group's outside sits within the outer group's piece, so it is what
            // that piece runs around rather than a piece in its own right - and the piece
            // loses its area. Left as a piece, the two would overlap and everything that adds
            // up areas over a division would count the middle twice.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, INNER_SQUARE),
                WELD_TOLERANCE,
                NOTHING_LAID);

            var outer = findPieceOfArea(faces, 9600.0);

            assertThat(outer.holes())
                .hasSize(1);
            assertThat(outer.holes().get(0).edgeLabels())
                .containsOnly(INNER);
        }

        @Test
        void aRingInsideAnotherIsStillAPieceOfItsOwn() {
            // Being cut out of the piece around it does not stop it being one. What the walk
            // hands back is every piece, and the inner ring bounds one.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, INNER_SQUARE),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(measureBoundedAreas(faces))
                .hasSize(2);

            assertThat(findPieceOfArea(faces, 400.0).holes())
                .isEmpty();
        }

        @Test
        void onlyTheOutsideOfEverythingSurvivesAsAnOuterFace() {
            // The inner group's outside became a hole, so the one left is the outside of the
            // whole division - which is what makes a frame worth laying, since framed there is
            // no such face and every piece is bounded.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, INNER_SQUARE),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(faces)
                .filteredOn(Face::isOuterFace)
                .hasSize(1);
        }

        @Test
        void aPieceIsNotCutOutOfOneItSharesEdgesWith() {
            // The chord's two pieces run along the very same edges, so asking whether one
            // sits inside the other is asking about a corner on both their boundaries - which
            // has no answer. Only groups that share nothing are weighed against each other;
            // asking anyway cut each piece out of the one beside it.
            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, List.of(new LabelledWall(new Segment(0, 50, 100, 50), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(collectBoundedFaces(faces))
                .allSatisfy(piece -> assertThat(piece.holes())
                    .isEmpty());
        }

        @Test
        void labelsRunParallelToTheBoundary() {

            var faces = FaceWalk.walkFaces(
                joinWalls(SQUARE, List.of(
                    new LabelledWall(new Segment(0, 50, 100, 50), WALL),
                    new LabelledWall(new Segment(50, 0, 50, 100), WALL))),
                WELD_TOLERANCE,
                NOTHING_LAID);

            assertThat(faces)
                .allSatisfy(face ->
                    assertThat(face.edgeLabels())
                        .hasSize(face.boundary().size()));
        }

        @Test
        void aLaidWallIsNotSwungAcrossACornerByTheBaseWeld() {
            // The wall crosses the left side at (0, 44), six below the ring's corner at
            // (0, 50) - within the coarse weld. Welded together with the ring, that junction is
            // pulled onto the corner and the wall now runs from (0, 50) to (100, 44): at
            // x = 50 it stands at 47, above the notch's tip at 46, and crosses a line the
            // cutting never saw. Laid after the base, the junction stays at (0, 44), and the
            // wall divides the ring into the 100 by 44 below it and the 2900 above.
            var faces = FaceWalk.walkFaces(
                NOTCHED_SQUARE,
                COARSE_WELD,
                List.of(WALL_UNDER_THE_NOTCH));

            var areas = measureBoundedAreas(faces);

            assertThat(areas)
                .hasSize(2);

            assertThat(areas.get(0))
                .isCloseTo(2900.0, within(AREA_SLACK));
            assertThat(areas.get(1))
                .isCloseTo(4400.0, within(AREA_SLACK));
        }

        @Test
        void aLaidWallsLooseEndStaysWhereItWasLaid() {
            // The other thing the base weld must not do to a laid line: reach its loose end.
            // The end at (-8, 44) is within the coarse weld of the junction at (0, 44), and
            // pulled onto it the stub outside the ring would vanish. It is a slit in the
            // outside, and the outside is walked to it and back.
            var faces = FaceWalk.walkFaces(
                NOTCHED_SQUARE,
                COARSE_WELD,
                List.of(WALL_UNDER_THE_NOTCH));

            assertThat(findTheOuterFace(faces).boundary())
                .anySatisfy(corner -> assertThat(
                        Points.computeDistance(corner, new double[] {-8, 44}))
                    .isLessThan(SAME_POINT));
        }

        @Test
        void theBaseIsStillWeldedAtItsOwnTolerance() {
            // What the two-stage joining must keep: the base's own corners, reported apart by
            // less than its tolerance, still meet. The square laid as five loose walls, its
            // left side in two that miss each other by 4; under the coarse weld it closes.
            var faces = FaceWalk.walkFaces(
                List.of(
                    new LabelledWall(new Segment(0, 0, 100, 0), 0),
                    new LabelledWall(new Segment(100, 0, 100, 100), 1),
                    new LabelledWall(new Segment(100, 100, 0, 100), 2),
                    new LabelledWall(new Segment(0, 100, 0, 54), 3),
                    new LabelledWall(new Segment(0, 50, 0, 0), 3)),
                COARSE_WELD,
                NOTHING_LAID);

            assertThat(measureBoundedAreas(faces))
                .hasSize(1);
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

    // The one piece of a given size, insisted on rather than picked out: two pieces of the
    // same area would make whichever came first stand for both.
    private static Face findPieceOfArea(List<Face> faces, double area) {

        var matching = collectBoundedFaces(faces).stream()
            .filter(piece -> Math.abs(piece.measureArea() - area) < AREA_SLACK)
            .toList();

        assertThat(matching)
            .as("one piece covering %.0f", area)
            .hasSize(1);

        return matching.get(0);
    }

    private static List<Face> collectBoundedFaces(List<Face> faces) {

        return faces.stream().filter(face -> !face.isOuterFace()).toList();
    }

    // A face's labels in order of value, since which edge the walk started from decides where
    // the ring begins and the assertion is about which lines bound the piece, not where.
    private static int[] sortLabels(Face face) {

        var labels = face.edgeLabels();

        Arrays.sort(labels);
        return labels;
    }

    // A closed outline as its sides, each under its own label, which is how a ring goes into
    // the walk.
    private static List<LabelledWall> layRingSides(List<double[]> corners, int[] labels) {

        var sides = new ArrayList<LabelledWall>();

        for (var index = 0; index < corners.size(); index++) {

            sides.add(new LabelledWall(
                Segment.joinPoints(corners.get(index), corners.get((index + 1) % corners.size())),
                labels[index]));
        }
        return List.copyOf(sides);
    }

    @SafeVarargs
    private static List<LabelledWall> joinWalls(List<LabelledWall>... groups) {

        var joined = new ArrayList<LabelledWall>();

        for (var group : groups) {
            joined.addAll(group);
        }
        return joined;
    }
}
