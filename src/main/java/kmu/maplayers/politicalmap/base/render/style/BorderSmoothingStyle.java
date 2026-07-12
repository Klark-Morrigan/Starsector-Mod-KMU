package kmu.maplayers.politicalmap.base.render.style;

/**
 * The sector-wide smoothing applied to every national border (and to a factionless cell's
 * lone outline, so it reads consistently): whether to sand thin spikes and whether to
 * round corners, plus the rounding shape - corner radius in world units, arc segments per
 * corner, and the chamfer angle in radians below which a sharp corner is cut flat instead
 * of arced. One profile for the whole map, so this is a global-tier value (part of
 * {@link GlobalStyle}); the two gates leave a raw Voronoi outline when off.
 */
public record BorderSmoothingStyle(boolean shouldSandSpikes, boolean shouldRoundCorners,
        double cornerRadius, int cornerSegments, double chamferAngleRadians) {
}
