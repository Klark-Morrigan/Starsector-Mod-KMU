package kmu.politicalmap.render.model;

/**
 * A cell ready to draw: its flattened fill polygon and its national-border and
 * interior-seam edge runs (GL_LINES segments), plus the fill/outer/inner
 * {@link ElementPaint}s (each a resolved color and opacity that reports whether it is
 * hidden) and the two border widths. All per cell, since colors resolve against each
 * cell's palette and widths differ by category.
 */
public record StyledCell(float[] fill, float[] boundaryEdges, float[] interiorEdges,
        ElementPaint fillPaint, ElementPaint outer, ElementPaint inner,
        float outerWidth, float innerWidth) {
}
