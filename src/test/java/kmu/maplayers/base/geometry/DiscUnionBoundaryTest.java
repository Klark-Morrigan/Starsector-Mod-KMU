package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the boundary walk hands back about where a cycle ran.
 *
 * <p>The samples are not the subject. What a hole is drawn from is easy to check by looking at
 * it, while the walk's record of WHICH stretch of which cell it ran along is read by code that
 * never draws anything - so the property worth pinning is that the record and the shape are the
 * same walk: the stretches named are stretches this hole actually has, and they stand in the
 * order the rest of the hole is described in.
 *
 * <p>Three discs rather than a sector, because the question is about one cycle and a fixture
 * with one hole in it is a fixture whose answer a reader can hold in their head.
 */
class DiscUnionBoundaryTest {

    // How far each disc reaches. A round number with no meaning beyond being the unit the
    // spacing below is chosen against.
    private static final double REACH = 100;

    // How far apart the three sites sit. Inside twice the reach, so each pair of discs overlaps
    // and the three form one connected shape; outside the reach's root-three multiple, so the
    // triangle's circumcentre stays beyond every disc and the middle is left uncovered. That
    // window is the whole of what makes this fixture ring exactly one hole.
    private static final double SPACING = 190;

    // Sides of the polygon the arcs are flattened onto. The shipped default is far finer than
    // a hole this size needs; a smaller count keeps the outline short enough to read in a
    // failure message without changing which arcs the walk finds, since the flattening moves
    // samples rather than boundary.
    private static final int BOUND_SEGMENTS = 64;

    // Where along a mark's sweep the void is probed. Off both ends, because an end is a
    // crossing where two circles meet and so lies on the neighbouring disc by definition -
    // asked there, every mark would look covered.
    private static final double[] PROBE_SHARES = {0.1, 0.3, 0.5, 0.7, 0.9};

    // How far outside its own circle a probe is pushed, as a multiple of the reach. A point
    // exactly on a circle is inside it or outside it by rounding alone, so a probe that stays
    // on the line answers about the arithmetic rather than about the map.
    private static final double PROBE_NUDGE = 1.001;

    // How far two points may sit apart and still be the same corner. Both come out of one
    // cosine and sine of one angle, so anything above rounding is a different point.
    private static final double SAME_POINT = 1e-6;

    // Three sites at the corners of an equilateral triangle, which is the arrangement the
    // spacing was chosen for.
    private static final DiscUnion UNION = new DiscUnion(
        List.of(
            new double[] {0, 0},
            new double[] {SPACING, 0},
            new double[] {SPACING / 2, SPACING * Math.sqrt(3) / 2}),
        REACH);

    @Nested
    class TraceHoles {

        @Test
        void aHoleNamesOneStretchOfBorderPerCellItRunsAlong() {
            // Three discs ringing one hole meet it on one arc each, so the marks are the three
            // cells and nothing else. A count that drifts from the arcs walked is the fault
            // this guards: marks built anywhere but in the walk cannot say which stretch was
            // taken, only which cell was somewhere near.
            var hole = traceTheOnlyHole();

            assertThat(hole.marks())
                .extracting(DiscUnionBoundary.CoastMark::circle)
                .containsExactlyInAnyOrder(0, 1, 2);

            assertThat(hole.ringing()).containsExactlyInAnyOrder(0, 1, 2);
        }

        @Test
        void aMarkedStretchIsVoidAlongItsWholeSweep() {
            // What makes a stretch part of THIS hole is that no other disc covers it. A mark
            // naming the whole of its circle, or a stretch on the far side of its cell, would
            // pass every count and describe border the hole never touched.
            var hole = traceTheOnlyHole();

            for (var mark : hole.marks()) {
                for (var share : PROBE_SHARES) {

                    var probe = findProbeAlong(mark, share);

                    assertThat(findCoveringSite(probe))
                        .as("mark on cell %d, %.0f%% along its sweep", mark.circle(), share * 100)
                        .isNull();
                }
            }
        }

        @Test
        void aHolesCornersStandWhereItsMarksBegin() {
            // The marks and the corners are one walk described twice, so they line up by index
            // or they are describing different journeys round the same hole. Worth pinning
            // because the boundary beside them is deliberately NOT in walk order - it is
            // reversed to wind like a filled shape - and reversing the wrong one of the three
            // is a fault nothing downstream would report as anything but wrong geometry.
            var hole = traceTheOnlyHole();

            assertThat(hole.corners()).hasSameSizeAs(hole.marks());

            for (var index = 0; index < hole.marks().size(); index++) {

                var mark = hole.marks().get(index);

                assertThat(Points.computeDistance(
                        hole.corners().get(index),
                        findPointOnCircle(mark.circle(), mark.fromAngle())))
                    .as("corner %d against the start of its mark on cell %d", index, mark.circle())
                    .isLessThan(SAME_POINT);
            }
        }
    }

    // The one hole the fixture rings, insisted on rather than picked out of what came back: a
    // run that found two of them is a different fixture from the one every assertion is about.
    private static VoidHole traceTheOnlyHole() {

        var holes = DiscUnionBoundary.traceHoles(UNION, BOUND_SEGMENTS);

        assertThat(holes)
            .as("three discs at this spacing ring exactly one hole")
            .hasSize(1);

        return holes.get(0);
    }

    // Which site's disc holds a point, or null where it lies in the void. Measured here rather
    // than asked of the union, so that what a mark claims is checked against the sites
    // themselves instead of against the same reading the walk was built on.
    private static Integer findCoveringSite(double[] point) {

        for (var site = 0; site < UNION.sites().size(); site++) {

            if (Points.computeDistance(point, UNION.sites().get(site)) < REACH) {
                return site;
            }
        }
        return null;
    }

    private static double[] findProbeAlong(DiscUnionBoundary.CoastMark mark, double share) {

        var angle = mark.fromAngle() + share * (mark.toAngle() - mark.fromAngle());
        var site = UNION.sites().get(mark.circle());

        return new double[] {
            site[0] + REACH * PROBE_NUDGE * Math.cos(angle),
            site[1] + REACH * PROBE_NUDGE * Math.sin(angle)};
    }

    private static double[] findPointOnCircle(int circle, double angle) {

        var site = UNION.sites().get(circle);

        return new double[] {
            site[0] + REACH * Math.cos(angle),
            site[1] + REACH * Math.sin(angle)};
    }
}
