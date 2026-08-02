package kmu.maplayers.base.render.clusters;

import kmlib.starsector.ui.render.gl.UiElementPaint;

import java.util.List;

/**
 * One owner's drawn footprint ready to draw: its fill and the border around it. The border is
 * the GLU-resolved cluster of the traced rings; a footprint that fills solid throughout has
 * {@code fillTriangles} as that same cluster triangulated, so the two match exactly, while a
 * split footprint's fills are each clipped to that cluster (see below), so their outer edge lands
 * on it too. {@code fillTriangles} is the solid part as a GL_TRIANGLES soup
 * ([x, y, x, y, ...], empty when nothing fills); {@code borderLoops} is the whole footprint's
 * boundary as GL_LINE_LOOP runs (empty when nothing strokes) - one loop per disjoint cluster and
 * per enclave, with any narrow-neck self-crossing resolved away. Each {@link UiElementPaint}
 * carries that element's colour and opacity and reports whether it is hidden, so the draw pass
 * skips what shows nothing.
 *
 * <p>The cluster-level sibling of {@link StyledCell}: a cell that fused into a cluster keeps only
 * the seam it contributes, and everything drawn once for the whole fused body - the fill, the
 * hatch, the border rings and their paints - is here. A cell that fused with nothing needs none
 * of this and carries its own fill and outline instead.
 *
 * <p>An owner whose ground does not all fill solid splits its one bordered footprint into up to
 * three {@linkplain FillSplit.FillState fill states} inside that one border, painted in the same
 * {@link #fill} colour and opacity: {@code fillTriangles} covers the ground in the solid state,
 * {@code hatchSegments} - a {@code GL_LINES} run of diagonal lines pre-clipped to the hatched
 * sub-cluster and baked once at build time - covers the hatched ground, and the unfilled ground
 * carries no geometry at all, painting nothing so it reads empty inside the border. Each drawn
 * state's cluster is clipped to the border, so both stop at the same line the border strokes, and
 * the states tile the footprint inside one border so it stays a single continuous outline
 * whichever states it carries. Which ground lands in which state is the layer's own call; a
 * footprint that fills solid throughout carries an empty hatch run and paints only its triangles.
 *
 * <p>The width the hatch strokes at is sector-wide rather than per footprint, so it lives on the
 * theme's global tier ({@link kmu.maplayers.base.theme.GlobalStyle}) and is bound once for the
 * frame.
 *
 * <p>The footprint's interior divisions - the transitions between fill states included - carry no
 * geometry here: each is drawn by its own cell as an interior seam ({@link StyledCell}), which
 * the cell shaper has already truncated where it runs into a pulled-in border, so no division
 * reaches the raw cell corner out in the border channel.
 */
public record StyledCluster(
    float[] fillTriangles,
    float[] hatchSegments,
    UiElementPaint fill,
    List<float[]> borderLoops,
    UiElementPaint border,
    float borderWidth) {
}
