package kmu.maplayers.base.hover;

import kmu.maplayers.base.theme.ElementPaint;

import java.awt.Color;
import java.util.List;

/**
 * The three things about a hovered cell only the layer that owns the map's regions can
 * answer: the extent it painted there, the border loops the cell might sit inside, and the
 * shade the ground under it draws in.
 *
 * <p>Each is a question about *this* frame's draw lists, so the answers are the geometry the
 * layer actually put on screen rather than a re-derivation of it - which is what keeps a
 * highlight from ever tracing a shape the map is not painting. The highlight pass owns the
 * halo and the wash on top of them, and owns nothing about who holds the ground.
 *
 * <p>Loops and extents are compared by identity downstream, so an implementation must return
 * the same instance for as long as the geometry behind it is unchanged, and a fresh one once
 * a rebuild or an incremental re-shape has replaced it. Handing back a defensive copy per
 * call would defeat the memoisation and re-trace every frame.
 */
public interface HoverHighlightSource {

    /**
     * The border loops the cell could sit inside - the layer's own traced regions, from which
     * the highlight picks the one that actually encloses it.
     *
     * @param cellId the hovered cell
     * @return the candidate loops as {@code [x, y, x, y, ...]} runs, empty when the cell
     *         fuses into no region or the region it fuses into traced no border
     */
    List<float[]> resolveCandidateFrontierLoopsOf(String cellId);

    /**
     * The shade the highlight burns in - the colour of the ground under the cursor, so the
     * halo and the wash say whose space this is.
     *
     * @param cellId        the hovered cell
     * @param paletteChoice which of the ground's palette shades the theme points the
     *                      highlight at
     * @return that shade, or null when the choice paints nothing, so the caller skips the
     *         whole pass
     */
    Color resolveHighlightColourOf(String cellId, ElementPaint paletteChoice);

    /**
     * The extent the layer painted for one cell - the shape the wash lifts.
     *
     * @param cellId the hovered cell
     * @return its painted extent as {x, y} vertex pairs in world coordinates, empty when the
     *         cell draws nothing at all
     */
    List<double[]> resolvePaintedExtentOf(String cellId);
}
