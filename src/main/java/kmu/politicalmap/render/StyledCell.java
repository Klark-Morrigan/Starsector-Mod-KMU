package kmu.politicalmap.render;

import java.awt.Color;

/**
 * A cell ready to draw: its flattened fill polygon and its national-border and
 * interior-seam edge runs (GL_LINES segments), plus the resolved fill/outer/inner
 * colors (null for a "No color" choice, which skips that element) with their
 * opacities and the two border widths. All per cell, since colors resolve against
 * each cell's palette and widths differ by category.
 */
record StyledCell(float[] fill, float[] boundaryEdges, float[] interiorEdges,
        Color fillColor, Color outerColor, Color innerColor,
        float fillAlpha, float outerAlpha, float innerAlpha,
        float outerWidth, float innerWidth) {
}
