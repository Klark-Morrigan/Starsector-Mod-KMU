package kmu.maplayers.base.theme;

import kmlib.opengl.GlLineQuality;

/**
 * A hatch handed to the GL line rasteriser: each clipped segment is stroked as a line, at a width
 * the driver measures in screen pixels.
 *
 * <p>Screen pixels is not a choice this stroke makes - {@code glLineWidth} has no other unit - so a
 * hatch stroked this way keeps the same weight however far the map is zoomed, and thickens
 * relative to the territory under it as that territory shrinks.
 *
 * @param quality     whether the lines are antialiased; a dense field of short strokes is the
 *                    case smoothing costs the most and flatters the least, so the hatch chooses
 *                    rather than inheriting whatever pass it draws inside
 * @param widthPixels how wide each line strokes, in screen pixels - what tunes the hatched
 *                    texture apart from the solid fill beside it
 */
public record GlLineHatchStroke(
    GlLineQuality quality,
    double widthPixels) implements HatchStroke {
}
