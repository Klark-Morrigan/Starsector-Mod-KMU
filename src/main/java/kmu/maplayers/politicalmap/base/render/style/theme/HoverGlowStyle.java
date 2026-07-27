package kmu.maplayers.politicalmap.base.render.style.theme;

/**
 * The halo the hovered territory's frontier blooms with - the outer edge of the contiguous
 * region the cursor is inside, so the player reads how far that territory reaches without
 * the map lighting up its interior seams.
 *
 * <p>A halo has no single width or alpha: it is several strokes of the same loop, widening
 * and fading outward until the accumulation reads as a soft edge rather than a thick line.
 * So the knobs describe the stack rather than one stroke - {@code width} is the widest
 * (outermost, faintest) layer in pixels and {@code opacity} the innermost layer's alpha,
 * with {@code layers} strokes spread between them. More layers buy a smoother falloff at a
 * stroke apiece.
 *
 * <p>The pulse is the halo breathing so a static screenshot and a live hover read
 * differently - {@code pulseStrength} is how much of the alpha it takes away at the trough
 * (0 leaves the halo steady) and {@code pulsePeriodSeconds} how long one breath takes.
 */
public record HoverGlowStyle(
        double opacity,
        double width,
        int layers,
        double pulseStrength,
        double pulsePeriodSeconds) {

    // A full breath is one cosine cycle, so the phase advances by this much per period.
    private static final double FULL_CYCLE_RADIANS = 2 * Math.PI;

    /**
     * How wide one layer of the stack strokes, in pixels - the innermost layer thinnest and
     * the outermost spanning the halo's full {@link #width}, spread evenly between.
     *
     * @param layer the layer's index, 0 (innermost) to {@code layers - 1} (outermost)
     * @return the stroke width for that layer
     */
    public double computeLayerWidth(int layer) {
        return width * (layer + 1) / layers;
    }

    /**
     * How opaque one layer of the stack draws at {@code timeSeconds} - {@link #opacity} at
     * the innermost layer, falling off linearly outward, and scaled by where the pulse has
     * reached.
     *
     * <p>Widening while fading is what makes the stack read as a halo rather than as one
     * thick line: each layer adds its light over a wider band than the last, so the
     * accumulation is brightest on the frontier and thins away from it.
     *
     * @param layer       the layer's index, 0 (innermost) to {@code layers - 1} (outermost)
     * @param timeSeconds elapsed time to phase the pulse by; any steadily advancing clock,
     *                    since only the difference between frames is read
     * @return the layer's alpha
     */
    public double computeLayerAlpha(int layer, double timeSeconds) {
        return opacity * (1.0 - (double) layer / layers) * computePulseScale(timeSeconds);
    }

    // How much of the halo's alpha survives at timeSeconds - 1 at the crest, down to
    // 1 - pulseStrength at the trough, on a cosine so the breath eases at both ends instead of
    // snapping between them. Folded into each layer's alpha rather than exposed, since the
    // pulse is not something to draw with on its own.
    private double computePulseScale(double timeSeconds) {
        if (pulseStrength <= 0 || pulsePeriodSeconds <= 0) {
            return 1;
        }
        // cos runs 1..-1, so this rides 0 (crest) to 1 (trough) and scales the strength into
        // the alpha it removes.
        var trough = 0.5 - 0.5 * Math.cos(FULL_CYCLE_RADIANS * timeSeconds / pulsePeriodSeconds);
        return 1 - pulseStrength * trough;
    }
}
