package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Bounds;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for whether a line would be drawing something already drawn, asked of walls
 * filed on a grid as they are laid.
 *
 * <p>Every line here runs along the x axis from 0 to 1000, and the walls under it are placed in
 * round numbers so which stretch each covers is read off the coordinates.
 */
class WallCoverageTests {

    private static final double[] LINE_START = {0, 0};
    private static final double[] LINE_END = {1000, 0};

    // Big enough to hold every wall below; the grid is only sized by it.
    private static final Bounds EXTENT = new Bounds(-100, -100, 1100, 100);

    private static final int WALL_COUNT = 4;

    private static final double TOLERANCE = 10;

    @Nested
    class IsAlreadyWalled {

        @Test
        void aLineUnderOneWallIsWalled() {

            var walls = WallCoverage.createWithin(EXTENT, WALL_COUNT, TOLERANCE);

            walls.fileWall(new double[] {0, 5}, new double[] {1000, 5});

            assertThat(walls.isAlreadyWalled(LINE_START, LINE_END))
                .isTrue();
        }

        @Test
        void aLineCoveredHalfByEachOfTwoWallsIsWalled() {
            // No one wall covers it, which is the case asking each wall in turn misses.
            var walls = WallCoverage.createWithin(EXTENT, WALL_COUNT, TOLERANCE);

            walls.fileWall(new double[] {0, 5}, new double[] {500, 5});
            walls.fileWall(new double[] {500, -5}, new double[] {1000, -5});

            assertThat(walls.isAlreadyWalled(LINE_START, LINE_END))
                .isTrue();
        }

        @Test
        void aLineWithAStretchNoWallCoversIsNotWalled() {

            var walls = WallCoverage.createWithin(EXTENT, WALL_COUNT, TOLERANCE);

            walls.fileWall(new double[] {0, 5}, new double[] {400, 5});
            walls.fileWall(new double[] {600, 5}, new double[] {1000, 5});

            assertThat(walls.isAlreadyWalled(LINE_START, LINE_END))
                .isFalse();
        }

        @Test
        void aWallFurtherOffThanTheToleranceDoesNotCount() {

            var walls = WallCoverage.createWithin(EXTENT, WALL_COUNT, TOLERANCE);

            walls.fileWall(new double[] {0, 20}, new double[] {1000, 20});

            assertThat(walls.isAlreadyWalled(LINE_START, LINE_END))
                .isFalse();
        }

        @Test
        void aWallFiledAfterAnEarlierQuestionCountsForTheNext() {

            var walls = WallCoverage.createWithin(EXTENT, WALL_COUNT, TOLERANCE);

            assertThat(walls.isAlreadyWalled(LINE_START, LINE_END))
                .isFalse();

            walls.fileWall(LINE_START, LINE_END);

            assertThat(walls.isAlreadyWalled(LINE_START, LINE_END))
                .isTrue();
        }
    }

    @Nested
    class CollectRingWalls {

        @Test
        void aRingBecomesOneWallPerSideClosingBackOnItsStart() {

            var walls = WallCoverage.collectRingWalls(List.of(List.of(
                new double[] {0, 0}, new double[] {10, 0}, new double[] {10, 10})));

            assertThat(walls)
                .hasSize(3);
            assertThat(walls.get(2)[0])
                .containsExactly(10, 10);
            assertThat(walls.get(2)[1])
                .containsExactly(0, 0);
        }
    }
}
