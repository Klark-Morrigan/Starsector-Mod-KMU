package kmu.maplayers.base.render.regions;

import kmlib.starsector.ui.render.gl.UiElementPaint;

/**
 * One cell ready to draw - in whichever of the two forms a cell takes, named.
 *
 * <p>A cell that fused into a region ({@link FusedCell}) has no fill or outline of its own:
 * both are drawn once for the whole region, from the region's own tessellated shape, so all
 * the cell contributes is the seam where it meets a sibling. A cell that fused with nothing
 * ({@link LoneCell}) is its own region, so it carries its own fill and outline and has no
 * seam - there is no sibling on any side of it to divide from.
 *
 * <p>Two types rather than one record carrying both halves, because each half is meaningless
 * for the other form. Spelled as one record, a fused cell has to pass a fill it will never
 * draw and a lone cell a seam it does not have, and the only way to say "not this one" is a
 * hidden paint over an empty run - an absence the reader has to infer from a null colour, and
 * a slot every later part of a cell would have to be padded into as well. Named apart, what a
 * cell has is what it carries, and the pass that fills cannot reach a fused cell's fill
 * because there is none to reach.
 *
 * <p>Geometry arrives pre-flattened either way: a fill as a triangle soup rather than a vertex
 * ring, so emitting one assumes nothing about the ring's convexity, and edges as GL_LINES
 * segments. Paints and widths are per cell, since a colour resolves against that cell's own
 * palette and widths differ by what the cell is.
 */
public sealed interface StyledCell {

    /**
     * A cell fused into a region: it draws only the seam edges where it meets a sibling in
     * that region, at the seam's own paint and width. The fill and the border belong to the
     * region and are drawn from the region's shape rather than from this cell's.
     *
     * @param seamEdges the seam segments this cell contributes, as GL_LINES vertices
     * @param seamPaint the colour and opacity the seams stroke at
     * @param seamWidth the seam line width in pixels
     */
    record FusedCell(
            float[] seamEdges,
            UiElementPaint seamPaint,
            float seamWidth)
            implements StyledCell {
    }

    /**
     * A cell that fused with nothing and so stands as its own region: it draws its own fill
     * and its own outline, and has no seam. The fill is triangulated from the same ring the
     * outline strokes, so a rounded outline and its fill cannot drift apart at the corners.
     *
     * <p>Either element may still be hidden by the player's own choice - ground that is
     * outlined but not filled is the ordinary case - which is what its paint reports, while
     * the geometry stays baked to shape the cells around it.
     *
     * @param fillTriangles the fill as a GL_TRIANGLES soup; empty when nothing fills
     * @param outlineEdges  the outline as GL_LINES segments
     * @param fillPaint     the colour and opacity the fill paints at
     * @param outlinePaint  the colour and opacity the outline strokes at
     * @param outlineWidth  the outline line width in pixels
     */
    record LoneCell(
            float[] fillTriangles,
            float[] outlineEdges,
            UiElementPaint fillPaint,
            UiElementPaint outlinePaint,
            float outlineWidth) implements StyledCell {
    }
}
