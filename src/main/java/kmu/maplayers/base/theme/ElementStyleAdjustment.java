package kmu.maplayers.base.theme;

/**
 * The two orthogonal knobs that push one painted element into the background: the fraction of
 * its opacity it draws at, and whether it drops its own shades for the theme's desaturation
 * palette. Sits beside {@link ElementStyle} rather than inside it because a style is what the
 * player authored and an adjustment is what a rebuild decided - the same style draws receded on
 * one pass and untouched on the next, so the two cannot share a lifetime.
 *
 * <p>Held here rather than by whoever decides a subject recedes so the render code stays a pure
 * applier: it honours an adjustment without knowing why one was asked for. The framework owns
 * the shape and the combination rule; every reason an element recedes stays with the code that
 * has the reason.
 *
 * <p>The adjustment names no target colour - what {@code shouldDesaturate} maps to is the theme's
 * business (an authored profile), so a caller flags only <em>whether</em> its subject
 * desaturates and stays out of the profile decision. {@link #NONE} is the identity: draw the
 * element exactly as its style says.
 *
 * @param opacityMultiplier the fraction of its normal opacity the element draws at; 1.0 leaves
 *                          it unchanged
 * @param shouldDesaturate  whether the element recolours to the theme's desaturation palette
 *                          instead of its own shades
 */
public record ElementStyleAdjustment(
    double opacityMultiplier,
    boolean shouldDesaturate) {

    /**
     * The identity adjustment - full opacity, no desaturation - so an element draws exactly as
     * its style says. The value to return for a subject that is neither dimmed nor recoloured.
     */
    public static final ElementStyleAdjustment NONE =
        new ElementStyleAdjustment(1.0, false);

    /**
     * Unions this recede with another so an element that recedes for more than one reason dims and
     * desaturates once rather than compounding. Composing them multiplicatively would over-dim an
     * element every extra reason applies (each opacity multiplier stacking on the last); the union
     * instead takes the strongest mute (the smallest opacity multiplier) and the OR of desaturate,
     * so folding a reason in a second time is idempotent. The single home for "combine two
     * recedes", so every path that stacks them reads one rule.
     *
     * @param other the other recede to fold in
     * @return the combined recede: the smaller opacity multiplier, desaturate if either desaturates
     */
    public ElementStyleAdjustment mergeRecede(ElementStyleAdjustment other) {
        return new ElementStyleAdjustment(
            Math.min(opacityMultiplier, other.opacityMultiplier),
            shouldDesaturate || other.shouldDesaturate);
    }

    /**
     * Applies this adjustment's mute to one element's base opacity - the single home for "muting
     * scales opacity", so a fill, a border, a seam, and a name all dim by the one rule rather than
     * each site multiplying by hand. {@link #NONE}'s 1.0 multiplier returns the base unchanged.
     *
     * @param baseOpacity the element's own opacity before muting, from its style
     * @return the muted opacity as a float, ready for the GL paint
     */
    public float muteOpacity(double baseOpacity) {
        return (float) (baseOpacity * opacityMultiplier);
    }
}
