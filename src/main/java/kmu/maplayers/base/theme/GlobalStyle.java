package kmu.maplayers.base.theme;

/**
 * The sector-wide render style - the tier of the {@link RenderStyle} theme that does not
 * vary by category or cluster: the {@link HatchStyle} a hatched fill is cut with, the
 * {@link BorderSmoothingStyle} cluster frontiers are rounded by, the
 * {@link HoverHighlightStyle} the cursor's answer draws in, and the two spotlight strengths -
 * {@code desaturationDarkening}, how far a receded cluster is sunk toward black, and
 * {@code presenceLightening}, how far a cell the spotlight spares is lifted toward white.
 * Holding these once (rather than reading each ad hoc where it is used) gives the global tier a
 * single home; the builders read it off the drawables the same way they read the per-category
 * styles, so an incremental re-shape smooths and hatches against the exact settings the full
 * build baked in.
 *
 * <p>The two strengths are the pair a spotlight separates its subject from its backdrop with,
 * which is why they sit together. One alone cannot do it: both the receded clusters and the spared
 * cells are greys, so a backdrop that only sinks and a subject that never rises stay near enough
 * in value to read as one surface.
 *
 * @param desaturationDarkening the fraction of brightness removed from the desaturated palette
 *                              a layer recedes a cluster into; 0 leaves that palette's shades
 *                              untouched, 1 goes to black
 * @param presenceLightening    the fraction of the way to white a spared cell's shades are
 *                              washed; 0 leaves them at the plain neutral, 1 goes to white. A
 *                              wash toward white rather than a recolour, so a grey stays the
 *                              same grey and only its value moves
 */
public record GlobalStyle(
    HatchStyle hatch,
    BorderSmoothingStyle borderSmoothing,
    HoverHighlightStyle hoverHighlight,
    double desaturationDarkening,
    double presenceLightening) {
}
