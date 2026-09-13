package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.theme.HatchStyle;
import kmu.maplayers.base.theme.ThemeFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacingCell;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins which of the three fills a cluster actually pays for: nothing at all for a "No color"
 * fill, one tessellation of the frontier when every member fills solid, and a per-state carve
 * only when the cluster is spotlit or genuinely holds non-solid members.
 *
 * <p>That fast path is the point - the common case is an owner that only fills solid, and it
 * must come out of the same smoothed loops the border strokes rather than out of a trace of
 * its own, or the fill and the border stop in different places.
 *
 * <p>And that each of an owner's bodies gets a fill confined to itself. The rings are traced
 * once for the owner and cut per body, so the per-body clip is the only thing keeping one body's
 * fill out of another - which is why each case here names the body it cuts against.
 *
 * <p>The fixtures are two hand-built square cells side by side, sized well clear of the border
 * channel, so the areas below are checkable by hand.
 */
final class SplitFillBuilderTest {

    private static final String REGION_KEY = "cluster";
    private static final String HELD_SYSTEM_ID = "A";
    private static final String HATCHED_SYSTEM_ID = "B";
    private static final SystemKey HELD_SYSTEM = buildCellKey(HELD_SYSTEM_ID);
    private static final SystemKey HATCHED_SYSTEM = buildCellKey(HATCHED_SYSTEM_ID);
    private static final HatchStyle HATCH = ThemeFixtures.createHatchStyle(200, Math.PI / 4, 1);
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;

    // Two cells of the one cluster, meeting along x = 2000: A spans [0, 2000], B spans [2000, 4000],
    // both 2000 tall. Their shared edge is a same-owner seam; every other edge is a border.
    private static final Map<SystemKey, List<CellEdge>> EDGES = Map.of(
        HELD_SYSTEM, List.of(
            buildEdgeFacingCell(0, 0, 2000, 0, null),
            buildEdgeFacingCell(2000, 0, 2000, 2000, HATCHED_SYSTEM),
            buildEdgeFacingCell(2000, 2000, 0, 2000, null),
            buildEdgeFacingCell(0, 2000, 0, 0, null)),
        HATCHED_SYSTEM, List.of(
            buildEdgeFacingCell(2000, 0, 4000, 0, null),
            buildEdgeFacingCell(4000, 0, 4000, 2000, null),
            buildEdgeFacingCell(4000, 2000, 2000, 2000, null),
            buildEdgeFacingCell(2000, 2000, 2000, 0, HELD_SYSTEM)));

    private static final CellGrouping GROUPING = new CellGrouping(
        Map.of(HELD_SYSTEM, HELD_SYSTEM, HATCHED_SYSTEM, HATCHED_SYSTEM),
        Map.of(HELD_SYSTEM_ID, REGION_KEY, HATCHED_SYSTEM_ID, REGION_KEY));

    // The body the fill is clipped to: the square [0, 1000] x [0, 1000], area 1e6, overlapping
    // the members' own cells.
    private static final RingRegion HOME_BODY = new RingRegion(
        buildSquare(0, 0, 1000),
        List.of());

    // A second body of the same owner, clear of the members' cells and half the size - so a
    // fill that leaked from one body into the other, or that was built against the two together,
    // is caught by area rather than by mere presence. Area 25e4.
    private static final RingRegion DISTANT_BODY =
        new RingRegion(buildSquare(10000, 0, 500), List.of());

    @Nested
    class TraceFill {

        @Test
        void traceFillDrawsNothingForANoColourFill() {
            // An owner the player has switched the fill off for pays no tessellation at all,
            // rather than baking triangles the draw pass would then skip - and every body it
            // holds cuts to nothing, since the bodies themselves still stroke and hold a label.
            var tracedFill = createSplitFillBuilder().traceFill(
                false, // Is not spotlit.
                buildSolidOnlySplit(),
                REGION_KEY,
                null); // No fill colour.

            assertThat(tracedFill.buildFillFor(HOME_BODY).solidTriangles())
                .isEmpty();
            assertThat(tracedFill.buildFillFor(HOME_BODY).hatchSegments())
                .isEmpty();
            assertThat(tracedFill.buildFillFor(DISTANT_BODY).solidTriangles())
                .isEmpty();
        }

        @Test
        void traceFillTessellatesTheFrontierWhenEveryMemberFillsSolid() {
            // The fast path: no trace of its own, just the smoothed loops the border strokes, so
            // the fill lands exactly where the border does.
            var fill = createSplitFillBuilder()
                .traceFill(false, buildSolidOnlySplit(), REGION_KEY, Color.RED)
                .buildFillFor(HOME_BODY);

            assertThat(computeTotalTriangleArea(fill.solidTriangles()))
                .isCloseTo(1e6, within(1.0));
            assertThat(fill.hatchSegments())
                .isEmpty();
        }

