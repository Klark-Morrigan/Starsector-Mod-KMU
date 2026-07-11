package kmu.maplayers.politicalmap.base.render.model;

import kmlib.starsector.ui.render.gl.UiElementPaint;

import java.util.List;

/**
 * One owned faction's fill and national border, both the same GLU-resolved region
 * of the border rings so they match exactly. {@code fillTriangles} is that region as
 * a GL_TRIANGLES soup ([x, y, x, y, ...], empty when the fill is "No color");
 * {@code borderLoops} is its boundary as GL_LINE_LOOP runs (empty when the border is
 * "No color") - one loop per disjoint cluster and per enclave, with any narrow-neck
 * self-crossing resolved away. Each {@link UiElementPaint} carries that element's color
 * and opacity and reports whether it is hidden, so the draw pass skips what shows
 * nothing.
 *
 * <p>{@code fillStyle} says how that fill region is painted - {@link FillStyle#SOLID} for every
 * territory but the political-map filter's contested cluster, which is {@link FillStyle#HATCHED}.
 * The border stays solid either way; only the interior differs. {@code hatchSegments} is that
 * hatch as a {@code GL_LINES} run ([x, y, x, y, ...]), the diagonal lines clipped to the fill
 * region and baked once at build time so the per-frame draw only strides them; it is empty for a
 * {@link FillStyle#SOLID} territory, which paints its {@code fillTriangles} instead. Both carry
 * the same {@link #fill} colour and opacity, so a hatched cluster reads in the spotlighted bloc's
 * palette exactly as a solid one would.
 */
public record FactionTerritory(float[] fillTriangles, UiElementPaint fill, FillStyle fillStyle,
        float[] hatchSegments, List<float[]> borderLoops, UiElementPaint border,
        float borderWidth) {
}
