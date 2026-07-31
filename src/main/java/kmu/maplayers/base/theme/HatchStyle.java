package kmu.maplayers.base.theme;

/**
 * The sector-wide hatch pattern that ground in the
 * {@link kmu.maplayers.base.render.clusters.FillSplit.FillState#HATCHED} state is cut with, so
 * it reads apart from the solid fill beside it while staying the same colour. One pattern for
 * the whole sector, so this is a global-tier value (part of {@link GlobalStyle}) rather than
 * something that varies per territory: {@code spacing} is the perpendicular gap between lines
 * in world units, {@code angleRadians} their direction, and {@code width} the pixel stroke of
 * each line.
 */
public record HatchStyle(
    double spacing,
    double angleRadians,
    double width) {
}
