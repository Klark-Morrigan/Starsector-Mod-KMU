package kmu.maplayers.politicalmap.base;

/**
 * The two orthogonal styling knobs the shared political-map pipeline honours without knowing
 * why anything asked for them, so a caller can dim or recolour a piece of the map while the
 * render code stays a pure applier.
 *
 * <p>A view resolves one of these per bloc through
 * {@link PoliticalMapView#resolveBlocStyleAdjustment}, and ownerless ground takes one by its
 * own rule - a bloc is the usual subject, not the only one. {@code opacityMultiplier} scales
 * the subject's fills, borders, and name alpha; {@code desaturate} swaps its palette for the
 * shared desaturation-profile palette the pipeline resolves. The adjustment names no
 * target colour - what {@code desaturate} maps to is the pipeline's business (an authored
 * profile setting), so a caller flags only <em>whether</em> its subject desaturates and stays
 * out of the profile decision. {@link #NONE} is the identity: draw the subject exactly as its
 * style classification says.
 *
 * @param opacityMultiplier the fraction of its normal opacity the bloc's fills, borders,
 *                          and name draw at; 1.0 leaves them unchanged
 * @param desaturate        whether the bloc recolours to the pipeline's desaturation
 *                          palette instead of its owner's own shades
 */
public record BlocStyleAdjustment(double opacityMultiplier, boolean desaturate) {

    /**
     * The identity adjustment - full opacity, no desaturation - so a bloc draws exactly as
     * classified. The value every view returns for a bloc it neither dims nor recolours.
     */
    public static final BlocStyleAdjustment NONE = new BlocStyleAdjustment(1.0, false);

    /**
     * Unions this recede with another so a bloc that recedes for more than one reason - non-spotlit
     * under a filter, non-allied under the alliances view, or an independent's own recede - dims and
     * desaturates once rather than compounding. Composing them multiplicatively would over-dim a
     * bloc every extra reason applies (each opacity multiplier stacking on the last); the union
     * instead takes the strongest mute (the smallest opacity multiplier) and the OR of desaturate,
     * so folding a reason in a second time is idempotent. The single home for "combine two recedes",
     * so every path that stacks them - the filter over the view, the view over an independent - reads
     * one rule.
     *
     * @param other the other recede to fold in
     * @return the combined recede: the smaller opacity multiplier, desaturate if either desaturates
     */
    public BlocStyleAdjustment mergeRecede(BlocStyleAdjustment other) {
        return new BlocStyleAdjustment(
                Math.min(opacityMultiplier, other.opacityMultiplier),
                desaturate || other.desaturate);
    }

    /**
     * Applies this adjustment's mute to one element's base opacity - the single home for "muting
     * scales opacity", so a bloc's fill, borders, seams, and name all dim by the one rule rather
     * than each site multiplying by hand. {@link #NONE}'s 1.0 multiplier returns the base unchanged.
     *
     * @param baseOpacity the element's own opacity before muting, from its style
     * @return the muted opacity as a float, ready for the GL paint
     */
    public float muteOpacity(double baseOpacity) {
        return (float) (baseOpacity * opacityMultiplier);
    }
}
