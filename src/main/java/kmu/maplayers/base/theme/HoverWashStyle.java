package kmu.maplayers.base.theme;

/**
 * The lift over the single cell the cursor is inside - the one star system a hover is
 * ultimately about, named exactly rather than by the cluster around it.
 *
 * <p>Two elements because a cell has to read both ways: {@code fillOpacity} brightens its
 * whole painted extent, which is what carries at a glance, while the outline at
 * {@code outlineOpacity} and {@code outlineWidth} traces its full boundary, which is the
 * only cue an interior cell has - one surrounded by its own grouping on every side draws no
 * border of its own, so without the trace its edges would be invisible however bright the
 * wash. Either element can be dialed to nothing on its own.
 */
public record HoverWashStyle(
    double fillOpacity,
    double outlineOpacity,
    double outlineWidth) {
}
