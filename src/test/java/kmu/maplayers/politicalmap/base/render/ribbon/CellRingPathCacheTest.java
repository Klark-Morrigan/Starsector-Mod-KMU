package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.math.geometry.RingPath;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the three answers the cache gives, since the whole of its safety is that a path is served
 * only for the shape it was traced inside.
 *
 * <p>The miss is the case worth stating outright: a cell with no path standing is a cell yet to be
 * walked, not a cell whose ring holds no band. The two are told apart by a null against a path
 * that came back empty, and a caller reading them the other way round would leave every cell too
 * narrow for a band re-tracing on every bake.
 */
final class CellRingPathCacheTest {

    private static final String CELL = "cell";
    private static final String OTHER_CELL = "other";

    @Nested
    class FindRingPathOf {

        @Test
        void findsNothingForACellWhoseRingHasNotBeenWalked() {
            // The miss, which is what every cell of a fresh build answers with.
            assertThat(new CellRingPathCache().findRingPathOf(CELL))
                .isNull();
        }

        @Test
        void findsThePathKeptForTheCellItWasTracedFor() {

            var cache = new CellRingPathCache();
            var path = RingPath.nothingLeftToTrace();

            cache.putRingPath(CELL, path);

            assertThat(cache.findRingPathOf(CELL))
                .isSameAs(path);
        }
    }

    @Nested
    class DropRingPathOf {

        @Test
        void dropsThePathOfTheCellThatWasReshaped() {
            // A path describes one particular ring, so a re-shaped cell must come back to a miss
            // and be walked again rather than being served the ring it used to have.
            var cache = new CellRingPathCache();

            cache.putRingPath(CELL, RingPath.nothingLeftToTrace());
            cache.dropRingPathOf(CELL);

            assertThat(cache.findRingPathOf(CELL))
                .isNull();
        }

        @Test
        void leavesEveryOtherCellsPathStanding() {
            // Re-shaping one cell is the ordinary case and the reason the cache is worth having:
            // the sector's other cells keep the rings they were traced with.
            var cache = new CellRingPathCache();
            var keptPath = RingPath.nothingLeftToTrace();

            cache.putRingPath(CELL, RingPath.nothingLeftToTrace());
            cache.putRingPath(OTHER_CELL, keptPath);
            cache.dropRingPathOf(CELL);

            assertThat(cache.findRingPathOf(OTHER_CELL))
                .isSameAs(keptPath);
        }
    }
}