        @Test
        void traceFillGivesEachBodyItsOwnAreaRatherThanTheOwnersCombinedOne() {
            // Two bodies of one owner cut from the same traced fill, each from its own loops.
            // Cut against both at once, either would carry the union - the error the flat record
            // could not have caught, since it held one soup over everything either way.
            var tracedFill = createSplitFillBuilder().traceFill(
                false, // Is not spotlit.
                buildSolidOnlySplit(),
                REGION_KEY,
                Color.RED);

            assertThat(computeTotalTriangleArea(tracedFill.buildFillFor(HOME_BODY).solidTriangles()))
                .isCloseTo(1e6, within(1.0));
            assertThat(computeTotalTriangleArea(tracedFill.buildFillFor(DISTANT_BODY).solidTriangles()))
                .isCloseTo(25e4, within(1.0));
        }

        @Test
        void traceFillCarvesPerStateWhenTheOwnerHoldsAHatchedMember() {
            // Non-solid members force the carve even off the spotlight, so solid and hatched
            // fills read apart inside the one frontier.
            var fill = createSplitFillBuilder()
                .traceFill(false, buildHatchedSplit(), REGION_KEY, Color.RED)
                .buildFillFor(HOME_BODY);

            assertThat(fill.solidTriangles())
                .isNotEmpty();
        }

        @Test
        void traceFillConfinesACarvedStateToTheBodyItsMembersSitIn() {
            // The states are traced once across the whole owner, so nothing but the per-body clip
            // keeps the distant body from being painted over area it does not contain. Its fill
            // has to come back empty: its own loops enclose none of the members.
            var tracedFill = createSplitFillBuilder().traceFill(
                false, // Is not spotlit.
                buildHatchedSplit(),
                REGION_KEY,
                Color.RED);

            assertThat(tracedFill.buildFillFor(HOME_BODY).solidTriangles())
                .isNotEmpty();
            assertThat(tracedFill.buildFillFor(DISTANT_BODY).solidTriangles())
                .isEmpty();
            assertThat(tracedFill.buildFillFor(DISTANT_BODY).hatchSegments())
                .isEmpty();
        }

        @Test
        void traceFillCarvesPerStateForASpotlitOwnerThatFillsSolidThroughout() {
            // The spotlight always splits: its fill is per state even where it dominates
            // everywhere, so a spotlit owner does not fall into the solid fast path.
            var solid = createSplitFillBuilder()
                .traceFill(false, buildSolidOnlySplit(), REGION_KEY, Color.RED)
                .buildFillFor(HOME_BODY);
            var spotlit = createSplitFillBuilder()
                .traceFill(true, buildSolidOnlySplit(), REGION_KEY, Color.RED)
                .buildFillFor(HOME_BODY);

            // The carve traces its own rings and clips them to the frontier, so it cannot come
            // back as the frontier's own untouched tessellation the fast path produces.
            assertThat(spotlit.solidTriangles())
                .isNotEqualTo(solid.solidTriangles());
        }

        @Test
        void traceFillLeavesTheHatchEmptyWhenNoMemberIsHatched() {
            var fill = createSplitFillBuilder()
                .traceFill(true, buildSolidOnlySplit(), REGION_KEY, Color.RED)
                .buildFillFor(HOME_BODY);

            assertThat(fill.hatchSegments())
                .isEmpty();
        }
    }

    // This suite's subject is which cells each state fills, so a case asserts on the geometry that
    // comes back rather than on what the cuts along the way recorded.
    private static SplitFillBuilder createSplitFillBuilder() {
        return new SplitFillBuilder(
            EDGES,
            GROUPING,
            new ClusterBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT),
            HATCH);
    }

    // Both members held outright - the common case, and the one the solid fast path is for.
    private static FillSplit buildSolidOnlySplit() {
        return FillSplit.splitMembersByFillState(
            GROUPING,
            List.of(HELD_SYSTEM, HATCHED_SYSTEM),
            Set.of(),
            Set.of());
    }

    // One member solid, one hatched - the split the carve exists to draw.
    private static FillSplit buildHatchedSplit() {
        return FillSplit.splitMembersByFillState(
            GROUPING,
            List.of(HELD_SYSTEM, HATCHED_SYSTEM),
            Set.of(HATCHED_SYSTEM_ID),
            Set.of());
    }

    // An axis-aligned square ring, counter-clockwise, spanning [minX, minX + side] x
    // [minY, minY + side].
    private static List<double[]> buildSquare(double minX, double minY, double side) {
        return List.of(
            new double[] {minX, minY},
            new double[] {minX + side, minY},
            new double[] {minX + side, minY + side},
            new double[] {minX, minY + side});
    }

    // Sums the unsigned area of every triangle in a flat [x, y, x, y, ...] soup, six floats per
    // triangle - what the fill actually covers.
    private static double computeTotalTriangleArea(float[] triangles) {
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
