package kmu.maplayers.base.ribbon;

import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the answers the cache gives, since the whole of its safety is that a path is served only for
 * the shape and the inset it was traced at.
 *
 * <p>The miss is the case worth stating outright: a cell with no path standing is a cell yet to be
 * walked, not a cell whose ring holds no band. The two are told apart by a null against a path
 * that came back empty, and a caller reading them the other way round would leave every cell too
 * narrow for a band re-tracing on every bake.
 *
 * <p>The insets are kept apart because a cell's two rings answer different questions: served the
 * unpadded ring where the padded one was asked for, the overlay would read a cell that gave up its
 * pad as one that kept it.
 */
final class CellRingPathCacheTests {

    private static final SystemKey CELL = buildCellKey("cell");
    private static final SystemKey OTHER_CELL = buildCellKey("other");

    @Nested
    class FindRingPathOf {

        // The miss, which is what every cell of a fresh build answers with.
        @Test
        void findsNothingForACellWhoseRingHasNotBeenWalked() {

            // ARRANGE
            var cache = new CellRingPathCache();

            // ACT
            var actualRingPath = cache.findRingPathOf(CELL, RibbonPathInset.PADDED);

            // ASSERT
            assertThat(actualRingPath)
                .isNull();
        }

        @Test
        void findsThePathKeptForTheCellAndInsetItWasTracedAt() {

            // ARRANGE
            var cache = new CellRingPathCache();
            var expectedRingPath = RingPath.nothingLeftToTrace();

            cache.putRingPath(CELL, RibbonPathInset.PADDED, expectedRingPath);

            // ACT
            var actualRingPath = cache.findRingPathOf(CELL, RibbonPathInset.PADDED);

            // ASSERT
            assertThat(actualRingPath)
                .isSameAs(expectedRingPath);
        }

        // A cell's padded ring is no answer for its unpadded one: the inset is what decides which
        // ring a band is laid on.
        @Test
        void findsNothingAtAnInsetTheCellWasNotTracedAt() {

            // ARRANGE
            var cache = new CellRingPathCache();

            cache.putRingPath(CELL, RibbonPathInset.PADDED, RingPath.nothingLeftToTrace());

            // ACT
            var actualRingPath = cache.findRingPathOf(CELL, RibbonPathInset.UNPADDED);

            // ASSERT
            assertThat(actualRingPath)
                .isNull();
        }
    }

    @Nested
    class DropRingPathsOf {

        // A path describes one particular ring, so a re-shaped cell must come back to a miss at
        // every inset and be walked again rather than being served the ring it used to have.
        @Test
        void dropsEveryPathOfTheCellThatWasReshaped() {

            // ARRANGE
            var cache = new CellRingPathCache();

            cache.putRingPath(CELL, RibbonPathInset.PADDED, RingPath.nothingLeftToTrace());
            cache.putRingPath(CELL, RibbonPathInset.UNPADDED, RingPath.nothingLeftToTrace());

            // ACT
            cache.dropRingPathsOf(CELL);

            // ASSERT
            var actualPaddedRingPath = cache.findRingPathOf(CELL, RibbonPathInset.PADDED);
            var actualUnpaddedRingPath = cache.findRingPathOf(CELL, RibbonPathInset.UNPADDED);

            assertThat(actualPaddedRingPath)
                .isNull();
            assertThat(actualUnpaddedRingPath)
                .isNull();
        }

        // Re-shaping one cell is the ordinary case and the reason the cache is worth having: the
        // sector's other cells keep the rings they were traced with.
        @Test
        void leavesEveryOtherCellsPathStanding() {

            // ARRANGE
            var cache = new CellRingPathCache();
            var expectedRingPath = RingPath.nothingLeftToTrace();

            cache.putRingPath(CELL, RibbonPathInset.PADDED, RingPath.nothingLeftToTrace());
            cache.putRingPath(OTHER_CELL, RibbonPathInset.PADDED, expectedRingPath);

            // ACT
            cache.dropRingPathsOf(CELL);

            // ASSERT
            var actualRingPath = cache.findRingPathOf(OTHER_CELL, RibbonPathInset.PADDED);

            assertThat(actualRingPath)
                .isSameAs(expectedRingPath);
        }
    }
}
