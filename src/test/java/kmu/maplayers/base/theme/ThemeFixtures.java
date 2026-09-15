package kmu.maplayers.base.theme;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.HatchPattern;

/**
 * Shared fixtures for the tests that need a theme they are not testing: the global tier and the
 * highlight tiers in the shape that reads nothing back.
 *
 * <p>One home for these because a {@link RenderStyle} cannot be built without a full global tier,
 * so every suite that styles anything at all was spelling out the same four zeroed sub-records to
 * get at the one tier it actually asserts on. A placeholder that drifts between suites is the kind
 * of difference that makes two tests of the same behaviour quietly disagree.
 *
 * <p>The categories are deliberately absent: what the cells divide into belongs to the layer
 * painting it, so a layer's own fixtures seed those against its own category set.
 */
public final class ThemeFixtures {

    /**
     * A highlight tier that paints nothing, for the suites that light nothing up. Serves either
     * tier the global style carries - the cursor's and the preview's differ only in the weights
     * they were read at, and both are off at zero.
     */
    public static final HoverHighlightStyle NO_HIGHLIGHT = new HoverHighlightStyle(
        null,
        HoverGlowStyle.NO_GLOW,
        new HoverWashStyle(0, 0, 0));

    // The two values the inert tier does not zero. A desaturating build reads the darkening to
    // sink the shared grey and the lightening to lift a spared cell's, so zeroing either would
    // leave that path exercised against a no-op.
    private static final double DESATURATION_DARKENING = 0.3;
    private static final double PRESENCE_LIGHTENING = 0.2;

    // The join tolerance a fixture hatches under. Zero merges only crossings that coincide
    // exactly, which is the reading with no epsilon in it: a suite about which cells hatch gets
    // the same segments either way, and one whose subject is the merge names a tolerance of its
    // own rather than inheriting a number chosen here.
    private static final double FIXTURE_HATCH_JOIN_TOLERANCE = 0;

    // Fixtures only; never instantiated.
    private ThemeFixtures() {
    }

    /**
     * A global tier that changes nothing it is read for: no hatch, no border smoothing, neither
     * highlight painting anything, and the shipped spotlight strengths.
     *
     * @return a live global tier, for a suite whose subject is the per-category tier or the
     *         drawables built off it
     */
    public static GlobalStyle createInertGlobalStyle() {
        return createGlobalStyleRoundingBy(new CornerRoundingStyle(false, 0, 0, 0, 0));
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
            createHatchStyle(0, 0, 0),
            new BorderSmoothingStyle(
                new SpikeSandingStyle(false, 0, 0),
                cornerRounding),
            NO_HIGHLIGHT,
            NO_HIGHLIGHT,
            DESATURATION_DARKENING,
            PRESENCE_LIGHTENING);
    }

    /**
     * The same inert tier with one live preview-highlight tier in it, for a suite whose subject is
     * what a whole lit set of cells is drawn to. The cursor's tier stays off beside it, so a case
     * cannot pass by reading whichever of the two it happened to reach.
     *
     * @param previewHighlight the tier a previewed set of cells is lit to
     * @return a live global tier, inert but for its preview highlight
     */
    public static GlobalStyle createGlobalStylePreviewingWith(
            HoverHighlightStyle previewHighlight) {

        var inert = createInertGlobalStyle();

        return new GlobalStyle(
            inert.hatch(),
            inert.borderSmoothing(),
            NO_HIGHLIGHT,
            previewHighlight,
            inert.desaturationDarkening(),
            inert.presenceLightening());
    }

    /**
     * The line family a cut is made to, for a suite whose subject is the hatched geometry. The
     * join tolerance a caller does not name is the one a geometry case has no opinion on, and
     * pinning it here is what keeps those suites from each choosing their own.
     *
     * @param spacing      the perpendicular gap between lines, in world units
     * @param angleRadians the direction the lines run in
     * @return a live hatch pattern
     */
    public static HatchPattern createHatchPattern(double spacing, double angleRadians) {
        return new HatchPattern(spacing, angleRadians, FIXTURE_HATCH_JOIN_TOLERANCE);
    }

    /**
     * The same pattern stroked the way the shipped theme strokes it, for a suite that needs a
     * whole hatch style rather than the pattern a cut is made to.
     *
     * @param spacing       the perpendicular gap between lines, in world units
     * @param angleRadians  the direction the lines run in
     * @param widthFraction the share of the spacing each line inks
     * @return a live hatch style
     */
    public static HatchStyle createHatchStyle(
            double spacing,
            double angleRadians,
            double widthFraction) {

        return new HatchStyle(
            createHatchPattern(spacing, angleRadians),
            new GlLineHatchStroke(GlLineQuality.ALIASED, widthFraction));
    }
}
