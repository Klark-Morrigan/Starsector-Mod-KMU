package kmu.maplayers.base.theme;

/**
 * The sector-wide hatch pattern that a fill in the
 * {@link kmu.maplayers.base.render.clusters.FillSplit.FillState#HATCHED} state is cut with, so
 * it reads apart from the solid fill beside it while staying the same colour. One pattern for
 * the whole sector, so this is a global-tier value (part of {@link GlobalStyle}) rather than
 * something that varies per cluster.
 *
 * <p>Split along when each part is decided. The first three components shape the geometry and are
 * baked into the drawables, so changing one costs a rebuild; the stroke is read at the emit, so
 * changing it costs a frame. Holding the two halves in one record is what keeps "the hatch" one
 * value to pass around while still letting each reader take only the half it acts on.
 *
 * @param spacing               the perpendicular gap between lines, in world units
 * @param angleRadians          the direction the lines run in
 * @param joinToleranceFraction how far apart two of one line's crossings may sit and still count
 *                              as the same stroke, as a fraction of the spacing
 * @param stroke                how the resulting primitives are put on screen
 */
public record HatchStyle(
    double spacing,
    double angleRadians,
    double joinToleranceFraction,
    HatchStroke stroke) {
}
