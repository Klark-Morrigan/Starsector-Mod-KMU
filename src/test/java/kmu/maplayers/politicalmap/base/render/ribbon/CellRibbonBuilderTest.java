package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.CELL_NARROWER_THAN_THE_BAND;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.CELL_NARROWER_THAN_THE_BAND_SITE;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.CELL_TOO_NARROW_FOR_THE_PAD;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.CELL_TOO_NARROW_FOR_THE_PAD_SITE;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.NECKED_CELL;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.SQUARE_CELL;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.SQUARE_CELL_SITE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins where a cell's band is laid and how far round it reaches: the inset it runs at, the corner
 * it starts from, the direction it travels, and what happens on a cell that cannot hold it.
 *
 * <p>The first case states all four at once, and does so in literal coordinates, because every
 * one of them is a convention rather than a derivation - a band that started at the bottom, ran
 * anticlockwise, or sat on the border instead of inside it would be no less "correct" as
 * geometry and completely wrong as a readout. Pinning the four together is what makes the
 * convention a fact about the code rather than about whichever cell was looked at in play.
 *
 * <p>Each case walks its own cell's ring through the very class a pass walks through, rather than
 * hand-building a path. What is under test here is what a band does with the room a real ring
 * offers it, and a path assembled for a case would be room the cases agreed among themselves to
 * have - which is exactly the agreement a cell in play is not party to.
 *
 * <p>Two cases are about the band being one shape rather than a row of them. Where two runs meet
 * on a corner of the cell, the band turns through that corner instead of stopping square either
 * side of it - the difference between stroking the whole band once and stroking it a run at a
 * time, and invisible to every other case here. And a run of no length lays nothing at all, with
 * the runs after it keeping their own colours: a run that strokes to nothing drops out of what is
 * drawn without the runs behind it sliding onto each other's colours as the gap closes.
 *
 * <p>The remaining cases are the ends of the size question the design answers deliberately: a
 * band longer than its cell's outline compresses rather than being cut short, compressing itself
 * stops where a width would come to nothing, and a cell with no room for a band at all draws none
 * rather than one crushed against its own border.
 *
 * <p>The names' cases are the same question asked of the room a cluster name takes: the whole
 * band goes on the longest stretch the names leave and the rest of the ring stays bare, the clamp
 * measures against that one stretch rather than against every stretch together, the band sits as
 * near the cell's top centre as that stretch allows rather than wherever the surviving ring
 * happens to open, and a cell whose ring is wholly under a name draws nothing.
 *
 * <p>The forcing cases are the answer to a cell whose ring has no room, and they are as much about
 * where that answer stops: the clearance gives way only where it left nothing, the pad gives way
 * only as far as the half width, and a cell narrower than the band still draws none.
 *
 * <p>A neck is the same question asked of the cell's own shape rather than of the names, and the
 * two cases are the two halves of its answer: a cell pinched in one place lays its band on the
 * ring the neck leaves instead of refusing the whole cell, and forcing does not give that neck
 * back - a band run through it would hang outside the cell it reports on, which is the one thing
 * neither the clearance nor the pad is ever allowed to cost.
 *
 * <p>Three of them are the same rule about the path's own start, which the carve states as two
 * ends rather than as the one point it is. A stretch reaching both ends is one stretch and is
 * read as one; a stretch reaching only one of them is not, whichever end it reaches. The
 * distinction is invisible on a cell with no name near its start and decides where the band goes
 * on every cell with one, so all three are posed rather than the first alone.
 */
final class CellRibbonBuilderTest {

    // A map whose names are all somewhere else, which is the state most cells are in: the band
    // has its cell's whole ring to itself.
    private static final List<List<double[]>> NO_NAMES = List.of();

    // A name lying across the cell's top edge, 400 to 800 along the band's path from its start
    // above the site - so the band meets it a run in.
    private static final List<double[]> NAME_ACROSS_THE_TOP_EDGE = List.of(
        new double[] {2400.0, 3400.0},
        new double[] {2800.0, 3400.0},
        new double[] {2800.0, 3800.0},
        new double[] {2400.0, 3800.0});

