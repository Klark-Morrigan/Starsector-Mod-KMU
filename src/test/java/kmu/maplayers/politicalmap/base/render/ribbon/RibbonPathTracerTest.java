package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ladder both callers walk - the authored inset, then the pad given up - and the one
 * thing they do differently with it.
 *
 * <p>The band pass is pinned at its two ends, since between them sits the whole of what a cell
 * short of room draws: a ring that holds the pad is traced at it, and a ring that does not comes
 * back traced or empty depending on the player's answer alone.
 *
 * <p>The overlay is pinned on the case that is its entire reason for existing: a cell refused for
 * want of the pad still hands back the path it was refused on. Nothing on the map says so
 * otherwise - the cell simply draws nothing, exactly like a cell with nothing to report - so a
 * trace that came back empty here would leave the two indistinguishable and the overlay pointless.
 *
 * <p>Both are stated in literal coordinates, because the start is a convention rather than a
 * derivation: a path traced at the right inset but opened at the wrong point of the ring is no
 * less correct as geometry and completely wrong as the mark of where a band begins.
 */
final class RibbonPathTracerTest {

    // A cell four thousand units across, the scale a real cell is cut at, and its own site.
    private static final List<double[]> SQUARE_CELL = List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0});

    private static final double[] SQUARE_CELL_SITE = new double[] {2000.0, 2000.0};

    // A cell with no room for the pad and the half width together, but room to spare for the half
    // width on its own - the narrow neck the fallback exists for.
    private static final List<double[]> CELL_TOO_NARROW_FOR_THE_PAD = List.of(
        new double[] {0.0, 0.0},
        new double[] {500.0, 0.0},
        new double[] {500.0, 500.0},
        new double[] {0.0, 500.0});

    private static final double[] NARROW_CELL_SITE = new double[] {250.0, 250.0};

    // A cell narrower than the band is wide. The pad given up entirely still leaves the half width
    // nowhere to go, so there is no shallower trace to fall back to.
    private static final List<double[]> CELL_NARROWER_THAN_THE_BAND = List.of(
        new double[] {0.0, 0.0},
        new double[] {300.0, 0.0},
        new double[] {300.0, 300.0},
        new double[] {0.0, 300.0});

    private static final double[] NARROWEST_CELL_SITE = new double[] {150.0, 150.0};

    // Round numbers rather than the shipped sizes, so the coordinates below pin the conventions
    // and not whatever the defaults happen to be: a 400-wide band 200 clear of the border runs its
    // centreline 400 inside the cell, and 200 inside it once the pad is given up.
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

    @Nested
    class TraceLaidRibbonPath {

        @Test
        void traceLaidRibbonPathTracesTheRingAtTheAuthoredInset() {

            var path = RibbonPathTracer.traceLaidRibbonPath(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                STYLE);

            // The pad plus the half width in from the cell's top edge, above its own site.
            assertThat(path.getPoints().get(0))
                .containsExactly(2000.0, 3600.0);
        }

        @Test
        void traceLaidRibbonPathTracesNothingOnACellTooNarrowForThePadWhileForcingIsOff() {

            var path = RibbonPathTracer.traceLaidRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                NARROW_CELL_SITE,
                STYLE);

            assertThat(path.isEmpty())
                .isTrue();
        }

        @Test
        void traceLaidRibbonPathGivesUpThePadOnANarrowCellWhileForcingIsOn() {

            var path = RibbonPathTracer.traceLaidRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                NARROW_CELL_SITE,
                STYLE_FORCING_A_BAND);

            // The half width alone in from the top edge - the pad given up, the half width kept.
            assertThat(path.getPoints().get(0))
                .containsExactly(250.0, 300.0);
        }
    }

    @Nested
    class TraceInspectedRibbonPath {

        @Test
        void traceInspectedRibbonPathReportsACellTracedAtTheAuthoredInsetAsLaidAtThePad() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                STYLE);

            assertThat(ribbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
            assertThat(ribbonPath.centreline())
                .startsWith(2000f, 3600f);
        }

        @Test
        void traceInspectedRibbonPathReportsANarrowCellDrawnAnywayAsLaidUnpadded() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                NARROW_CELL_SITE,
                STYLE_FORCING_A_BAND);

            assertThat(ribbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_UNPADDED);
            assertThat(ribbonPath.centreline())
                .startsWith(250f, 300f);
        }

        @Test
        void traceInspectedRibbonPathKeepsTheRefusedPathOfANarrowCellWhileForcingIsOff() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                NARROW_CELL_SITE,
                STYLE);

            // Refused a band, and drawn all the same: the band pass came back empty on this very
            // cell, which is the state the overlay is looked at to explain.
            assertThat(ribbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.REFUSED);
            assertThat(ribbonPath.centreline())
                .startsWith(250f, 300f);
        }

        @Test
        void traceInspectedRibbonPathTracesNothingOnACellNarrowerThanTheBand() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL_NARROWER_THAN_THE_BAND,
                NARROWEST_CELL_SITE,
                STYLE_FORCING_A_BAND);

            assertThat(ribbonPath.isEmpty())
                .isTrue();
        }
    }
}
