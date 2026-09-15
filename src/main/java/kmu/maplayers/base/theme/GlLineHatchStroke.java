package kmu.maplayers.base.theme;

import kmlib.opengl.GlLineQuality;

/**
 * A hatch handed to the GL line rasteriser: each clipped segment is stroked as a line, at a width
 * the driver measures in screen pixels.
 *
 * <p>The width is carried as a fraction of the hatch spacing rather than as a pixel count, because
 * the spacing it has to read against is world-space geometry that the map scales by its zoom.
 * A fixed pixel width therefore covers an ever larger share of the gap as the map zooms out: past
 * the whole gap the hatch fills in solid, and just short of it the neighbouring strokes clip each
 * other's ends into shards. Stated as a fraction and multiplied by the frame's own scale, the share
 * of the gap the ink covers is the same at every zoom, and a fraction below one cannot close the
 * gap at any of them.
 *
 * <p>What that costs is that the hatch is a property of the cluster rather than of the screen - it
 * grows and shrinks with the body under it, as a printed map's hatching does under a magnifier -
 * and that far enough out the computed width falls under the one-pixel floor the
 * rasteriser can draw, below which the pattern coarsens back towards solid. Both are properties of
 * drawing the hatch as world-space lines at all; a substrate that wanted a screen-constant texture
 * would be a different {@link HatchStroke}.
 *
 * @param quality       whether the lines are antialiased; a dense field of short strokes is the
 *                      case smoothing costs the most and flatters the least, so the hatch chooses
 *                      rather than inheriting whatever pass it draws inside. Note that a smoothed
 *                      line is capped at a driver-defined width (commonly ten pixels) where an
 *                      aliased one is not, so a heavy hatch reads heavier hard-edged
 * @param widthFraction how wide each line strokes, as a fraction of the hatch spacing - what tunes
 *                      the hatched texture apart from the solid fill beside it. One would fill
 *                      solid, so the drawn range stops short of it
 */
public record GlLineHatchStroke(
    GlLineQuality quality,
    double widthFraction) implements HatchStroke {

    /**
     * The width this stroke asks the rasteriser for on one frame, in the screen pixels
     * {@code glLineWidth} measures.
     *
     * <p>Resolved here rather than at the emission because the fraction is this stroke's own unit:
     * the renderer knows the frame's scale and the pattern it is cutting, and should not also have
     * to know what a given substrate's width number means.
     *
     * @param spacing the hatch spacing the fraction is taken of, in world units
     * @param factor  the frame's world-to-map scale, the same one the segments are drawn under
     * @return the stroke width in screen pixels
     */
    public float computeWidthPixelsAt(double spacing, float factor) {
        return (float) (widthFraction * spacing * factor);
    }
}