    // A second name on the same edge but 11800 to 12200 along - a thousand short of the band's
    // start, which the path reaches at 12800. The two together leave the ring in three stretches:
    // a long one between them, and the two short ones either side of the start that are one
    // stretch straddling it.
    private static final List<double[]> NAME_JUST_BEFORE_THE_START = List.of(
        new double[] {1000.0, 3400.0},
        new double[] {1400.0, 3400.0},
        new double[] {1400.0, 3800.0},
        new double[] {1000.0, 3800.0});

    // A name across the cell's bottom edge, 6000 to 6400 along - the far side of the ring from
    // the band's start, so what it leaves clear is one stretch running from it round past that
    // start and back again. The carve states that stretch as two intervals, one at each end of
    // the path, and it is the longest thing on the cell only once they are read as one.
    private static final List<double[]> NAME_ACROSS_THE_BOTTOM_EDGE = List.of(
        new double[] {2000.0, 200.0},
        new double[] {2400.0, 200.0},
        new double[] {2400.0, 600.0},
        new double[] {2000.0, 600.0});

    // A name lying over the band's own start, covering the path from 12600 round through the
    // start and on to 200. The two stretches it leaves reach the path's two ends without
    // meeting there, so a cell carrying this name has nothing at its start to fuse.
    private static final List<double[]> NAME_ACROSS_THE_START = List.of(
        new double[] {1800.0, 3400.0},
        new double[] {2200.0, 3400.0},
        new double[] {2200.0, 3800.0},
        new double[] {1800.0, 3800.0});

    // A name ending exactly on the band's start, covering 12400 to 12800. The stretch opening the
    // path and the stretch closing it are the two the fuse joins on any other cell, and here they
    // are separated by this name rather than being one stretch stated twice.
    private static final List<double[]> NAME_ENDING_AT_THE_START = List.of(
        new double[] {1600.0, 3400.0},
        new double[] {2000.0, 3400.0},
        new double[] {2000.0, 3800.0},
        new double[] {1600.0, 3800.0});

    private static final List<List<double[]>> NAMES_EITHER_SIDE_OF_THE_START =
        List.of(NAME_ACROSS_THE_TOP_EDGE, NAME_JUST_BEFORE_THE_START);

    private static final List<List<double[]>> NAMES_OVER_THE_START_AND_THE_BOTTOM_EDGE =
        List.of(NAME_ACROSS_THE_START, NAME_ACROSS_THE_BOTTOM_EDGE);

    private static final List<List<double[]>> NAMES_UP_TO_THE_START_AND_THE_TOP_EDGE =
        List.of(NAME_ENDING_AT_THE_START, NAME_ACROSS_THE_TOP_EDGE);

