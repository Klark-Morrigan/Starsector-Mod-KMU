package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.render.regions.StyledCell;

import java.util.List;

/**
 * One owned faction's fill and national border. The border is the GLU-resolved region of the
 * border rings; a territory that fills solid throughout has {@code fillTriangles} as that same
 * region triangulated, so the two match exactly, while a split territory's fills are each clipped
 * to that region (see below), so their outer edge lands on it too. {@code fillTriangles} is the
 * solid part as a GL_TRIANGLES soup ([x, y, x, y, ...], empty when the fill is "No color");
 * {@code borderLoops} is the whole region's boundary as GL_LINE_LOOP runs (empty when the border
 * is "No color") - one loop per disjoint cluster and per enclave, with any narrow-neck
 * self-crossing resolved away. Each {@link UiElementPaint} carries that element's color and
 * opacity and reports whether it is hidden, so the draw pass skips what shows nothing.
 *
 * <p>A bloc whose systems do not all fill solid splits its one bordered footprint into up to
 * three fill states inside that one frontier, painted in the same {@link #fill} colour and
 * opacity: {@code fillTriangles} covers the systems it holds solid, {@code hatchSegments} - a
 * {@code GL_LINES} run of diagonal lines pre-clipped to the contested systems' region and baked
 * once at build time - covers the systems it holds but does not dominate (hatched, reading as
 * "mine but contested"), and the held-but-unfilled systems carry no geometry at all, painting
 * nothing so their ground reads empty inside the border. The filter's spotlighted bloc is what
 * produces the hatched state today; every other territory sets {@code hatchSegments} empty and
 * paints only its solid triangles. Each drawn state's region is clipped to the national border, so
 * both stop at the same frontier the border strokes, and the states tile the footprint inside one
 * frontier so its border stays a single continuous outline whichever states it carries. The width
 * the hatch strokes at is sector-wide, so it lives on the theme's global tier
 * ({@link kmu.maplayers.base.style.GlobalStyle}) and the renderer sets it once,
 * not per territory.
 *
 * <p>The footprint's interior divisions - the transitions between fill states included - carry no
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
