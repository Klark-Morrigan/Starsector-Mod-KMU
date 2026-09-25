package kmu.maplayers.ownermap.render.ribbon;

import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.CELL_NARROWER_THAN_THE_BAND;
import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.CELL_NARROWER_THAN_THE_BAND_SITE;
import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.CELL_TOO_NARROW_FOR_THE_PAD;
import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.CELL_TOO_NARROW_FOR_THE_PAD_SITE;
import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.NECKED_CELL;
import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.SQUARE_CELL;
import static kmu.maplayers.ownermap.render.ribbon.RibbonCellFixtures.SQUARE_CELL_SITE;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ladder both callers walk - the authored inset, then the pad given up - and the one
 * thing they do differently with it.
 *
 * <p>The band pass is pinned at its two ends, since between them sits the whole of what a cell
 * short of room draws: a ring that holds the pad is traced at it, and a ring that holds it nowhere
 * is traced at the shallower inset or holds nothing, depending on the player's answer alone. The
 * third case is what separates those two - a ring holding the pad everywhere but one neck takes
 * no step down, since the fall is for a cell with no room anywhere.
 *
 * <p>The overlay is pinned on the case that is its entire reason for existing: a cell refused for
 * want of the pad still hands back the path it was refused on. Nothing on the map says so
 * otherwise - the cell simply draws nothing, exactly like a cell with nothing to report - so a
 * trace that came back empty here would leave the two indistinguishable and the overlay pointless.
 *
 * <p>And on what it hands that path back as: the ring a band may lie on and the ring the cell's own
 * shape denied it, kept apart. A cell with room the whole way round carves nothing; a cell with a
 * neck reports the neck as its own stretch, and what is left of its ring as the two stretches the
 * path's start divides them into.
 *
 * <p>Both are stated in literal coordinates, because the start is a convention rather than a
 * derivation: a path traced at the right inset but opened at the wrong point of the ring is no
 * less correct as geometry and completely wrong as the mark of where a band begins.
 */
final class RibbonPathTracerTest {

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
        void traceLaidRibbonPathHoldsNoStretchOnACellTooNarrowForThePadWhileForcingIsOff() {

            var path = RibbonPathTracer.traceLaidRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE);

            // Traced, and holding nothing: a ring with no room for the pad anywhere fails the
            // inset at every corner, so every stretch of it is carved and there is none left for
            // a band. The refusal is what the carve leaves rather than a verdict of its own.
            assertThat(path.hasStretchHoldingItsInset())
                .isFalse();
        }

        @Test
        void traceLaidRibbonPathKeepsThePadOnACellNarrowedInOnePlaceOnly() {

            var path = RibbonPathTracer.traceLaidRibbonPath(
                NECKED_CELL,
                SQUARE_CELL_SITE,
                STYLE_FORCING_A_BAND);

            // Traced at the authored inset even with the fall available, because the fall
            // answers a cell with no room anywhere and this one has room everywhere but its
            // neck. Taken here, it would move the whole band onto the border to rescue a
            // stretch the carve had already given up.
            assertThat(path.getPoints().get(0))
                .containsExactly(2000.0, 3600.0);
        }

        @Test
        void traceLaidRibbonPathGivesUpThePadOnANarrowCellWhileForcingIsOn() {

            var path = RibbonPathTracer.traceLaidRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
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

            // A cell with room the whole way round carves nothing, so its ring comes back as the
            // one stretch it is, opening at the point a band opens at.
            assertThat(ribbonPath.carvedStretches())
                .isEmpty();
            assertThat(ribbonPath.heldStretches())
                .singleElement()
                .satisfies(stretch -> assertThat(stretch).startsWith(2000f, 3600f));
            assertThat(ribbonPath.startPoint())
                .containsExactly(2000f, 3600f);
        }

        @Test
        void traceInspectedRibbonPathKeepsTheRingANeckDeniedOutOfTheStretchesABandMayUse() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                NECKED_CELL,
                SQUARE_CELL_SITE,
                STYLE);

            // The overlay's whole job on this cell. Drawn as one ring it would show a path
            // running through a neck no band may enter - and on a cell narrow enough to fold its
            // ring, running outside the cell's own border - with nothing to say which part of it
            // a band could use. Split, the neck is reported as the ring the band never had.
            assertThat(ribbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
            assertThat(ribbonPath.carvedStretches())
                .singleElement()
                .satisfies(stretch -> assertThat(stretch).startsWith(3600f, 2200f));

            // Two stretches rather than one, because the ring the neck leaves runs through the
            // path's own start: the carve states it as the piece closing the ring and the piece
            // opening it. Reading them as the one stretch they are is the layout's business, not
            // the overlay's - drawn, the two meet at the start dot and read as continuous.
            assertThat(ribbonPath.heldStretches())
                .hasSize(2);
            assertThat(ribbonPath.heldStretches().get(0))
                .startsWith(2000f, 3600f);
            assertThat(ribbonPath.heldStretches().get(1))
                .startsWith(3600f, 1800f);
        }

        @Test
        void traceInspectedRibbonPathReportsANarrowCellDrawnAnywayAsLaidUnpadded() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE_FORCING_A_BAND);

            assertThat(ribbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_UNPADDED);
            assertThat(ribbonPath.startPoint())
                .containsExactly(250f, 300f);
        }

        @Test
        void traceInspectedRibbonPathKeepsTheRefusedPathOfANarrowCellWhileForcingIsOff() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE);

            // Refused a band, and drawn all the same: the band pass found no stretch to lay one
            // on for this very cell, which is the state the overlay is looked at to explain.
            assertThat(ribbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.REFUSED);
            assertThat(ribbonPath.startPoint())
                .containsExactly(250f, 300f);
        }

        @Test
        void traceInspectedRibbonPathTracesNothingOnACellNarrowerThanTheBand() {

            var ribbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL_NARROWER_THAN_THE_BAND,
                CELL_NARROWER_THAN_THE_BAND_SITE,
                STYLE_FORCING_A_BAND);

            assertThat(ribbonPath.isEmpty())
                .isTrue();
        }
    }
}
