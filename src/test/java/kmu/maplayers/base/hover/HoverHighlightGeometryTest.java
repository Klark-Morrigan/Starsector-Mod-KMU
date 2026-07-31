package kmu.maplayers.base.hover;

import kmu.maplayers.base.theme.ElementPaint;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the contract of {@link HoverHighlightGeometry#resolveHighlightFor}:
 *  - a hovered cell resolves the one candidate loop that encloses it, and never the others,
 *  - nested loops resolve the innermost - the cell's own cluster - rather than the distant
 *    one that also happens to contain it,
 *  - a cell no loop encloses still washes, with no halo,
 *  - nothing hovered, or a cell with no drawable shape, resolves nothing,
 *  - a resting cursor resolves once and reuses the answer until the geometry under it changes,
 *    tracked by the identity of what the source handed back rather than of the source itself.
 *
 * <p>The fixtures are hand-built squares standing in for cells and traced border loops: the
 * search reads only the polygons and runs it is handed, so no shaping, styling, or GL is
 * involved in which loop it picks.
 */
final class HoverHighlightGeometryTest {
    private static final String CELL_ID = "A";

    @Nested
    class ResolveHighlightFor {
        @Test
        void a_hovered_cell_resolves_the_loop_that_encloses_it() {
            var loop = squareRun(0, 0, 100);
            var sourceFake = sourceOf(square(10, 10, 80), List.of(loop));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops()).containsExactly(loop);
        }

        @Test
        void a_distant_candidate_never_glows_for_a_cell_it_does_not_enclose() {
            // One cluster, two disjoint clusters - the whole reason the loop is searched for
            // rather than taken as "the cluster's border".
            var hoveredLoop = squareRun(0, 0, 100);
            var distantLoop = squareRun(500, 0, 100);
            var sourceFake = sourceOf(
                    square(10, 10, 80),
                    List.of(distantLoop, hoveredLoop));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops()).containsExactly(hoveredLoop);
        }

        @Test
        void nested_loops_resolve_the_innermost_one_around_the_cell() {
            // The cluster's enclave, walled inside a rival that is itself walled inside the
            // cluster's own cluster: three of its loops enclose the cell, and only the tightest
            // is the cluster the cell actually belongs to.
            var outerCluster = squareRun(0, 0, 1000);
            var enclaveInsideRival = squareRun(400, 400, 100);
            var sourceFake = sourceOf(
                    square(410, 410, 80),
                    List.of(outerCluster, enclaveInsideRival));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops()).containsExactly(enclaveInsideRival);
        }

        @Test
        void a_cell_fully_inside_its_frontier_washes_its_whole_extent() {
            // The clip must be a no-op for an interior cell: it washes its full 80x80 area (6400),
            // not a clamped-down piece, so only cells that reach the frontier are ever trimmed.
            var sourceFake = sourceOf(square(10, 10, 80), List.of(squareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(totalTriangleArea(highlight.washTriangles()))
                    .isCloseTo(6400.0, within(1e-2));
        }

        @Test
        void a_cell_poking_past_the_frontier_washes_only_up_to_it() {
            // The shaped cell reaches past the frontier that encloses its centre - the corner the
            // border's rounding cut, which the raw cell keeps. The wash must clamp to the loop, so
            // the overlap [50,100]x[50,100] (area 2500) washes, not the whole 80x80 cell (6400).
            var sourceFake = sourceOf(square(50, 50, 80), List.of(squareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(totalTriangleArea(highlight.washTriangles()))
                    .isCloseTo(2500.0, within(1e-2));
            assertThat(highlight.washOutline()).hasSize(1);
        }

        @Test
        void a_cell_with_no_candidate_loops_washes_with_no_halo() {
            // Ground that fuses into no cluster, or whose cluster traced no border at all: there is
            // no frontier to bloom - but the cell itself is still what the cursor is on.
            var sourceFake = sourceOf(square(10, 10, 80), List.of());

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops()).isEmpty();
            assertThat(highlight.washOutline()).isNotEmpty();
            assertThat(highlight.washTriangles()).isNotEmpty();
        }

        @Test
        void a_cell_no_candidate_encloses_washes_with_no_halo() {
            // Candidates exist but the cell sits outside every one of them, which reads the same
            // as having none: the cell washes and nothing haloes.
            var sourceFake = sourceOf(square(10, 10, 80), List.of(squareRun(500, 500, 100)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops()).isEmpty();
            assertThat(highlight.washOutline()).isNotEmpty();
        }

        @Test
        void nothing_hovered_lights_nothing_up() {
            var sourceFake = sourceOf(square(10, 10, 80), List.of(squareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, MapHover.NONE);

            assertThat(highlight.isEmpty()).isTrue();
        }

        @Test
        void a_hovered_cell_with_no_drawable_shape_lights_nothing_up() {
            var sourceFake = sourceOf(List.of(), List.of(squareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.isEmpty()).isTrue();
        }

        @Test
        void a_resting_cursor_reuses_the_answer_it_already_resolved() {
            // The whole point of the memo: this runs every frame, and re-tracing the same loops
            // sixty times a second for an answer that cannot have changed is pure waste.
            var sourceFake = sourceOf(square(10, 10, 80), List.of(squareRun(0, 0, 100)));
            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(sourceFake, hoverOf(CELL_ID));
            var second = geometry.resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(second).isSameAs(first);
        }

        @Test
        void a_fresh_source_over_unchanged_geometry_still_reuses_the_answer() {
            // The memo keys on the extent and the loops the source handed back, not on the source
            // itself - which is what lets a layer wrap its current draw lists afresh each frame
            // without costing a re-trace on every one of them.
            var paintedExtent = square(10, 10, 80);
            var frontierLoops = List.of(squareRun(0, 0, 100));
            var geometry = new HoverHighlightGeometry();
            var first = geometry.resolveHighlightFor(
                    sourceOf(paintedExtent, frontierLoops), hoverOf(CELL_ID));

            var second = geometry.resolveHighlightFor(
                    sourceOf(paintedExtent, frontierLoops), hoverOf(CELL_ID));

            assertThat(second).isSameAs(first);
        }

        @Test
        void a_rebuilt_cell_resolves_again_rather_than_tracing_a_shape_that_is_gone() {
            var geometry = new HoverHighlightGeometry();
            var first = geometry.resolveHighlightFor(
                    sourceOf(square(10, 10, 80), List.of(squareRun(0, 0, 100))),
                    hoverOf(CELL_ID));

            // An incremental re-shape replaces the cell's extent and its cluster's loops; the
            // retained answer describes geometry the map no longer paints.
            var reshapedLoop = squareRun(0, 0, 60);
            var second = geometry.resolveHighlightFor(
                    sourceOf(square(5, 5, 50), List.of(reshapedLoop)),
                    hoverOf(CELL_ID));

            assertThat(second).isNotSameAs(first);
            assertThat(second.glowLoops()).containsExactly(reshapedLoop);
        }
    }

    private static MapHover hoverOf(String cellId) {
        return new MapHover(cellId, List.of(cellId));
    }

    // A source answering for the one hovered cell every case here uses.
    private static HoverHighlightSourceFake sourceOf(
            List<double[]> paintedExtent,
            List<float[]> frontierLoops) {
        return new HoverHighlightSourceFake(
                Map.of(CELL_ID, paintedExtent),
                Map.of(CELL_ID, frontierLoops));
    }

    // An axis-aligned square, counter-clockwise, spanning [minX, minX + side] x
    // [minY, minY + side] - a stand-in cell shape or traced loop, which the search reads only
    // as an area that does or does not enclose a point.
    private static List<double[]> square(double minX, double minY, double side) {
        return List.of(
                new double[] {minX, minY},
                new double[] {minX + side, minY},
                new double[] {minX + side, minY + side},
                new double[] {minX, minY + side});
    }

    // Sums the unsigned area of every triangle in a flat [x, y, x, y, ...] soup, six floats per
    // triangle - the area the wash actually covers, for asserting the clip clamped it.
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

    // The same square as the baked [x, y, x, y, ...] run a border loop is kept in.
    private static float[] squareRun(float minX, float minY, float side) {
        return new float[] {
            minX, minY,
            minX + side, minY,
            minX + side, minY + side,
            minX, minY + side};
    }

    // A layer's answers as two plain lookups, handing back the very instances it was built with
    // so the memo's identity comparison is exercised exactly as a real layer's would be. The
    // highlight colour is a constant: the geometry never reads it.
    private record HoverHighlightSourceFake(
            Map<String, List<double[]>> paintedExtentByCellId,
            Map<String, List<float[]>> frontierLoopsByCellId) implements HoverHighlightSource {

        @Override
        public List<float[]> resolveCandidateFrontierLoopsOf(String cellId) {
            return frontierLoopsByCellId.getOrDefault(cellId, List.of());
        }

        @Override
        public Color resolveHighlightColourOf(String cellId, ElementPaint paletteChoice) {
            return Color.RED;
        }

        @Override
        public List<double[]> resolvePaintedExtentOf(String cellId) {
            return paintedExtentByCellId.getOrDefault(cellId, List.of());
        }
    }
}
