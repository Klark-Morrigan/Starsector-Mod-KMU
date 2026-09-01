package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquare;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquareRun;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.computeTotalTriangleArea;

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

            var loop = buildSquareRun(0, 0, 100);

            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(loop));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .containsExactly(loop);
        }

        @Test
        void a_distant_candidate_never_glows_for_a_cell_it_does_not_enclose() {
            // One cluster, two disjoint clusters - the whole reason the loop is searched for
            // rather than taken as "the cluster's border".
            var hoveredLoop = buildSquareRun(0, 0, 100);
            var distantLoop = buildSquareRun(500, 0, 100);

            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(distantLoop, hoveredLoop));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .containsExactly(hoveredLoop);
        }

        @Test
        void nested_loops_resolve_the_innermost_one_around_the_cell() {
            // A group's enclave, walled inside a rival that is itself walled inside another
            // cluster of that same group: three of its loops enclose the cell, and only the
            // tightest is the cluster the cell actually belongs to.
            var outerCluster = buildSquareRun(0, 0, 1000);
            var enclaveInsideRival = buildSquareRun(400, 400, 100);

            var sourceFake = readSourceOf(
                buildSquare(410, 410, 80),
                List.of(outerCluster, enclaveInsideRival));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .containsExactly(enclaveInsideRival);
        }

        @Test
        void nested_loops_resolve_the_innermost_one_whichever_order_they_arrive_in() {
            // The same nesting with the tight loop met first: a group hands its loops over in
            // whatever order it traced them, so the rule has to be "the smallest that encloses"
            // rather than "the last one found to".
            var outerCluster = buildSquareRun(0, 0, 1000);
            var enclaveInsideRival = buildSquareRun(400, 400, 100);

            var sourceFake = readSourceOf(
                buildSquare(410, 410, 80),
                List.of(enclaveInsideRival, outerCluster));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .containsExactly(enclaveInsideRival);
        }

        @Test
        void a_cell_fully_inside_its_frontier_washes_its_whole_extent() {
            // The clip must be a no-op for an interior cell: it washes its full 80x80 area (6400),
            // not a clamped-down piece, so only cells that reach the frontier are ever trimmed.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(6400.0, within(1e-2));
        }

        @Test
        void a_cell_poking_past_the_frontier_washes_only_up_to_it() {
            // The shaped cell reaches past the frontier that encloses its centre - the corner the
            // border's rounding cut, which the raw cell keeps. The wash must clamp to the loop, so
            // the overlap [50,100]x[50,100] (area 2500) washes, not the whole 80x80 cell (6400).
            var sourceFake = readSourceOf(
                buildSquare(50, 50, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(2500.0, within(1e-2));
            assertThat(highlight.washOutline())
                .hasSize(1);
        }

        @Test
        void a_cell_with_no_candidate_loops_washes_with_no_halo() {
            // A cell that fuses into no cluster, or whose cluster traced no border at all: there is
            // no frontier to bloom - but the cell itself is still what the cursor is on.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of());

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .isEmpty();
            assertThat(highlight.washOutline())
                .isNotEmpty();
            assertThat(highlight.washTriangles())
                .isNotEmpty();
        }

        @Test
        void a_cell_no_candidate_encloses_washes_with_no_halo() {
            // Candidates exist but the cell sits outside every one of them, which reads the same
            // as having none: the cell washes and nothing haloes.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(500, 500, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .isEmpty();
            assertThat(highlight.washOutline())
                .isNotEmpty();
        }

        @Test
        void nothing_hovered_lights_nothing_up() {

            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, MapHover.NONE);

            assertThat(highlight.isEmpty())
                .isTrue();
        }

        @Test
        void a_hovered_cell_with_no_drawable_shape_lights_nothing_up() {

            var sourceFake = readSourceOf(
                List.of(),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.isEmpty())
                .isTrue();
        }

        @Test
        void a_resting_cursor_reuses_the_answer_it_already_resolved() {
            // The whole point of the memo: this runs every frame, and re-tracing the same loops
            // sixty times a second for an answer that cannot have changed is pure waste.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(sourceFake, hoverOf(CELL_ID));
            var second = geometry.resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(second)
                .isSameAs(first);
        }

        @Test
        void a_fresh_source_over_unchanged_geometry_still_reuses_the_answer() {
            // The memo keys on the extent and the loops the source handed back, not on the source
            // itself - which is what lets a layer wrap its current draw lists afresh each frame
            // without costing a re-trace on every one of them.
            var paintedExtent = buildSquare(10, 10, 80);
            var frontierLoops = List.of(buildSquareRun(0, 0, 100));
            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(
                readSourceOf(paintedExtent, frontierLoops),
                hoverOf(CELL_ID));

            var second = geometry.resolveHighlightFor(
                readSourceOf(paintedExtent, frontierLoops),
                hoverOf(CELL_ID));

            assertThat(second)
                .isSameAs(first);
        }

        @Test
        void a_rebuilt_cell_resolves_again_rather_than_tracing_a_shape_that_is_gone() {

            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(
                readSourceOf(
                    buildSquare(10, 10, 80),
                    List.of(buildSquareRun(0, 0, 100))),
                hoverOf(CELL_ID));

            // An incremental re-shape replaces the cell's extent and its cluster's loops; the
            // retained answer describes geometry the map no longer paints.
            var reshapedLoop = buildSquareRun(0, 0, 60);

            var second = geometry.resolveHighlightFor(
                readSourceOf(
                    buildSquare(5, 5, 50),
                    List.of(reshapedLoop)),
                hoverOf(CELL_ID));

            assertThat(second)
                .isNotSameAs(first);
            assertThat(second.glowLoops())
                .containsExactly(reshapedLoop);
        }
    }

    private static MapHover hoverOf(String cellId) {
        return new MapHover(cellId, List.of(cellId));
    }

    // A source answering for the one hovered cell every case here uses, handing back the very
    // instances it was built with so the memo's identity comparison is exercised as a real
    // layer's answers would exercise it.
    private static HoverHighlightSourceFake readSourceOf(
            List<double[]> paintedExtent,
            List<float[]> frontierLoops) {

        return new HoverHighlightSourceFake(
            Map.of(CELL_ID, paintedExtent),
            Map.of(CELL_ID, frontierLoops));
    }

}
