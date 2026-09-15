package kmu.maplayers.base.theme;

import kmlib.opengl.hatch.HatchPattern;

/**
 * The sector-wide hatch a fill in the
 * {@link kmu.maplayers.base.render.clusters.FillSplit.FillState#HATCHED} state is cut with, so
 * it reads apart from the solid fill beside it while staying the same colour. One hatch for
 * the whole sector, so this is a global-tier value (part of {@link GlobalStyle}) rather than
 * something that varies per cluster.
 *
 * <p>Two halves, split by when each is decided. The pattern shapes the clipped line geometry and
 * is baked into the drawables, so changing it costs a rebuild; the stroke is read at the emit, so
 * changing it costs a frame. Each half is its own value rather than both being flattened into one
 * record of five components, which is what lets the cut take the pattern alone and never hold a
 * per-frame number it could come to bake by mistake.
 *
 * <p>Holding the two as one value is what keeps "the hatch" a single thing to pass around and to
 * read a capture against, while every reader below still takes only the half it acts on.
 *
 * @param pattern the line family the hatched area is cut with
 * @param stroke  how the resulting primitives are put on screen
 */
public record HatchStyle(
    HatchPattern pattern,
    HatchStroke stroke) {
}
