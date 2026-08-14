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
 * <p>The remaining cases are the two ends of the size question the design answers deliberately:
 * a band longer than its cell's outline compresses rather than being cut short, and a cell with
 * no room for a band at all draws none rather than one crushed against its own border.
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

    // Round numbers rather than the shipped sizes, so the expected coordinates below are the
    // convention being pinned and not an echo of whatever the defaults happen to be: a 400-wide
    // band 200 clear of the border runs its centreline exactly 400 inside the cell. The screen
    // floor is zero because nothing here is on screen - laying a band out is settled entirely in
    // the world, and how thin it may be drawn is the frame's question.
    private static final RibbonStyle STYLE = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        new RibbonSegmentLengths(3, 1),
        0.0);

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // One width of run - the shortest a segment can be, and short enough here to sit wholly on
    // the cell's top edge, so what it strokes is a plain quad with no corner in it.
    private static final int ONE_WIDTH = 1;

    // A plan far longer than any cell's outline can hold at full size: sixty runs of three widths
    // is 24000 units of band around an outline of 12800.
    private static final int CROWDED_RUN_COUNT = 60;
    private static final int CROWDED_RUN_LENGTH = 3;

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
                    STYLE))
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
                STYLE);

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
                STYLE);

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
                STYLE);

            assertThat(ribbon.bands())
                .hasSize(CROWDED_RUN_COUNT)
                .allSatisfy(band -> assertThat(band.triangles()).isNotEmpty());
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
                    STYLE))
                .isEqualTo(CellRibbon.NONE);
        }
    }
}
