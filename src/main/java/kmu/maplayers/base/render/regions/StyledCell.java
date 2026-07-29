package kmu.maplayers.base.render.regions;

import kmlib.starsector.ui.render.gl.UiElementPaint;

/**
 * A cell ready to draw: its fill as a pre-tessellated triangle soup and its cluster-border
 * and interior-seam edge runs (GL_LINES segments), plus the fill/outer/inner
 * {@link UiElementPaint}s (each a resolved color and opacity that reports whether it is
 * hidden) and the two border widths. All per cell, since colors resolve against each
 * cell's palette and widths differ by category.
 *
 * <p>The fill is triangulated here rather than left as a vertex ring so the renderer stays a
 * flat GL_TRIANGLES emit that makes no assumption about the ring's convexity - the same
 * shape a cluster's fill takes, and the same shape the cell's own outline strokes, so a
 * rounded outline and its fill cannot drift apart at the corners.
 */
public record StyledCell(
        float[] fillTriangles,
        float[] boundaryEdges,
        float[] interiorEdges,
        UiElementPaint fillPaint,
        UiElementPaint outer,
        UiElementPaint inner,
        float outerWidth,
        float innerWidth) {
}
