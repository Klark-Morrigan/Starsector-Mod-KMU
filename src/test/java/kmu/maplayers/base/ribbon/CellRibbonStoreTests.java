package kmu.maplayers.base.ribbon;

import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.base.ribbon.overlay.RibbonPathVerdict;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two things the store promises: each map holds only the cells that have something in it,
 * and one drop takes everything a cell's ring was fitted with.
 *
 * <p>The sparse maps are what the emission leans on: it walks the bands and the paths per frame,
 * so an empty value kept for every bare cell would be a walk over the whole sector for nothing.
 *
 * <p>The drop is what the cell store's two writes lean on. A band, its path and the rings behind
 * them are each cut to one particular ring, so each gets its own case: a drop that missed one of
 * them would draw the last shape's work inside the cell's new shape.
 */
final class CellRibbonStoreTests {

    private static final SystemKey CELL = buildCellKey("cell");
    private static final SystemKey OTHER_CELL = buildCellKey("other");

    @Nested
    class PutCellRibbon {

        @Test
        void recordsABandUnderTheCellThatDrawsIt() {

            // ARRANGE
            var store = new CellRibbonStore();
            var expectedRibbon = buildAnyRibbon();

            // ACT
            store.putCellRibbon(CELL, expectedRibbon);

            // ASSERT
            var actualRibbonByCellKey = store.getRibbonByCellKey();

            assertThat(actualRibbonByCellKey)
                .containsOnlyKeys(CELL);
            assertThat(actualRibbonByCellKey.get(CELL))
                .isSameAs(expectedRibbon);
        }

        // Most of the sector draws no band, so the map is kept sparse rather than parallel to the
        // cells: the render pass walks the cells that draw one and no others.
        @Test
        void keepsABandlessCellOutOfTheBandMap() {

            // ARRANGE
            var store = new CellRibbonStore();

            // ACT
            store.putCellRibbon(CELL, CellRibbon.NONE);

            // ASSERT
            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }

        // The re-bake that finds nothing left to report. Without the clear, the cell would keep
        // drawing the band it no longer earns.
        @Test
        void clearsAStandingBandWhenTheCellStopsDrawingOne() {

            // ARRANGE
            var store = new CellRibbonStore();

            store.putCellRibbon(CELL, buildAnyRibbon());

            // ACT
            store.putCellRibbon(CELL, CellRibbon.NONE);

            // ASSERT
            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }
    }

    @Nested
    class PutCellRibbonPath {

        @Test
        void recordsAPathUnderTheCellItWasTracedIn() {

            // ARRANGE
            var store = new CellRibbonStore();
            var expectedRibbonPath = buildAnyRibbonPath();

            // ACT
            store.putCellRibbonPath(CELL, expectedRibbonPath);

            // ASSERT
            var actualRibbonPathByCellKey = store.getRibbonPathByCellKey();

            assertThat(actualRibbonPathByCellKey)
                .containsOnlyKeys(CELL);
            assertThat(actualRibbonPathByCellKey.get(CELL))
                .isSameAs(expectedRibbonPath);
        }

        // A bake hands over nothing for every cell while the overlay is off, and that is the whole
        // of how it is switched off: a path surviving here would leave the map ringed with a
        // diagnostic the player has turned off.
        @Test
        void clearsAStandingPathWhenTheOverlayIsSwitchedOff() {

            // ARRANGE
            var store = new CellRibbonStore();

            store.putCellRibbonPath(CELL, buildAnyRibbonPath());

            // ACT
            store.putCellRibbonPath(CELL, CellRibbonPath.NONE);

            // ASSERT
            assertThat(store.getRibbonPathByCellKey())
                .isEmpty();
        }
    }

    @Nested
    class DropRingFittedWorkOf {

        // A band is triangles fitted to one particular ring, so a re-shaped cell keeping its band
        // would draw the last shape's band inside this shape's cell.
        @Test
        void dropsTheCellsBand() {

            // ARRANGE
            var store = new CellRibbonStore();

            store.putCellRibbon(CELL, buildAnyRibbon());

            // ACT
            store.dropRingFittedWorkOf(CELL);

            // ASSERT
            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }

        // A path traced in the last shape drawn over this one would report the overlay's own
        // staleness as the cell's geometry, which is the one thing a diagnostic must not do.
        @Test
        void dropsTheCellsBandPath() {

            // ARRANGE
            var store = new CellRibbonStore();

            store.putCellRibbonPath(CELL, buildAnyRibbonPath());

            // ACT
            store.dropRingFittedWorkOf(CELL);

            // ASSERT
            assertThat(store.getRibbonPathByCellKey())
                .isEmpty();
        }

        // The whole of what makes the kept rings safe. They are held under no key and no revision,
        // so a cell served a ring traced inside the shape it used to have would lay its band round
        // a cell that is no longer there. Both insets, since a narrow cell keeps one of each.
        @Test
        void dropsEveryRingTracedInsideTheCell() {

            // ARRANGE
            var store = new CellRibbonStore();
            var ringPathCache = store.getRingPathCache();

            ringPathCache.putRingPath(CELL, RibbonPathInset.PADDED, RingPath.nothingLeftToTrace());
            ringPathCache.putRingPath(CELL, RibbonPathInset.UNPADDED, RingPath.nothingLeftToTrace());

            // ACT
            store.dropRingFittedWorkOf(CELL);

            // ASSERT
            var actualPaddedRing = ringPathCache.findRingPathOf(CELL, RibbonPathInset.PADDED);
            var actualUnpaddedRing = ringPathCache.findRingPathOf(CELL, RibbonPathInset.UNPADDED);

            assertThat(actualPaddedRing)
                .isNull();
            assertThat(actualUnpaddedRing)
                .isNull();
        }

        // Re-shaping one cell is the ordinary case: the sector's other cells keep what they were
        // laid with.
        @Test
        void leavesEveryOtherCellsWorkStanding() {

            // ARRANGE
            var store = new CellRibbonStore();
            var expectedRing = RingPath.nothingLeftToTrace();

            store.putCellRibbon(CELL, buildAnyRibbon());
            store.putCellRibbon(OTHER_CELL, buildAnyRibbon());
            store.putCellRibbonPath(OTHER_CELL, buildAnyRibbonPath());
            store.getRingPathCache().putRingPath(OTHER_CELL, RibbonPathInset.PADDED, expectedRing);

            // ACT
            store.dropRingFittedWorkOf(CELL);

            // ASSERT
            var actualRing = store
                .getRingPathCache()
                .findRingPathOf(OTHER_CELL, RibbonPathInset.PADDED);

            assertThat(store.getRibbonByCellKey())
                .containsOnlyKeys(OTHER_CELL);
            assertThat(store.getRibbonPathByCellKey())
                .containsOnlyKeys(OTHER_CELL);
            assertThat(actualRing)
                .isSameAs(expectedRing);
        }
    }

    // A band that draws something, which is all these cases ask of it: what distinguishes it from
    // CellRibbon.NONE is that it has a run at all, not what the run looks like.
    private static CellRibbon buildAnyRibbon() {

        return new CellRibbon(List.of(
            new RibbonBand(Color.WHITE, new float[] {0, 0, 1, 0, 1, 1})));
    }

    // A path that draws something, which is all these cases ask of it: what distinguishes it from
    // CellRibbonPath.NONE is that it has a stretch at all, not where that stretch runs.
    private static CellRibbonPath buildAnyRibbonPath() {

        return new CellRibbonPath(
            List.of(new float[] {0, 0, 1, 0, 1, 1}),
            List.of(),
            new float[] {0, 0},
            RibbonPathVerdict.LAID_AT_PAD);
    }
}
