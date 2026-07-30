package kmu.maplayers.base.theme;

import kmu.settings.FactionPaletteChoice;

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
            FactionPaletteChoice.NONE,
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
        return new GlobalStyle(
                new HatchStyle(0, 0, 0),
                new BorderSmoothingStyle(false, false, 0, 0, 0, 0, 0),
                NO_HOVER_HIGHLIGHT,
                DESATURATION_DARKENING);
    }
}
