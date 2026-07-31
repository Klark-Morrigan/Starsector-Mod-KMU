package kmu.maplayers.base.hover;

import kmlib.opengl.GlVertexRuns;

import java.util.List;

/**
 * The geometry one hover lights up: the frontier of the cluster the cursor is inside, and
 * the single cell it is actually in.
 *
 * <p>Every run is world-coordinate and GL-ready, resolved from geometry the map already
 * baked. {@code glowLoops} are {@code GL_LINE_LOOP} runs - normally the one loop enclosing
 * the hovered cell, empty when the cell belongs to no cluster (a cell that fuses into nothing
 * has no frontier to bloom). {@code washTriangles} is the hovered cell's painted extent as a
 * {@code GL_TRIANGLES} soup and {@code washOutline} the same extent as {@code GL_LINE_LOOP}
 * rings - one normally, but more where the extent is clipped to the frontier into disjoint
 * pieces.
 *
 * <p>Both wash runs are the cell clamped to the frontier it sits inside, not the raw cell:
 * at the cluster edge the shaped cell keeps the sharp mitered corner the border's rounding
 * cut away, so washing it raw would spill the wash past the rounded border. Clipping to the
 * frontier stops the wash exactly where the border strokes.
 *
 * <p>A triangle soup rather than a fan over the cell's vertices: a cell is not reliably
 * convex - a keep-out pocket bitten out of a frontier cell leaves a concave notch, and a fan
 * would paint straight across it, washing ground the cell does not cover.
 */
public record HoverHighlight(
    List<float[]> glowLoops,
    float[] washTriangles,
    List<float[]> washOutline) {

    /** Nothing is hovered, or the hovered cell has no drawable shape, so nothing lights up. */
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
