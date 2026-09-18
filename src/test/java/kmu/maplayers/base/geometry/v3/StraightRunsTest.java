package kmu.maplayers.base.geometry.v3;

import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit coverage for where a run along a wall lands when the wall's mouth is a point.
 *
 * <p>Two discs and a wall between them, small enough that every angle below can be worked out
 * by hand: the pinned end is the mark's own end, and the free end is the place on the other
 * mark's frontage nearest its middle that the pinned point can see.
 *
 * <p>The two directions are two fixtures rather than one run read backwards. A run is only
 * accepted with both cells on its left, which is the side the walk keeps them on, so a run
 * under the discs goes from the near one to the far one and a run over them goes the other
 * way - and a wall that lands ON the line of centres has one cell on its right whichever way
 * the run tilts, so the free end in each fixture is the one off that line.
 */
class StraightRunsTest {

    // How far each disc reaches. A round number with no meaning beyond being the unit the
    // spacing below is chosen against.
    private static final double REACH = 1000;

    // How far apart the two sites sit, centre to centre. Twice the reach plus a gap, so the
    // discs are apart and a wall between them has a side with length.
    private static final double SPACING = 3000;

    // How close an angle has to come to the one worked out by hand.
    private static final double SAME_ANGLE = 1e-9;

    private static final int NEAR = 0;
    private static final int FAR = 1;

    // The near disc at the origin, the far one along the x axis.
    private static final DiscUnion UNION = new DiscUnion(
        List.of(new double[] {0, 0}, new double[] {SPACING, 0}), REACH);

    // The run UNDER the discs, near to far. The wall lands on the near disc thirty degrees
    // below the line of centres and on the far disc exactly on it; the near stretch ENDS at its
    // anchor, the far stretch BEGINS at its own - a wall's side joins the far end of one mark to
    // the near end of the next - and each runs on along its disc's underside from there.
    private static final double NEAR_ANCHOR = -Math.PI / 6;
    private static final double FAR_ANCHOR = Math.PI;

    private static final CoastMark NEAR_MARK =
        new CoastMark(NEAR, -Math.PI / 2, NEAR_ANCHOR);
    private static final CoastMark FAR_MARK =
        new CoastMark(FAR, FAR_ANCHOR, 3 * Math.PI / 2);

    // Seen from the near anchor, about 2.19 reaches from the far centre, the far disc shows
    // just under 63 degrees of arc either side of the point facing the anchor - a window from
    // about 130 to 256 degrees - and the far stretch's own middle at 225 lies inside it. The
    // free end therefore stays at the middle.
    private static final double FAR_MIDDLE = 5 * Math.PI / 4;

    // The run OVER the discs, far to near: the same wall reflected through the line of centres
    // and walked the other way, so the pinned end is now the arrival. The far stretch ends at
    // its anchor on the line of centres and the near stretch begins at its own, thirty degrees
    // above it.
    private static final double NEAR_TOP_ANCHOR = Math.PI / 6;
    private static final double FAR_TOP_ANCHOR = Math.PI;

    private static final CoastMark FAR_TOP_MARK =
        new CoastMark(FAR, Math.PI / 2, FAR_TOP_ANCHOR);
    private static final CoastMark NEAR_TOP_MARK =
        new CoastMark(NEAR, NEAR_TOP_ANCHOR, Math.PI / 2);

    // Seen from the near anchor above the line, the far disc's window runs from about 104 to
    // 230 degrees, and the far stretch's middle at 135 lies inside it.
    private static final double FAR_TOP_MIDDLE = 3 * Math.PI / 4;

    // The anchor the map is drawn under, so these pin the placement a reader is looking at.
    // The other answer moves where a free end lands, which is what three of these measure.
    private static final StraightRuns.ReachAnchor SHIPPED_ANCHOR =
        StraightRuns.ReachAnchor.AT_THE_STRETCH_START;

    @Nested
    class FindEdgeThroughMouth {

        @Test
        void aRunFromAPointMouthLeavesFromTheAnchor() {
            // The whole point of the placement: the departure is the mark's end, where the
            // wall lands, and not the clamp's choice.
            var edge = StraightRuns.findEdgeThroughMouth(
                new StraightRuns.StraightRun(UNION, NEAR_MARK, FAR_MARK), true, false, SHIPPED_ANCHOR);

            assertThat(edge.departAngle())
                .isCloseTo(NEAR_ANCHOR, within(SAME_ANGLE));
        }

        @Test
        void aRunFromAPointMouthLandsWhereTheFarCellIsSeenFromTheAnchor() {
            // The other end keeps a free run's freedom rather than the wall's far mouth, so the
            // coast touches the wall at the anchor and leaves it again.
            var edge = StraightRuns.findEdgeThroughMouth(
                new StraightRuns.StraightRun(UNION, NEAR_MARK, FAR_MARK), true, false, SHIPPED_ANCHOR);

            assertThat(edge.arriveAngle())
                .isCloseTo(FAR_MIDDLE, within(SAME_ANGLE));
        }

        @Test
        void aRunIntoAPointMouthLandsOnTheAnchor() {
            // Mirrored: pinned at the arrival.
            var edge = StraightRuns.findEdgeThroughMouth(
                new StraightRuns.StraightRun(UNION, FAR_TOP_MARK, NEAR_TOP_MARK), false, true, SHIPPED_ANCHOR);

            assertThat(edge.arriveAngle())
                .isCloseTo(NEAR_TOP_ANCHOR, within(SAME_ANGLE));
        }

        @Test
        void aRunIntoAPointMouthLeavesFromWhereTheAnchorSeesTheFarCell() {
            // Mirrored: free at the departure.
            var edge = StraightRuns.findEdgeThroughMouth(
                new StraightRuns.StraightRun(UNION, FAR_TOP_MARK, NEAR_TOP_MARK), false, true, SHIPPED_ANCHOR);

            assertThat(edge.departAngle())
                .isCloseTo(FAR_TOP_MIDDLE, within(SAME_ANGLE));
        }

        @Test
        void aRunBetweenTwoPointMouthsIsTheWallsSide() {
            // Nothing left to place: both ends are anchors.
            var edge = StraightRuns.findEdgeThroughMouth(
                new StraightRuns.StraightRun(UNION, NEAR_MARK, FAR_MARK), true, true, SHIPPED_ANCHOR);

            assertThat(List.of(edge.departAngle(), edge.arriveAngle()))
                .containsExactly(NEAR_ANCHOR, FAR_ANCHOR);
        }

        @Test
        void aRunThatWouldCutAThirdCellGivesWayToTheWallsSide() {
            // A third disc under the free run's path, reaching about 130 units past it. The
            // side is boundary and cannot cut anything, so it is what the run falls back to.
            var crowded = new DiscUnion(
                List.of(
                    new double[] {0, 0},
                    new double[] {SPACING, 0},
                    new double[] {1700, -1500}),
                REACH);

            var edge = StraightRuns.findEdgeThroughMouth(
                new StraightRuns.StraightRun(crowded, NEAR_MARK, FAR_MARK), true, false, SHIPPED_ANCHOR);

            assertThat(List.of(edge.departAngle(), edge.arriveAngle()))
                .containsExactly(NEAR_ANCHOR, FAR_ANCHOR);
        }
    }
}