    // A name across the whole cell - a long name over a small cluster, which leaves its cell's
    // ring with no stretch clear anywhere.
    private static final List<List<double[]>> NAME_ACROSS_THE_WHOLE_CELL = List.of(List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0}));

    // Two names covering the whole cell but for a hairline between them at x=1000, which the ring
    // crosses on its top edge and again on its bottom. Each crossing leaves a clear stretch of a
    // ten-thousandth of a unit: a hundred times the shortest arc the carve keeps, and so a stretch
    // that survives being carved while being nowhere near enough ring to state a plan along.
    private static final List<List<double[]>> NAMES_LEAVING_ONLY_A_HAIRLINE = List.of(
        List.of(
            new double[] {0.0, 0.0},
            new double[] {1000.0, 0.0},
            new double[] {1000.0, 4000.0},
            new double[] {0.0, 4000.0}),
        List.of(
            new double[] {1000.0001, 0.0},
            new double[] {4000.0, 0.0},
            new double[] {4000.0, 4000.0},
            new double[] {1000.0001, 4000.0}));

    // Round numbers rather than the shipped sizes, so the expected coordinates below are the
    // convention being pinned and not an echo of whatever the defaults happen to be: a 400-wide
    // band 200 clear of the border runs its centreline exactly 400 inside the cell.
    // Forcing off, so the cases below state what a cell's own ring allows rather than what the
    // fallbacks recover. The two cases that are about the fallbacks say so by taking the style
    // beneath this one.
    private static final RibbonStyle STYLE = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        new RibbonSegmentLengths(3, 1),
        false);

    // The same sizes with the shipped answer to a cell that has no room: draw the band anyway.
    private static final RibbonStyle STYLE_FORCING_A_BAND = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        new RibbonSegmentLengths(3, 1),
        true);

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // One width of run - the shortest a segment can be, and short enough here to sit wholly on
    // the cell's top edge, so what it strokes is a plain quad with no corner in it.
    private static final int ONE_WIDTH = 1;

    // Thirty widths of run - 12000 units at full size, longer than any stretch the names above
    // leave. A run this long is compressed to exactly fill the stretch it is laid on, so where it
    // ends names that stretch, which is what tells one reading of the carved ring from another.
    private static final int RUN_OUTRUNNING_THE_STRETCH = 30;

    // A plan far longer than any cell's outline can hold at full size: sixty runs of three widths
    // is 24000 units of band around an outline of 12800.
    private static final int CROWDED_RUN_COUNT = 60;
    private static final int CROWDED_RUN_LENGTH = 3;

    // A run of no length at all - a plan can carry one, since a run's length is a plain count of
    // widths with nothing forbidding zero. It draws nothing, and what it must not do is take the
    // colour off the runs after it.
    private static final int NO_WIDTHS = 0;

    // A thousand widths of run. Against a hairline of ring it compresses one width to a
    // ten-millionth of a unit, which is where compressing stops being a readout and starts being
    // a smear - a scale no plan a sector produces reaches, and the point of stating it here is
    // that the floor answers it rather than that a system could plan it.
    private static final int RUN_NO_HAIRLINE_CAN_STATE = 1000;

    // Four widths of run is 1600 units, exactly the distance from the band's start above the
    // cell's site to the cell's top right corner - so a plan of these puts a run boundary on that
    // corner, which is the case the band has to turn through rather than butt at.
    private static final int RUN_REACHING_THE_CORNER = 4;

    // How near a stroked corner has to land to be that corner. The band's coordinates come out of
    // rail intersections, so they carry a rounding whisker rather than being stated arithmetic.
    private static final double CORNER_SLACK = 0.01;

    // Where a bake charges what a cell cost it. Handed in and never read back here: a band's
    // phases are summed per pass, while what these cases pin is the band one cell comes out with.
    private final RibbonBakeTimings passTimings = new RibbonBakeTimings();

    @Nested
    class BuildCellRibbon {

        @Test
        void chargesTheCellsCarveAndStrokeToThePass() {
            // What a bake is measured by, and it only means anything if the phases are actually
            // charged: two numbers nothing writes to would read as a bake that costs nothing. The
            // trace is not among them - the path arrives already walked, and what walking it cost
            // is charged where that happened.
            var timingsMock = mock(RibbonBakeTimings.class);

            bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES,
                timingsMock);

            verify(timingsMock).addCarveNanos(anyLong());
            verify(timingsMock).addStrokeNanos(anyLong());
            verify(timingsMock, never()).addTraceNanos(anyLong());
        }

        @Test
        void chargesTheCarveOfACellItLeftNoRoomToLayABandOn() {
            // A refusal is not free, and the cells the carve turns down are the ones whose carve
            // is most worth knowing the cost of: charged for the ring it walked, and for no
            // stroke it never reached.
            var timingsMock = mock(RibbonBakeTimings.class);

            bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NAME_ACROSS_THE_WHOLE_CELL,
                timingsMock);

            verify(timingsMock).addCarveNanos(anyLong());
            verify(timingsMock, never()).addStrokeNanos(anyLong());
        }

        @Test
        void startsAtTheCellsTopCentreAndRunsClockwiseInsideItsBorder() {
            // The convention in full. The cell's top edge is at y=4000 and the band's centreline
            // runs 400 inside it, so the run spans y from 3400 to 3800 - its outer edge the pad's
            // 200 clear of the border. It starts above the cell's site at x=2000 and runs to
            // x=2400, which is clockwise on a screen where y points up.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(band.colour()).isEqualTo(BRIGHT);
                    assertThat(band.triangles()).containsExactly(
                        2000.0f, 3800.0f,
                        2000.0f, 3400.0f,
                        2400.0f, 3400.0f,
                        2000.0f, 3800.0f,
                        2400.0f, 3400.0f,
                        2400.0f, 3800.0f);
                });
        }

        @Test
        void laysEachPlannedRunAsItsOwnBandInPlanOrder() {
            // Runs are baked one patch of triangles each, in the order planned, so the renderer
            // paints a band's colours without re-reading the plan they came from.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, ONE_WIDTH),
                    new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES,
                passTimings);

            assertThat(ribbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BRIGHT, DARK, BRIGHT);
        }

        @Test
        void compressesACrowdedBandRatherThanCuttingItShort() {
            // A market-dense system plans more band than its cell has outline. Every run still
            // draws - the whole point of compressing what a width is worth rather than truncating
            // is that no bloc drops off the readout - so a crowded cell reads as crowded.
            var crowdedRuns = new ArrayList<RibbonSegment>();
            for (var run = 0; run < CROWDED_RUN_COUNT; run++) {
                crowdedRuns.add(new RibbonSegment(BRIGHT, CROWDED_RUN_LENGTH));
            }

            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(crowdedRuns),
                STYLE,
                NO_NAMES,
                passTimings);

            assertThat(ribbon.bands())
                .hasSize(CROWDED_RUN_COUNT)
                .allSatisfy(band -> assertThat(band.triangles()).isNotEmpty());
        }

        @Test
        void keepsEveryRunsOwnColourWhereARunInTheMiddleDrawsNothing() {
            // A run drawing nothing lays no band, and the runs after it are unaffected: a run's
            // colour is read where its own stroke came back, so dropping one shifts no other's.
            // The last band's far end at x=2800 is what says the geometry stayed with its colour
            // rather than both shifting together.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, NO_WIDTHS),
                    new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES,
                passTimings);

            assertThat(ribbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BRIGHT, BRIGHT);

            assertThat(hasCorner(ribbon.bands().get(1), 2800.0, 3800.0))
                .isTrue();
        }

        @Test
        void turnsARunBoundaryThroughACornerOfTheCell() {
            // Two runs meeting exactly on the cell's top right corner, where the band's centreline
            // turns from running east to running south. Stroked a run at a time, the first run
            // would stop square across the corner at (3600,3800) and the second start square at
            // (3800,3600), leaving a wedge of the corner uncovered - which is what the band was
            // pinching to on most of its boundaries, a rounded outline putting a corner every few
            // hundred units. Stroked as one band the boundary takes the corner's own mitre, so the
            // first run reaches the outer mitre at (3800,3800) and both runs meet along the line
            // from there to the inner one at (3400,3400).
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, RUN_REACHING_THE_CORNER),
                    new RibbonSegment(DARK, RUN_REACHING_THE_CORNER))),
                STYLE,
                NO_NAMES,
                passTimings);

            assertThat(hasCorner(ribbon.bands().get(0), 3800.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(0), 3400.0, 3400.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(0), 3600.0, 3800.0))
                .isFalse();
            assertThat(hasCorner(ribbon.bands().get(1), 3800.0, 3800.0))
                .isTrue();
        }

        @Test
        void laysTheWholeBandOnTheLongestStretchTheNamesLeave() {
            // Two names cut the ring into a long stretch and a short one. The whole band goes on
            // the long one, in plan order from its opening at 800 - the top centre lies off that
            // stretch, and its opening is the nearer of the stretch's two ends to it - so the
            // first run runs 2800 to 3200 along the cell's top edge and the second carries on to
            // the corner at 3600.
            // The short stretch stays bare, which is what its own start at 1400 says: a band
            // scattered over both stretches would have put a piece there, and a reader cannot
            // tell such a piece from a run of its own.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, ONE_WIDTH))),
                STYLE,
                NAMES_EITHER_SIDE_OF_THE_START,
                passTimings);

            assertThat(ribbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BRIGHT, DARK);

            assertThat(hasCorner(ribbon.bands().get(0), 2800.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(1), 3600.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(0), 1400.0, 3800.0))
                .isFalse();
            assertThat(hasCorner(ribbon.bands().get(1), 1400.0, 3800.0))
                .isFalse();
        }

        @Test
        void readsTheStretchStraddlingTheStartAsOneStretch() {
            // One name on the far side of the ring leaves one clear stretch, and the carve states
            // it as two intervals because it runs through the path's own origin. Read as the one
            // stretch it is, the top centre is a point within it with the whole cell's top edge
            // clockwise of it, so the band opens there and runs east to (2400,3800). Read as two,
            // the longer would be the interval closing the path, whose latest start puts the band
            // 400 earlier - reaching the top centre from (1600,3800) rather than leaving it.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                List.of(NAME_ACROSS_THE_BOTTOM_EDGE),
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 2000.0, 3800.0)).isTrue();
                    assertThat(hasCorner(band, 2400.0, 3800.0)).isTrue();
                    assertThat(hasCorner(band, 1600.0, 3800.0)).isFalse();
                });
        }

        @Test
        void backsTheBandUpToReachTheTopCentreWhereItsStretchClosesTooSoonAfterIt() {
            // The placement rule seen through a cell. The name on the top edge leaves a stretch
            // opening 800 past the top centre and closing 400 after it, and the band reaches 800,
            // so it cannot both open on the top centre and stay clear of the name. It backs up to
            // (1600,3800) and finishes where the name begins at (2400,3800), which keeps the top
            // centre on the band rather than throwing the whole thing round to the stretch's own
            // opening at (2800,3800).
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, ONE_WIDTH))),
                STYLE,
                List.of(NAME_ACROSS_THE_TOP_EDGE),
                passTimings);

            assertThat(hasCorner(ribbon.bands().get(0), 1600.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(1), 2400.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(0), 2800.0, 3800.0))
                .isFalse();
        }

        @Test
        void takesTheLongerStretchWhereANameOverTheStartLeavesNothingToFuse() {
            // The fuse is what a stretch reaching the path's two ends earns, not what reaching one
            // of them does. This name sits on the start itself, so the stretches either side of it
            // end where it begins rather than running into each other, and the band takes the
            // longer of the two: 6400 to 12600, ending at 1800 along the cell's top edge. Fused
            // regardless, the two would read as 12400 of room and the band would run at full size
            // straight through both names, reaching the bottom edge at 2800.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, RUN_OUTRUNNING_THE_STRETCH))),
                STYLE,
                NAMES_OVER_THE_START_AND_THE_BOTTOM_EDGE,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 1800.0, 3800.0)).isTrue();
                    assertThat(hasCorner(band, 2800.0, 600.0)).isFalse();
                });
        }

        @Test
        void takesTheLongerStretchWhereANameEndsOnTheStart() {
            // The same rule read from the other end. The stretch closing the path stops 400 short
            // of it, so the stretch opening the path is a stretch of its own however exactly the
            // two nearly meet - and the band, laid on the longer, ends at 1600 where that name
            // begins. Fused on the strength of the other stretch starting at the origin, it would
            // draw over the name and finish on the start itself at 2000.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, RUN_OUTRUNNING_THE_STRETCH))),
                STYLE,
                NAMES_UP_TO_THE_START_AND_THE_TOP_EDGE,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 1600.0, 3800.0)).isTrue();
                    assertThat(hasCorner(band, 2000.0, 3800.0)).isFalse();
                });
        }

        @Test
        void compressesAgainstTheStretchItLaysOnRatherThanTheRingTheNamesLeaveInTotal() {
            // The run is 12000 units at full size, against a chosen stretch of 11000 and 12000 of
            // clear ring in total. Measured against the ring it would draw at full size and
            // overrun the stretch by a whole name's worth; measured against the stretch it
            // compresses to fit, ending at 1000 along the cell's top edge where the second name
            // begins. The ring the band is not laid on is ring the band does not get to spend.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, RUN_OUTRUNNING_THE_STRETCH))),
                STYLE,
                NAMES_EITHER_SIDE_OF_THE_START,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 1000.0, 3800.0)).isTrue();
                    assertThat(hasCorner(band, 2000.0, 3800.0)).isFalse();
                });
        }

        @Test
        void drawsNoBandOnACellWhoseRingIsWhollyUnderAName() {
            // A long name over a small cluster. There is no stretch of ring left to state the
            // plan on at any size, so the cell says nothing rather than squeezing a smear of
            // colour into whatever slivers remain.
            assertThat(bakeBandIn(
                    SQUARE_CELL,
                    SQUARE_CELL_SITE,
                    new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                    STYLE,
                    NAME_ACROSS_THE_WHOLE_CELL,
                    passTimings))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void drawsNoBandWhereTheRingLeftWouldCompressAWidthToNothing() {
            // Where compressing stops. The carve keeps any stretch longer than its own shortest
            // arc, so a cell can come back with ring to lay a band on and still have nowhere near
            // enough of it: this hairline is a hundred times that floor and a ten-thousandth of
            // one width. Compressed onto it the band would be a discoloured point on the outline,
            // which says less than the bare cell does and reads as a fault rather than as a
            // system with a great deal in it.
            assertThat(bakeBandIn(
                    SQUARE_CELL,
                    SQUARE_CELL_SITE,
                    new RibbonPlan(List.of(new RibbonSegment(BRIGHT, RUN_NO_HAIRLINE_CAN_STATE))),
                    STYLE,
                    NAMES_LEAVING_ONLY_A_HAIRLINE,
                    passTimings))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void laysTheBandOnTheWholeRingWhereTheNamesLeaveNoneAndABandIsForced() {
            // The same cell and the same name as the case above, and the opposite answer, because
            // the two cannot both be honoured: a ring wholly covered leaves keeping clear of the
            // names and drawing the band at all in direct conflict. Forced, the clearance is what
            // gives way, and the band runs from the cell's top centre as though no name were
            // there.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE_FORCING_A_BAND,
                NAME_ACROSS_THE_WHOLE_CELL,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 2000.0, 3800.0)).isTrue();
                    assertThat(hasCorner(band, 2400.0, 3800.0)).isTrue();
                });
        }

        @Test
        void keepsAForcedBandClearOfNamesThatLeaveItRoom() {
            // Forcing answers a ring with no room at all; it is not a way of switching the
            // clearance off. This name leaves most of the ring, so the band is laid exactly where
            // it is without the forcing - backed up to (1600,3800) and stopping where the name
            // begins - rather than running from the top centre at 2000 straight over the name, as
            // it would were the boxes simply dropped whenever the forcing is on.
            var ribbon = bakeBandIn(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, ONE_WIDTH))),
                STYLE_FORCING_A_BAND,
                List.of(NAME_ACROSS_THE_TOP_EDGE),
                passTimings);

            assertThat(hasCorner(ribbon.bands().get(0), 1600.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(1), 2800.0, 3800.0))
                .isFalse();
        }

        @Test
        void drawsNoForcedBandOnACellNarrowerThanTheBandItself() {
            // Where the fallback stops. The pad is what a narrow cell gives up, and this cell is
            // narrower than the band even with the whole pad gone, so there is no shallower trace
            // left to take. A band laid here would hang outside the very cell it reports on, which
            // is worse than the blank the forcing exists to remove.
            assertThat(bakeBandIn(
                    CELL_NARROWER_THAN_THE_BAND,
                    CELL_NARROWER_THAN_THE_BAND_SITE,
                    new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                    STYLE_FORCING_A_BAND,
                    NO_NAMES,
                    passTimings))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void tracesAForcedBandWithoutItsPadOnACellThatCannotHoldBoth() {
            // The cell the case below draws nothing on, forced. Its ring cannot take the pad and
            // the half width together, so the pad is what gives way: the band's outer edge lands
            // on the cell's own border at (250,500) rather than the 200 clear of it the pad asks
            // for. The half width is kept whatever happens, since giving up any of that would hang
            // the band outside the cell it reports on.
            var ribbon = bakeBandIn(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE_FORCING_A_BAND,
                NO_NAMES,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> assertThat(hasCorner(band, 250.0, 500.0)).isTrue());
        }

        @Test
        void laysTheBandOnTheRingANeckLeavesRatherThanRefusingTheWholeCell() {
            // A neck is a stretch of ring, not a verdict on the cell. This one costs the 566
            // units of outline that reach it, leaving 12400 of the cell's 12966 - so a plan of
            // 12000 fits on what is left at full size, opening at (3600,1400) and closing at
            // (3600,2200) where the neck begins again. Refused as a whole, the cell would draw
            // nothing at all and read as a system with nothing to report.
            var ribbon = bakeBandIn(
                NECKED_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, RUN_OUTRUNNING_THE_STRETCH))),
                STYLE,
                NO_NAMES,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 3800.0, 1400.0)).isTrue();
                    assertThat(hasCorner(band, 3800.0, 2200.0)).isTrue();
                    assertThat(computeRightmostReach(band)).isLessThan(3800.0 + CORNER_SLACK);
                });
        }

        @Test
        void keepsAForcedBandOffTheNeckTheNamesCannotGiveBack() {
            // Forcing is the names giving up the ring they cover, and a neck is not a name. The
            // same cell under a name across the whole of it draws the same band as above rather
            // than one running from its top centre straight through the neck - which is what a
            // fallback to the cell's whole outline would lay, hanging the band over the border
            // the half width exists to keep it inside.
            var ribbon = bakeBandIn(
                NECKED_CELL,
                SQUARE_CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, RUN_OUTRUNNING_THE_STRETCH))),
                STYLE_FORCING_A_BAND,
                NAME_ACROSS_THE_WHOLE_CELL,
                passTimings);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> {
                    assertThat(hasCorner(band, 3800.0, 1400.0)).isTrue();
                    assertThat(computeRightmostReach(band)).isLessThan(3800.0 + CORNER_SLACK);
                });
        }

        @Test
        void drawsNoBandOnACellWithNoRoomToHoldOne() {
            // The cell smaller than the pad and width together. The inset of such a ring comes
            // back tidy and correctly wound while being no inset at all, so the answer here is
            // the one the trace measures rather than the one its shape suggests.
            assertThat(bakeBandIn(
                    CELL_TOO_NARROW_FOR_THE_PAD,
                    SQUARE_CELL_SITE,
                    new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                    STYLE,
                    NO_NAMES,
                    passTimings))
                .isEqualTo(CellRibbon.NONE);
        }
    }

    // One cell's band, walked and then laid, which is the pair a pass performs and the reason the
    // two are one call here: the ring is walked at the sizes the band is laid at, and a case naming
    // those sizes twice could name two of them.
    //
    // The walk goes through the very class a pass walks through rather than a path built for the
    // case, since what these cases pin is what a band does with the room a real ring offers - room
    // a hand-built path would be free to agree it had.
    private static CellRibbon bakeBandIn(
            List<double[]> ring,
            double[] topAnchor,
            RibbonPlan plan,
            RibbonStyle style,
            List<List<double[]>> nameBoxes,
            RibbonBakeTimings timings) {

        return CellRibbonBuilder.buildCellRibbon(
            RibbonPathTracer.traceLaidRibbonPath(ring, topAnchor, style),
            plan,
            style,
            nameBoxes,
            timings);
    }

    // How far right a run's triangles reach. A band on the necked cell's right side stands at
    // most half a width out at x=3800; a band that ran through the neck would take the mouth's
    // own mitre and reach past 4000, outside the very cell it reports on. Asserted as a reach
    // rather than as a corner, since where such a band would put its corners is the stroker's
    // arithmetic and what is wrong with it is that it is out there at all.
    private static double computeRightmostReach(RibbonBand band) {

        var rightmost = -Double.MAX_VALUE;

        for (var vertex = 0; vertex + 1 < band.triangles().length; vertex += 2) {
            rightmost = Math.max(rightmost, band.triangles()[vertex]);
        }
        return rightmost;
    }

    // Whether a run turns on the given world point - whether any of its triangles has a corner
    // there. A run's shape is asserted through the corners it reaches, since which of its
    // triangles carries one is the stroker's own business.
    private static boolean hasCorner(RibbonBand band, double x, double y) {

        for (var vertex = 0; vertex + 1 < band.triangles().length; vertex += 2) {

            if (Math.abs(band.triangles()[vertex] - x) < CORNER_SLACK
                    && Math.abs(band.triangles()[vertex + 1] - y) < CORNER_SLACK) {

                return true;
            }
        }
        return false;
    }
}
