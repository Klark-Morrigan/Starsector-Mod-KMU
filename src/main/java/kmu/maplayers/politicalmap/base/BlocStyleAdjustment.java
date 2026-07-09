package kmu.maplayers.politicalmap.base;

/**
 * The two orthogonal per-bloc styling knobs the shared political-map pipeline honours
 * without knowing why a view asked for them, so a view can dim or recolour a bloc while
 * the render code stays a pure applier.
 *
 * <p>A view resolves one of these per bloc through
 * {@link PoliticalMapView#resolveBlocStyleAdjustment}. {@code opacityMultiplier} scales
 * the bloc's fills, borders, and name alpha; {@code desaturate} swaps its palette for the
 * shared desaturation-profile palette the pipeline resolves. The adjustment names no
 * target colour - what {@code desaturate} maps to is the pipeline's business (an authored
 * profile setting), so the view flags only <em>whether</em> a bloc desaturates and stays
 * out of the profile decision. {@link #NONE} is the identity: draw the bloc exactly as its
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
}
