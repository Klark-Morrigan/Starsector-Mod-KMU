package kmu.maplayers.base.theme;

/**
 * The sector-wide smoothing applied to every cluster border (and to an unowned cell's
 * lone outline, so it reads consistently): whether to sand thin spikes and whether to round
 * corners, plus the shape each pass works to - the tallest protrusion sanding splices out and
 * the angle at which one counts as a spike, then the corner radius in world units, the arc
 * segments per corner, and the chamfer angle in radians below which a sharp corner is cut
 * flat instead of arced. One profile for the whole map, so this is a global-tier value (part
 * of {@link GlobalStyle}); the two gates leave a raw Voronoi outline when off.
 *
 * <p>Both passes' shape lives here rather than being fetched where the pass runs, so one
 * border and the cell outline beside it can never smooth to different numbers.
 */
public record BorderSmoothingStyle(
    boolean shouldSandSpikes,
    boolean shouldRoundCorners,
    double spikeHeight,
    double spikeAngleRadians,
    double cornerRadius,
    int cornerSegments,
    double chamferAngleRadians) {
}
