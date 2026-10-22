package kmu.maplayers.base.ribbon;

import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.base.ribbon.overlay.RibbonPathVerdict;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.CELL_NARROWER_THAN_THE_BAND;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.CELL_NARROWER_THAN_THE_BAND_SITE;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.CELL_TOO_NARROW_FOR_THE_PAD;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.CELL_TOO_NARROW_FOR_THE_PAD_SITE;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.NECKED_CELL;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.SQUARE_CELL;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.SQUARE_CELL_SITE;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ladder both callers walk - the authored inset, then the pad given up - and the one
 * thing they do differently with it.
 *
 * <p>The band pass is pinned at its two ends, since between them sits the whole of what a cell
 * short of room draws: a ring that holds the pad is traced at it, and a ring that holds it nowhere
 * is traced at the shallower inset or holds nothing, depending on the style's answer alone. The
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
 * <p>The kept rings are pinned from both sides: each rung the ladder walks is kept, a rung it did
 * not need is never walked, and a ring standing for the cell is read rather than walked again -
 * by the band pass on a re-bake, and by the overlay after the band pass.
 *
 * <p>Coordinates are literal, because the start is a convention rather than a derivation: a path
 * traced at the right inset but opened at the wrong point of the ring is no less correct as
 * geometry and completely wrong as the mark of where a band begins.
 */
final class RibbonPathTracerTests {

    // The cell every case traces, which is the key its rings are kept under.
    private static final SystemKey CELL = buildCellKey("cell");

