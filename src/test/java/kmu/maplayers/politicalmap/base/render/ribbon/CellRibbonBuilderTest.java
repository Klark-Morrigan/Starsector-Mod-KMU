package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>Two cases are about the band being one shape rather than a row of them. Where two runs meet
 * on a corner of the cell, the band turns through that corner instead of stopping square either
 * side of it - the difference between stroking the whole band once and stroking it a run at a
 * time, and invisible to every other case here. And a run of no length lays nothing at all, with
 * the runs after it keeping their own colours: each piece of band carries the run it came from,
 * so what is drawn and what colours it cannot fall out of step however many pieces a run takes.
 *
 * <p>The remaining cases are the two ends of the size question the design answers deliberately:
 * a band longer than its cell's outline compresses rather than being cut short, and a cell with
 * no room for a band at all draws none rather than one crushed against its own border.
 *
 * <p>The names' cases are the same question asked of the room a cluster name takes: the band
 * stops where a name starts and resumes past it, a run interrupted that way keeps its whole
 * length across the two pieces rather than being cut short by the interruption, and a cell whose
 * ring is wholly under a name draws nothing.
 */
final class CellRibbonBuilderTest {

    // A cell four thousand units across, the scale a real cell is cut at.
    private static final List<double[]> SQUARE_CELL = List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0});

    // A cell far too small for the band asked of it: the pad and half width together reach
    // further in than the cell's own half width, so there is no inside left to run along.
    private static final List<double[]> TINY_CELL = List.of(
        new double[] {0.0, 0.0},
        new double[] {500.0, 0.0},
        new double[] {500.0, 500.0},
        new double[] {0.0, 500.0});

    // The cell's own site, the point the band's start is found above.
    private static final double[] CELL_SITE = new double[] {2000.0, 2000.0};

    // A map whose names are all somewhere else, which is the state most cells are in: the band
    // has its cell's whole ring to itself.
    private static final List<List<double[]>> NO_NAMES = List.of();

    // A name lying across the cell's top edge, 400 to 800 along the band's path from its start
    // above the site - so the band meets it a run in and is clear of it a run later.
    private static final List<List<double[]>> NAME_ACROSS_THE_TOP_EDGE = List.of(List.of(
        new double[] {2400.0, 3400.0},
        new double[] {2800.0, 3400.0},
        new double[] {2800.0, 3800.0},
        new double[] {2400.0, 3800.0}));

    // A name across the whole cell - a long name over a small cluster, which leaves its cell's
    // ring with no stretch clear anywhere.
    private static final List<List<double[]>> NAME_ACROSS_THE_WHOLE_CELL = List.of(List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0}));

    // Round numbers rather than the shipped sizes, so the expected coordinates below are the
    // convention being pinned and not an echo of whatever the defaults happen to be: a 400-wide
    // band 200 clear of the border runs its centreline exactly 400 inside the cell.
    private static final RibbonStyle STYLE = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        new RibbonSegmentLengths(3, 1));

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // One width of run - the shortest a segment can be, and short enough here to sit wholly on
    // the cell's top edge, so what it strokes is a plain quad with no corner in it.
    private static final int ONE_WIDTH = 1;

    // Two widths of run, which is longer than the stretch of ring the name below leaves before
    // it - so a run of this length has to be drawn in two pieces or lose half of itself.
    private static final int TWO_WIDTHS = 2;

    // A plan far longer than any cell's outline can hold at full size: sixty runs of three widths
    // is 24000 units of band around an outline of 12800.
    private static final int CROWDED_RUN_COUNT = 60;
    private static final int CROWDED_RUN_LENGTH = 3;

    // A run of no length at all - a plan can carry one, since a run's length is a plain count of
    // widths with nothing forbidding zero. It draws nothing, and what it must not do is take the
    // colour off the runs after it.
    private static final int NO_WIDTHS = 0;

    // Four widths of run is 1600 units, exactly the distance from the band's start above the
    // cell's site to the cell's top right corner - so a plan of these puts a run boundary on that
    // corner, which is the case the band has to turn through rather than butt at.
    private static final int RUN_REACHING_THE_CORNER = 4;

    // How near a stroked corner has to land to be that corner. The band's coordinates come out of
    // rail intersections, so they carry a rounding whisker rather than being stated arithmetic.
    private static final double CORNER_SLACK = 0.01;

    @Nested
    class BuildCellRibbon {

        @Test
        void drawsNoBandWhereTheCellPlannedNone() {
            // The single-holder cell: nothing was planned, so nothing is laid out - and no ring is
            // traced for it either.
            assertThat(CellRibbonBuilder.buildCellRibbon(
                    SQUARE_CELL,
                    CELL_SITE,
                    RibbonPlan.NONE,
                    STYLE,
                    NO_NAMES))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void startsAtTheCellsTopCentreAndRunsClockwiseInsideItsBorder() {
            // The convention in full. The cell's top edge is at y=4000 and the band's centreline
            // runs 400 inside it, so the run spans y from 3400 to 3800 - its outer edge the pad's
            // 200 clear of the border. It starts above the cell's site at x=2000 and runs to
            // x=2400, which is clockwise on a screen where y points up.
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES);

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
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, ONE_WIDTH),
                    new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES);

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

            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(crowdedRuns),
                STYLE,
                NO_NAMES);

            assertThat(ribbon.bands())
                .hasSize(CROWDED_RUN_COUNT)
                .allSatisfy(band -> assertThat(band.triangles()).isNotEmpty());
        }

        @Test
        void keepsEveryRunsOwnColourWhereARunInTheMiddleDrawsNothing() {
            // A run drawing nothing lays no piece, and the runs after it are unaffected: each
            // piece carries the run it came from, so dropping one shifts no other run's colour.
            // The last band's far end at x=2800 is what says the geometry stayed with its colour
            // rather than both shifting together.
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, NO_WIDTHS),
                    new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NO_NAMES);

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
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, RUN_REACHING_THE_CORNER),
                    new RibbonSegment(DARK, RUN_REACHING_THE_CORNER))),
                STYLE,
                NO_NAMES);

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
        void breaksTheBandWhereANameLiesAcrossTheRing() {
            // The name covers the ring from 400 to 800 along, so the first run fills the stretch
            // up to it and the second begins on the far side rather than under the word. What
            // makes this the carve and not a coincidence is the far end of the second run: at
            // 3200 it is one full run past where the name ends, so nothing of it was eaten.
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(
                    new RibbonSegment(BRIGHT, ONE_WIDTH),
                    new RibbonSegment(DARK, ONE_WIDTH))),
                STYLE,
                NAME_ACROSS_THE_TOP_EDGE);

            assertThat(ribbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BRIGHT, DARK);

            assertThat(hasCorner(ribbon.bands().get(0), 2400.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(1), 2800.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(1), 3200.0, 3800.0))
                .isTrue();
        }

        @Test
        void keepsAnInterruptedRunsWholeLengthAcrossThePiecesItDrawsAs() {
            // A single run meeting a name: it carries on past it rather than stopping there, so
            // the two pieces together are as long as the run would have been. A run's length is
            // what says how many colonies a bloc holds, so a run cut short by a word would say
            // something false about the system - which is why the names carve the ring and not
            // the plan.
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, TWO_WIDTHS))),
                STYLE,
                NAME_ACROSS_THE_TOP_EDGE);

            assertThat(ribbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BRIGHT, BRIGHT);

            // 400 of the run before the name and 400 after it: the second piece reaches 3200,
            // where a run cut short at the name would have stopped at 2800.
            assertThat(hasCorner(ribbon.bands().get(0), 2400.0, 3800.0))
                .isTrue();
            assertThat(hasCorner(ribbon.bands().get(1), 3200.0, 3800.0))
                .isTrue();
        }

        @Test
        void leavesAStretchOfRingBareWhereThePlanRanOutBeforeReachingIt() {
            // The band stops where the plan does, not where the ring does. This one run fills
            // exactly the stretch up to the name, so the long stretch beyond it carries nothing -
            // and a pass that stroked a stretch it laid no runs on would put an uncoloured band
            // round most of the cell.
            var ribbon = CellRibbonBuilder.buildCellRibbon(
                SQUARE_CELL,
                CELL_SITE,
                new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                STYLE,
                NAME_ACROSS_THE_TOP_EDGE);

            assertThat(ribbon.bands())
                .singleElement()
                .satisfies(band -> assertThat(hasCorner(band, 2400.0, 3800.0)).isTrue());
        }

        @Test
        void drawsNoBandOnACellWhoseRingIsWhollyUnderAName() {
            // A long name over a small cluster. There is no stretch of ring left to state the
            // plan on at any size, so the cell says nothing rather than squeezing a smear of
            // colour into whatever slivers remain.
            assertThat(CellRibbonBuilder.buildCellRibbon(
                    SQUARE_CELL,
                    CELL_SITE,
                    new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                    STYLE,
                    NAME_ACROSS_THE_WHOLE_CELL))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void drawsNoBandOnACellWithNoRoomToHoldOne() {
            // The cell smaller than the pad and width together. The inset of such a ring comes
            // back tidy and correctly wound while being no inset at all, so the answer here is
            // the one the trace measures rather than the one its shape suggests.
            assertThat(CellRibbonBuilder.buildCellRibbon(
                    TINY_CELL,
                    CELL_SITE,
                    new RibbonPlan(List.of(new RibbonSegment(BRIGHT, ONE_WIDTH))),
                    STYLE,
                    NO_NAMES))
                .isEqualTo(CellRibbon.NONE);
        }
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
