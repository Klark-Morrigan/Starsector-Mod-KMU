package kmu.maplayers.politicalmap.base.render.ribbon;

import java.awt.Color;

/**
 * One run of a cell's presence band as the renderer paints it: a colour and the triangles
 * covering the stretch of ring that run occupies.
 *
 * <p>Triangles rather than a wide line, because a band laid along world geometry turns corners:
 * a line primitive strokes each segment on its own and leaves a notch on the outside of every
 * turn, where a stroked band mitres through it. Baked at rebuild rather than emitted per frame,
 * since nothing about the geometry moves between rebuilds - which is what makes a band cost a
 * draw call rather than a walk.
 *
 * @param colour    the shade this run draws in: a bloc's bright colour for a market, its dark
 *                  colour for the parting between two of them
 * @param triangles the run's triangles as a flat {@code [x, y, x, y, ...]} world-coordinate run,
 *                  every three vertices one triangle
 */
public record RibbonBand(
    Color colour,
    float[] triangles) {
}
