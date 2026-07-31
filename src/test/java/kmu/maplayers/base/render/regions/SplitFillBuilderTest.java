package kmu.maplayers.base.render.regions;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.theme.HatchStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins which of the three fills a region actually pays for: nothing at all for a "No color"
 * fill, one tessellation of the frontier when every member fills solid, and a per-state carve
 * only when the region is spotlit or genuinely holds non-solid ground.
 *
 * <p>That fast path is the point - the common case is a region that only fills solid, and it
 * must come out of the same smoothed loops the border strokes rather than out of a trace of
 * its own, or the fill and the border stop in different places.
 *
 * <p>The fixtures are two hand-built square cells side by side, sized well clear of the border
 * channel, so the areas below are checkable by hand.
 */
final class SplitFillBuilderTest {
    private static final String REGION_KEY = "region";
    private static final String HELD_SYSTEM = "A";
    private static final String HATCHED_SYSTEM = "B";
    private static final HatchStyle HATCH = new HatchStyle(200, Math.PI / 4, 1);
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;

    // Two cells of the one region, meeting along x = 2000: A spans [0, 2000], B spans [2000, 4000],
    // both 2000 tall. Their shared edge is a same-key seam; every other edge is a border.
    private static final Map<String, List<CellEdge>> EDGES = Map.of(
            HELD_SYSTEM, List.of(
                    edge(0, 0, 2000, 0, null),
                    edge(2000, 0, 2000, 2000, HATCHED_SYSTEM),
                    edge(2000, 2000, 0, 2000, null),
                    edge(0, 2000, 0, 0, null)),
            HATCHED_SYSTEM, List.of(
                    edge(2000, 0, 4000, 0, null),
                    edge(4000, 0, 4000, 2000, null),
                    edge(4000, 2000, 2000, 2000, null),
                    edge(2000, 2000, 2000, 0, HELD_SYSTEM)));
    private static final CellGrouping GROUPING = new CellGrouping(
            Map.of(HELD_SYSTEM, HELD_SYSTEM, HATCHED_SYSTEM, HATCHED_SYSTEM),
            Map.of(HELD_SYSTEM, REGION_KEY, HATCHED_SYSTEM, REGION_KEY));
    // A frontier the fill is clipped to: the square [0, 1000] x [0, 1000], area 1e6.
    private static final List<List<double[]>> BORDER_LOOPS = List.of(square(0, 0, 1000));

    @Nested
    class BuildFill {

        @Test
        void buildFillDrawsNothingForANoColorFill() {
            // A region the player has switched the fill off for pays no tessellation at all,
            // rather than baking triangles the draw pass would then skip.
            var fill = builder().buildFill(false, solidOnlySplit(), REGION_KEY, null);

            assertThat(fill.solidTriangles()).isEmpty();
            assertThat(fill.hatchSegments()).isEmpty();
        }

        @Test
        void buildFillTessellatesTheFrontierWhenEveryMemberFillsSolid() {
            // The fast path: no trace of its own, just the smoothed loops the border strokes, so
            // the fill lands exactly where the border does.
            var fill = builder().buildFill(false, solidOnlySplit(), REGION_KEY, Color.RED);

            assertThat(totalTriangleArea(fill.solidTriangles())).isCloseTo(1e6, within(1.0));
            assertThat(fill.hatchSegments()).isEmpty();
        }

        @Test
        void buildFillCarvesPerStateWhenTheRegionHoldsHatchedGround() {
            // Non-solid members force the carve even off the spotlight, so solid and hatched
            // ground read apart inside the one frontier.
            var fill = builder().buildFill(false, hatchedSplit(), REGION_KEY, Color.RED);

            assertThat(fill.solidTriangles()).isNotEmpty();
        }

        @Test
        void buildFillCarvesPerStateForASpotlitRegionThatFillsSolidThroughout() {
            // The spotlight always splits: its fill is per state even where it dominates
            // everywhere, so a spotlit region does not fall into the solid fast path.
            var solid = builder().buildFill(false, solidOnlySplit(), REGION_KEY, Color.RED);
            var spotlit = builder().buildFill(true, solidOnlySplit(), REGION_KEY, Color.RED);

            // The carve traces its own rings and clips them to the frontier, so it cannot come
            // back as the frontier's own untouched tessellation the fast path produces.
            assertThat(spotlit.solidTriangles()).isNotEqualTo(solid.solidTriangles());
        }

        @Test
        void buildFillLeavesTheHatchEmptyWhenNoMemberIsHatched() {
            var fill = builder().buildFill(true, solidOnlySplit(), REGION_KEY, Color.RED);

            assertThat(fill.hatchSegments()).isEmpty();
        }
    }

    private static SplitFillBuilder builder() {
        return new SplitFillBuilder(
                EDGES,
                GROUPING,
                new ClusterBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT),
                BORDER_LOOPS,
                HATCH);
    }

    // Both members held outright - the common case, and the one the solid fast path is for.
    private static FillSplit solidOnlySplit() {
        return FillSplit.splitMembersByFillState(
                GROUPING,
                List.of(HELD_SYSTEM, HATCHED_SYSTEM),
                Set.of(),
                Set.of());
    }

    // One member solid, one hatched - the split the carve exists to draw.
    private static FillSplit hatchedSplit() {
        return FillSplit.splitMembersByFillState(
                GROUPING,
                List.of(HELD_SYSTEM, HATCHED_SYSTEM),
                Set.of(HATCHED_SYSTEM),
                Set.of());
    }

    // One cell edge facing the given neighbour system, or the reach bound when it is null.
    private static CellEdge edge(double x1, double y1, double x2, double y2, String neighbour) {
        return new CellEdge(x1, y1, x2, y2,
                neighbour == null
                        ? EdgeTarget.REACH_BOUND
                        : new EdgeTarget.AcrossSystem(neighbour));
    }

    // An axis-aligned square ring, counter-clockwise, spanning [minX, minX + side] x
    // [minY, minY + side].
    private static List<double[]> square(double minX, double minY, double side) {
        return List.of(
                new double[] {minX, minY},
                new double[] {minX + side, minY},
                new double[] {minX + side, minY + side},
                new double[] {minX, minY + side});
    }

    // Sums the unsigned area of every triangle in a flat [x, y, x, y, ...] soup, six floats per
    // triangle - what the fill actually covers.
    private static double totalTriangleArea(float[] triangles) {
        var floatsPerTriangle = 6;
        var total = 0.0;
        for (var i = 0; i + floatsPerTriangle <= triangles.length; i += floatsPerTriangle) {
            var ax = triangles[i];
            var ay = triangles[i + 1];
            var bx = triangles[i + 2];
            var by = triangles[i + 3];
            var cx = triangles[i + 4];
            var cy = triangles[i + 5];
            total += Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2.0;
        }
        return total;
    }
}
