package kmu.maplayers.base.theme;

/**
 * The sector-wide hatch pattern that fills the filter's contested territory - the
 * spotlighted bloc's present-but-dominated systems - so it reads as "mine, but contested"
 * against the solid space it holds outright. One pattern for the whole sector, so this is
 * a global-tier value (part of {@link GlobalStyle}), not something that varies per
 * territory: {@code spacing} is the perpendicular gap between lines in world units,
 * {@code angleRadians} their direction, and {@code width} the pixel stroke of each line.
 */
public record HatchStyle(double spacing, double angleRadians, double width) {
}
