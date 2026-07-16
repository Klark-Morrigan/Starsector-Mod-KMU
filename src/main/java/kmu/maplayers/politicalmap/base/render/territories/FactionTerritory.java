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
 * <p>The footprint's interior divisions - the solid<->hatched transitions included - carry no
 * geometry here: each is drawn by its own cell as an interior seam ({@link StyledCell}), which
 * the cell shaper has already truncated where it runs into a pulled-in border, so no division
 * reaches the raw cell corner out in the border channel.
 */
public record FactionTerritory(
        float[] fillTriangles,
        float[] hatchSegments,
        UiElementPaint fill,
        List<float[]> borderLoops,
        UiElementPaint border,
        float borderWidth) {
}
