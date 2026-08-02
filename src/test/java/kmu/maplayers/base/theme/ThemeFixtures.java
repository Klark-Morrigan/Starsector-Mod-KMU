package kmu.maplayers.base.theme;

/**
 * Shared fixtures for the tests that need a theme they are not testing: the global tier and the
 * hover highlight in the shape that reads nothing back.
 *
 * <p>One home for these because a {@link RenderStyle} cannot be built without a full global tier,
 * so every suite that styles anything at all was spelling out the same four zeroed sub-records to
 * get at the one tier it actually asserts on. A placeholder that drifts between suites is the kind
 * of difference that makes two tests of the same behaviour quietly disagree.
 *
 * <p>The categories are deliberately absent: what the ground divides into belongs to the layer
 * painting it, so a layer's own fixtures seed those against its own category set.
 */
public final class ThemeFixtures {

    /** The cursor feedback switched off, for the suites that hover nothing. */
    public static final HoverHighlightStyle NO_HOVER_HIGHLIGHT = new HoverHighlightStyle(
            null,
        new HoverGlowStyle(0, 0, 0, 0, 0),
        new HoverWashStyle(0, 0, 0));

    // The one value the inert tier does not zero. A desaturating build reads it to darken the
    // shared grey, so zeroing it would leave the desaturation path exercised against a no-op.
    private static final double DESATURATION_DARKENING = 0.3;

    // Fixtures only; never instantiated.
    private ThemeFixtures() {
    }

    /**
     * A global tier that changes nothing it is read for: no hatch, no border smoothing, no hover
     * feedback, and the shipped desaturation darkening.
     *
     * @return a live global tier, for a suite whose subject is the per-category tier or the
     *         drawables built off it
     */
    public static GlobalStyle createInertGlobalStyle() {
        return createGlobalStyleRoundingBy(new CornerRoundingStyle(false, 0, 0, 0));
    }

    /**
     * The same inert tier with one live corner-rounding profile in it, for a suite whose subject
     * is what the rounding pass does to a shape. Sanding stays off: a caller reaching for this
     * wants the rounded geometry, and a sanding pass in front of it could splice out the very
     * corners the case is about.
     *
     * @param cornerRounding the rounding profile the tier carries, its gate included
     * @return a live global tier, inert but for its rounding
     */
    public static GlobalStyle createGlobalStyleRoundingBy(CornerRoundingStyle cornerRounding) {
        return new GlobalStyle(
            new HatchStyle(0, 0, 0),
            new BorderSmoothingStyle(
                new SpikeSandingStyle(false, 0, 0),
                cornerRounding),
            NO_HOVER_HIGHLIGHT,
            DESATURATION_DARKENING);
    }
}
