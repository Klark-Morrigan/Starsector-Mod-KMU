package kmu.maplayers.base.theme;

import kmlib.opengl.HatchJoining;

/**
 * The sector-wide hatch pattern that ground in the
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
 * @param spacing      the perpendicular gap between lines, in world units
 * @param angleRadians the direction the lines run in
 * @param joining      how many primitives one line's crossings of the hatched ground become
 * @param stroke       how those primitives are put on screen
 */
public record HatchStyle(
    double spacing,
    double angleRadians,
    HatchJoining joining,
    HatchStroke stroke) {
}
