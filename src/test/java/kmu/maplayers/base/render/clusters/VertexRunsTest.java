package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlVertexRuns;

import kmu.maplayers.base.geometry.ShapedCell;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one cell-aware GL packing the renderer depends on: selecting a shaped cell's
 * edges of a single class (national border or interior seam) and flattening them into the
 * GL_LINES run the renderer strokes. The generic {@code {x, y}} to flat-array conversion
 * lives in {@link GlVertexRuns}; what is pinned here is the boundary/seam split and the
 * closing edge's wrap back to the first vertex.
 */
final class VertexRunsTest {

    // A triangle whose three edges are, in winding order: a border, a seam, then a border.
    // The final edge (v2 -> v0) wraps, so a run that keeps it proves the modulo close.
    private static final List<double[]> TRIANGLE =
            List.of(new double[] {0, 0}, new double[] {1, 0}, new double[] {2, 2});
    private static final boolean[] BORDER_SEAM_BORDER = {true, false, true};

    @Nested
    class FlattenEdgesOfClass {

        @Test
        void flattenEdgesOfClassPacksOnlyTheBoundaryEdgesWhenBoundaryIsWanted() {
            var shaped = new ShapedCell(TRIANGLE, BORDER_SEAM_BORDER);

            var run = VertexRuns.flattenEdgesOfClass(shaped, true);

            // Edge 0 (v0 -> v1) and the wrapping edge 2 (v2 -> v0); the seam edge 1 is
            // dropped.
            assertThat(run).containsExactly(0, 0, 1, 0, 2, 2, 0, 0);
        }

        @Test
        void flattenEdgesOfClassPacksOnlyTheSeamEdgesWhenBoundaryIsNotWanted() {
            var shaped = new ShapedCell(TRIANGLE, BORDER_SEAM_BORDER);

            var run = VertexRuns.flattenEdgesOfClass(shaped, false);

            // Only the seam edge 1 (v1 -> v2) survives.
            assertThat(run).containsExactly(1, 0, 2, 2);
        }

        @Test
        void flattenEdgesOfClassSizesTheRunToExactlyTheMatchingSegments() {
            var shaped = new ShapedCell(TRIANGLE, BORDER_SEAM_BORDER);

            var run = VertexRuns.flattenEdgesOfClass(shaped, true);

            // Two boundary edges, packed as one exact array with no slack - the two-pass
            // count exists precisely so the run is never over-allocated.
            assertThat(run).hasSize(2 * GlVertexRuns.FLOATS_PER_SEGMENT);
        }

        @Test
        void flattenEdgesOfClassReturnsAnEmptyRunWhenNoEdgeMatches() {
            var shaped = new ShapedCell(TRIANGLE, new boolean[] {false, false, false});

            var run = VertexRuns.flattenEdgesOfClass(shaped, true);

            // No boundary edges: an empty run, so the renderer strokes nothing rather than
            // a zero-length array of stale coordinates.
            assertThat(run).isEmpty();
        }
    }
}
