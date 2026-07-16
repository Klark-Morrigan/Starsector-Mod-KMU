package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.ui.render.gl.UiElementPaint;

import java.util.List;

/**
 * One owned faction's fill and national border, both the same GLU-resolved region of the border
 * rings so they match exactly. {@code fillTriangles} is the solid part of that region as a
 * GL_TRIANGLES soup ([x, y, x, y, ...], empty when the fill is "No color"); {@code borderLoops}
 * is the whole region's boundary as GL_LINE_LOOP runs (empty when the border is "No color") -
 * one loop per disjoint cluster and per enclave, with any narrow-neck self-crossing resolved
 * away. Each {@link UiElementPaint} carries that element's color and opacity and reports whether
 * it is hidden, so the draw pass skips what shows nothing.
 *
 * <p>The political-map filter's spotlighted bloc splits its one bordered footprint into two
 * fills painted in the same {@link #fill} colour and opacity: {@code fillTriangles} covers the
 * systems it dominates (solid), and {@code hatchSegments} - a {@code GL_LINES} run of diagonal
 * lines pre-clipped to the contested systems' tessellated region and baked once at build time -
 * covers the systems it is merely present in (hatched, reading as "mine but contested"). Every
 * other territory sets {@code hatchSegments} empty and paints only its solid triangles. The two
 * fills tile the footprint inside one frontier, so its border stays a single continuous outline
 * either way. The width the hatch strokes at is sector-wide, so it lives on the theme's global
 * tier ({@link kmu.maplayers.politicalmap.base.render.style.GlobalStyle}) and the renderer sets it
 * once, not per territory.
 *
 * <p>{@code contestedSeams} marks every interior edge inside that footprint that touches a
 * contested cell - both where the solid and hatched fills meet and where two hatched cells join -
 * as a {@code GL_LINES} run stroked in {@code contestedSeam}'s (secondary-shade) colour and
 * {@code contestedSeamWidth}, so each contested pocket reads as a bounded region and two joined
 * hatched cells stay distinct rather than fusing into one blob. Dominant cells carry no such seam,
 * so the solid region stays one merged nation. It is empty for every non-spotlit territory (whose
 * fill is uniform) and for a spotlit footprint with no contested systems.
 */
public record FactionTerritory(float[] fillTriangles, float[] hatchSegments, UiElementPaint fill,
        float[] contestedSeams, UiElementPaint contestedSeam, float contestedSeamWidth,
        List<float[]> borderLoops, UiElementPaint border, float borderWidth) {
}
