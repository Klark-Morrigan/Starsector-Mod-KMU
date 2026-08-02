package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;

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
 * Pins which of the three fills a cluster actually pays for: nothing at all for a "No color"
 * fill, one tessellation of the frontier when every member fills solid, and a per-state carve
 * only when the cluster is spotlit or genuinely holds non-solid ground.
 *
 * <p>That fast path is the point - the common case is an owner that only fills solid, and it
 * must come out of the same smoothed loops the border strokes rather than out of a trace of
 * its own, or the fill and the border stop in different places.
 *
 * <p>And that each of an owner's bodies gets a fill confined to itself. One call covers every
 * body because the split's rings are the owner's rather than any one body's; the per-body clip
 * is what keeps a fill inside the body it belongs to, and it is the only thing that does.
 *
 * <p>The fixtures are two hand-built square cells side by side, sized well clear of the border
 * channel, so the areas below are checkable by hand.
 */
final class SplitFillBuilderTest {

    private static final String REGION_KEY = "cluster";
    private static final String HELD_SYSTEM = "A";
    private static final String HATCHED_SYSTEM = "B";
    private static final HatchStyle HATCH = new HatchStyle(200, Math.PI / 4, 1);
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;

    // Two cells of the one cluster, meeting along x = 2000: A spans [0, 2000], B spans [2000, 4000],
    // both 2000 tall. Their shared edge is a same-owner seam; every other edge is a border.
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

    // The body the fill is clipped to: the square [0, 1000] x [0, 1000], area 1e6, overlapping
    // the members' own ground.
    private static final RingRegion HOME_BODY = new RingRegion(
        square(0, 0, 1000),
        List.of());

    // A second body of the same owner, clear of the members' ground and half the size - so a
    // fill that leaked from one body into the other, or that was built against the two together,
    // is caught by area rather than by mere presence. Area 25e4.
    private static final RingRegion DISTANT_BODY =
        new RingRegion(square(10000, 0, 500), List.of());

    @Nested
    class BuildFills {

        @Test
        void buildFillsDrawNothingForANoColourFill() {
            // An owner the player has switched the fill off for pays no tessellation at all,
            // rather than baking triangles the draw pass would then skip - but still gets one
            // entry per body, since the bodies themselves still stroke and still hold a label.
            var fills = builder().buildFills(
                false, // Is not spotlit.
                solidOnlySplit(),
                REGION_KEY,
                null, // No fill color.
                List.of(HOME_BODY, DISTANT_BODY));

            assertThat(fills)
                .hasSize(2);
            assertThat(fills)
                .allSatisfy(fill -> {
                    assertThat(fill.solidTriangles()).isEmpty();
                    assertThat(fill.hatchSegments()).isEmpty();
            });
        }

        @Test
        void buildFillsTessellateTheFrontierWhenEveryMemberFillsSolid() {
            // The fast path: no trace of its own, just the smoothed loops the border strokes, so
            // the fill lands exactly where the border does.
            var fills = builder().buildFills(
                false, // Is not spotlit.
                solidOnlySplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY));

            assertThat(totalTriangleArea(fills.get(0).solidTriangles()))
                .isCloseTo(1e6, within(1.0));
            assertThat(fills.get(0).hatchSegments())
                .isEmpty();
        }

        @Test
        void buildFillsGiveEachBodyItsOwnAreaRatherThanTheOwnersCombinedOne() {
            // Two bodies of one owner, each filled from its own loops. Tessellated together, both
            // entries would carry the union - which is the error the flat record could not have
            // caught, since it held one soup over everything either way.
            var fills = builder().buildFills(
                false,
                solidOnlySplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY, DISTANT_BODY));

            assertThat(totalTriangleArea(fills.get(0).solidTriangles()))
                .isCloseTo(1e6, within(1.0));
            assertThat(totalTriangleArea(fills.get(1).solidTriangles()))
                .isCloseTo(25e4, within(1.0));
        }

        @Test
        void buildFillsCarvePerStateWhenTheOwnerHoldsHatchedGround() {
            // Non-solid members force the carve even off the spotlight, so solid and hatched
            // ground read apart inside the one frontier.
            var fills = builder().buildFills(
                false, // Is not spotlit.
                hatchedSplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY));

            assertThat(fills.get(0).solidTriangles())
                .isNotEmpty();
        }

        @Test
        void buildFillsConfineACarvedStateToTheBodyItsGroundSitsIn() {
            // The states are traced once across the whole owner, so nothing but the per-body clip
            // keeps the distant body from being painted with ground it does not contain. Its
            // fill has to come back empty: its own loops enclose none of the members.
            var fills = builder().buildFills(
                false, // Is not spotlit.
                hatchedSplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY, DISTANT_BODY));

            assertThat(fills.get(0).solidTriangles()).isNotEmpty();
            assertThat(fills.get(1).solidTriangles()).isEmpty();
            assertThat(fills.get(1).hatchSegments()).isEmpty();
        }

        @Test
        void buildFillsCarvePerStateForASpotlitOwnerThatFillsSolidThroughout() {
            // The spotlight always splits: its fill is per state even where it dominates
            // everywhere, so a spotlit owner does not fall into the solid fast path.
            var solid = builder().buildFills(
                false, // Is not spotlit.
                solidOnlySplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY));
                
            var spotlit = builder().buildFills(
                true, // Is spotlit.
                solidOnlySplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY));

            // The carve traces its own rings and clips them to the frontier, so it cannot come
            // back as the frontier's own untouched tessellation the fast path produces.
            assertThat(spotlit.get(0).solidTriangles())
                .isNotEqualTo(solid.get(0).solidTriangles());
        }

        @Test
        void buildFillsLeaveTheHatchEmptyWhenNoMemberIsHatched() {
            var fills = builder().buildFills(
                true, // Is spotlit.
                solidOnlySplit(),
                REGION_KEY,
                Color.RED,
                List.of(HOME_BODY));

            assertThat(fills.get(0).hatchSegments())
                .isEmpty();
        }
    }

    private static SplitFillBuilder builder() {
        return new SplitFillBuilder(
            EDGES,
            GROUPING,
            new ClusterBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT),
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
