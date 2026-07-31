package kmu.maplayers.base.theme;

/**
 * The sector-wide render style - the tier of the {@link RenderStyle} theme that does not
 * vary by category or territory: the {@link HatchStyle} hatched ground is cut with, the
 * {@link BorderSmoothingStyle} cluster frontiers are rounded by, the
 * {@link HoverHighlightStyle} the cursor's answer draws in, and
 * {@code desaturationDarkening}, how far receded ground is sunk toward black. Holding these
 * once (rather than reading each ad hoc where it is used) gives the global tier a single home;
 * the builders read it off the drawables the same way they read the per-category styles, so an
 * incremental re-shape smooths and hatches against the exact settings the full build baked in.
 *
 * @param desaturationDarkening the fraction of brightness removed from the desaturated palette
 *                              a layer recedes ground into; 0 leaves that palette's shades
 *                              untouched, 1 goes to black
 */
public record GlobalStyle(
        HatchStyle hatch,
        BorderSmoothingStyle borderSmoothing,
        HoverHighlightStyle hoverHighlight,
        double desaturationDarkening) {
}