    // Round numbers rather than the shipped sizes, so the coordinates below pin the conventions
    // and not whatever the defaults happen to be: a 400-wide band 200 clear of the border runs its
    // centreline 400 inside the cell, and 200 inside it once the pad is given up.
    private static final RibbonStyle STYLE = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        false);

    // The same sizes with the shipped answer to a cell that has no room: draw the band anyway.
    private static final RibbonStyle STYLE_FORCING_A_BAND = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        true);

    @Nested
    class TraceLaidRibbonPath {

        @Test
        void tracesTheRingAtTheAuthoredInset() {

            // ARRANGE
            var expectedStart = new double[] {2000.0, 3600.0};

            // ACT
            var actualPath = traceLaidIn(SQUARE_CELL, SQUARE_CELL_SITE, STYLE);

            // ASSERT
            // The pad plus the half width in from the cell's top edge, above its own site.
            assertThat(actualPath.getPoints().get(0))
                .containsExactly(expectedStart);
        }

        // Traced, and holding nothing: a ring with no room for the pad anywhere fails the inset at
        // every corner, so every stretch of it is carved and there is none left for a band. The
        // refusal is what the carve leaves rather than a verdict of its own.
        @Test
        void holdsNoStretchOnACellTooNarrowForThePadWhileForcingIsOff() {

            // ARRANGE
            var ringPathCache = new CellRingPathCache();

            // ACT
            var actualPath = RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE,
                ringPathCache);

            // ASSERT
            assertThat(actualPath.hasStretchHoldingItsInset())
                .isFalse();
        }

        // Traced at the authored inset even with the fall available, because the fall answers a
        // cell with no room anywhere and this one has room everywhere but its neck. Taken here, it
        // would move the whole band onto the border to rescue a stretch the carve had already
        // given up.
        @Test
        void keepsThePadOnACellNarrowedInOnePlaceOnly() {

            // ARRANGE
            var expectedStart = new double[] {2000.0, 3600.0};

            // ACT
            var actualPath = traceLaidIn(NECKED_CELL, SQUARE_CELL_SITE, STYLE_FORCING_A_BAND);

            // ASSERT
            assertThat(actualPath.getPoints().get(0))
                .containsExactly(expectedStart);
        }

        @Test
        void givesUpThePadOnANarrowCellWhileForcingIsOn() {

            // ARRANGE
            var expectedStart = new double[] {250.0, 300.0};

            // ACT
            var actualPath = traceLaidIn(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE_FORCING_A_BAND);

            // ASSERT
            // The half width alone in from the top edge - the pad given up, the half width kept.
            assertThat(actualPath.getPoints().get(0))
                .containsExactly(expectedStart);
        }

        // Forcing gives up the pad and nothing else: the half width is what keeps the band's near
        // edge off the border, so a cell narrower than the band has no shallower trace left to fall
        // to and draws nothing whatever the setting says.
        @Test
        void holdsNoStretchOnACellNarrowerThanTheBandEvenWhileForcingIsOn() {

            // ARRANGE
            var ringPathCache = new CellRingPathCache();

            // ACT
            var actualPath = RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                CELL_NARROWER_THAN_THE_BAND,
                CELL_NARROWER_THAN_THE_BAND_SITE,
                STYLE_FORCING_A_BAND,
                ringPathCache);

            // ASSERT
            assertThat(actualPath.hasStretchHoldingItsInset())
                .isFalse();
        }

        // What the kept rings are for. A bake runs whenever a cluster name may have moved, while
        // a cell's ring moves only when the cell is re-shaped, so the second trace of an untouched
        // cell must walk nothing. Identity, since a fresh walk of the same outline is equal.
        @Test
        void readsTheRingStandingForTheCellRatherThanWalkingItAgain() {

            // ARRANGE
            var ringPathCache = new CellRingPathCache();
            var expectedPath = RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                STYLE,
                ringPathCache);

            // ACT
            var actualPath = RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                STYLE,
                ringPathCache);

            // ASSERT
            assertThat(actualPath)
                .isSameAs(expectedPath);
        }

        // Both rungs a narrow cell walked are kept, the refused padded ring included: it is what
        // tells the overlay the pad was given up, without walking it a second time.
        @Test
        void keepsBothRungsOfANarrowCellWhileForcingIsOn() {

            // ARRANGE
            var ringPathCache = new CellRingPathCache();

            // ACT
            var actualPath = RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE_FORCING_A_BAND,
                ringPathCache);

            // ASSERT
            var actualPaddedPath = ringPathCache.findRingPathOf(CELL, RibbonPathInset.PADDED);
            var actualUnpaddedPath = ringPathCache.findRingPathOf(CELL, RibbonPathInset.UNPADDED);

            assertThat(actualPaddedPath)
                .isNotNull();
            assertThat(actualUnpaddedPath)
                .isSameAs(actualPath);
        }

        // With the fall switched off a band is never laid on the shallower ring, so walking it
        // would be inset work spent on nothing.
        @Test
        void walksNoUnpaddedRungWhileForcingIsOff() {

            // ARRANGE
            var ringPathCache = new CellRingPathCache();

            // ACT
            RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE,
                ringPathCache);

            // ASSERT
            var actualUnpaddedPath = ringPathCache.findRingPathOf(CELL, RibbonPathInset.UNPADDED);

            assertThat(actualUnpaddedPath)
                .isNull();
        }
    }

    @Nested
    class TraceInspectedRibbonPath {

        // A cell with room the whole way round carves nothing, so its ring comes back as the one
        // stretch it is, opening at the point a band opens at.
        @Test
        void reportsACellTracedAtTheAuthoredInsetAsLaidAtThePad() {

            // ARRANGE
            var expectedStart = new float[] {2000f, 3600f};

            // ACT
            var actualRibbonPath = traceInspectedIn(SQUARE_CELL, SQUARE_CELL_SITE, STYLE);

            // ASSERT
            assertThat(actualRibbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
            assertThat(actualRibbonPath.carvedStretches())
                .isEmpty();
            assertThat(actualRibbonPath.heldStretches())
                .singleElement()
                .satisfies(stretch -> assertThat(stretch).startsWith(expectedStart));
            assertThat(actualRibbonPath.startPoint())
                .containsExactly(expectedStart);
        }

        // The overlay's whole job on this cell. Drawn as one ring it would show a path running
        // through a neck no band may enter - and on a cell narrow enough to fold its ring, running
        // outside the cell's own border - with nothing to say which part of it a band could use.
        // Split, the neck is reported as the ring the band never had.
        @Test
        void keepsTheRingANeckDeniedOutOfTheStretchesABandMayUse() {

            // ARRANGE
            var expectedNeckStart = new float[] {3600f, 2200f};
            var expectedOpeningStart = new float[] {2000f, 3600f};
            var expectedClosingStart = new float[] {3600f, 1800f};

            // ACT
            var actualRibbonPath = traceInspectedIn(NECKED_CELL, SQUARE_CELL_SITE, STYLE);

            // ASSERT
            assertThat(actualRibbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
            assertThat(actualRibbonPath.carvedStretches())
                .singleElement()
                .satisfies(stretch -> assertThat(stretch).startsWith(expectedNeckStart));

            // Two stretches rather than one, because the ring the neck leaves runs through the
            // path's own start: the carve states it as the piece closing the ring and the piece
            // opening it. Reading them as the one stretch they are is the layout's business, not
            // the overlay's - drawn, the two meet at the start dot and read as continuous.
            assertThat(actualRibbonPath.heldStretches())
                .hasSize(2);
            assertThat(actualRibbonPath.heldStretches().get(0))
                .startsWith(expectedOpeningStart);
            assertThat(actualRibbonPath.heldStretches().get(1))
                .startsWith(expectedClosingStart);
        }

        @Test
        void reportsANarrowCellDrawnAnywayAsLaidUnpadded() {

            // ARRANGE
            var expectedStart = new float[] {250f, 300f};

            // ACT
            var actualRibbonPath = traceInspectedIn(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE_FORCING_A_BAND);

            // ASSERT
            assertThat(actualRibbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_UNPADDED);
            assertThat(actualRibbonPath.startPoint())
                .containsExactly(expectedStart);
        }

        // Refused a band, and drawn all the same: the band pass found no stretch to lay one on for
        // this very cell, which is the state the overlay is looked at to explain.
        @Test
        void keepsTheRefusedPathOfANarrowCellWhileForcingIsOff() {

            // ARRANGE
            var expectedStart = new float[] {250f, 300f};

            // ACT
            var actualRibbonPath = traceInspectedIn(
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE);

            // ASSERT
            assertThat(actualRibbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.REFUSED);
            assertThat(actualRibbonPath.startPoint())
                .containsExactly(expectedStart);
        }

        @Test
        void tracesNothingOnACellNarrowerThanTheBand() {

            // ARRANGE
            var expectedRibbonPath = CellRibbonPath.NONE;

            // ACT
            var actualRibbonPath = traceInspectedIn(
                CELL_NARROWER_THAN_THE_BAND,
                CELL_NARROWER_THAN_THE_BAND_SITE,
                STYLE_FORCING_A_BAND);

            // ASSERT
            assertThat(actualRibbonPath)
                .isSameAs(expectedRibbonPath);
        }

        // The overlay reads the rings the band pass walked rather than walking its own. Posed by
        // handing it an outline too narrow for the pad after the band pass traced the square: a
        // trace of its own would refuse that outline, while the kept square ring holds the pad.
        @Test
        void readsTheRingTheBandPassWalkedRatherThanWalkingItAgain() {

            // ARRANGE
            var ringPathCache = new CellRingPathCache();
            var expectedStart = new float[] {2000f, 3600f};

            RibbonPathTracer.traceLaidRibbonPath(
                CELL,
                SQUARE_CELL,
                SQUARE_CELL_SITE,
                STYLE,
                ringPathCache);

            // ACT
            var actualRibbonPath = RibbonPathTracer.traceInspectedRibbonPath(
                CELL,
                CELL_TOO_NARROW_FOR_THE_PAD,
                CELL_TOO_NARROW_FOR_THE_PAD_SITE,
                STYLE,
                ringPathCache);

            // ASSERT
            assertThat(actualRibbonPath.verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
            assertThat(actualRibbonPath.startPoint())
                .containsExactly(expectedStart);
        }
    }

    // The band pass's path through a cache of its own, for the cases about where a path runs
    // rather than about which rings are kept.
    private static RingPath traceLaidIn(
            List<double[]> ring,
            double[] topAnchor,
            RibbonStyle style) {

        return RibbonPathTracer.traceLaidRibbonPath(
            CELL,
            ring,
            topAnchor,
            style,
            new CellRingPathCache());
    }

    // The overlay's path through a cache of its own, for the same cases.
    private static CellRibbonPath traceInspectedIn(
            List<double[]> ring,
            double[] topAnchor,
            RibbonStyle style) {

        return RibbonPathTracer.traceInspectedRibbonPath(
            CELL,
            ring,
            topAnchor,
            style,
            new CellRingPathCache());
    }
}
