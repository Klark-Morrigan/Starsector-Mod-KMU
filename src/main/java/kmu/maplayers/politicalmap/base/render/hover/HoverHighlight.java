package kmu.maplayers.politicalmap.base.render.hover;

import kmlib.opengl.GlVertexRuns;

import java.util.List;

/**
 * The geometry one hover lights up: the frontier of the territory the cursor is inside, and
 * the single cell it is actually in.
 *
 * <p>Every run is world-coordinate and GL-ready, resolved from geometry the map already
 * baked. {@code glowLoops} are {@code GL_LINE_LOOP} runs - normally the one loop enclosing
 * the hovered cell, empty when the cell belongs to no faction (a factionless cell fuses into
 * no territory, so it has no frontier to bloom). {@code washTriangles} is the hovered cell's
 * painted extent as a {@code GL_TRIANGLES} soup and {@code washOutline} the same extent as a
 * {@code GL_LINE_LOOP} ring.
 *
 * <p>A triangle soup rather than a fan over the cell's vertices: a cell is not reliably
 * convex - a keep-out pocket bitten out of a frontier cell leaves a concave notch, and a fan
 * would paint straight across it, washing ground the cell does not cover.
 */
public record HoverHighlight(
        List<float[]> glowLoops,
        float[] washTriangles,
        float[] washOutline) {

    /** Nothing is hovered, or the hovered cell has no drawable shape, so nothing lights up. */
    public static final HoverHighlight NONE = new HoverHighlight(
            List.of(), GlVertexRuns.NO_VERTICES, GlVertexRuns.NO_VERTICES);

    public HoverHighlight {
        glowLoops = List.copyOf(glowLoops);
    }

    /**
     * @return whether there is anything to draw at all, the one test the highlight pass gates
     *         its GL state push on
     */
    public boolean isEmpty() {
        return glowLoops.isEmpty() && washOutline.length == 0;
    }
}
