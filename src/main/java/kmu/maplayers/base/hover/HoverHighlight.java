package kmu.maplayers.base.hover;

import kmlib.opengl.GlVertexRuns;

import java.util.List;

/**
 * The geometry one highlight lights up: what to bloom, and what to lift under it.
 *
 * <p>Every run is world-coordinate and GL-ready, resolved from geometry the map already baked.
 * {@code washTriangles} is what the highlight covers as a {@code GL_TRIANGLES} soup and
 * {@code washOutline} the boundary of that same region as {@code GL_LINE_LOOP} rings - more than
 * one where the region is clipped or joined into disjoint pieces, or where it encloses a hole.
 * {@code glowLoops} are the {@code GL_LINE_LOOP} runs the halo stacks on, which the two callers
 * answer differently: a cursor blooms the frontier of the cluster it landed in, saying whose space
 * that is, while a lit set blooms its own joined outline, saying how far the set reaches. Empty
 * when there is nothing to bloom at all - a cell that fuses into no cluster has no frontier.
 *
 * <p>The wash is always clamped to the frontier it sits inside, not the raw cells: at the cluster
 * edge a shaped cell keeps the sharp mitered corner the border's rounding cut away, so washing it
 * raw would spill past the rounded border. Clipping stops the wash exactly where the border
 * strokes.
 *
 * <p>A triangle soup rather than a fan over the vertices: a cell is not reliably convex - a
 * keep-out pocket bitten out of a frontier cell leaves a concave notch, and a fan would paint
 * straight across it, washing map the cell does not cover.
 */
public record HoverHighlight(
    List<float[]> glowLoops,
    float[] washTriangles,
    List<float[]> washOutline) {

    /** Nothing is lit, or nothing lit has a drawable shape, so nothing lights up. */
    public static final HoverHighlight NONE = new HoverHighlight(
        List.of(),
        GlVertexRuns.NO_VERTICES,
        List.of());

    public HoverHighlight {
        glowLoops = List.copyOf(glowLoops);
        washOutline = List.copyOf(washOutline);
    }

    /**
     * @return whether there is anything to draw at all, the one test the highlight pass gates
     *         its GL state push on
     */
    public boolean isEmpty() {
        return glowLoops.isEmpty() && washOutline.isEmpty();
    }
}
