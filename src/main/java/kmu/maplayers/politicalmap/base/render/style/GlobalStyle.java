package kmu.maplayers.politicalmap.base.render.style;

/**
 * The sector-wide render style - the tier of the {@link RenderStyle} theme that does not
 * vary by category or territory: the contested-fill {@link HatchStyle}, the national-border
 * {@link BorderSmoothingStyle}, and {@code desaturationDarkening}, how far a receded bloc's
 * uniform Independent-based grey is sunk toward black. Holding these once (rather than reading
 * each ad hoc where it is used) gives the global tier a single home; the builders read it off
 * the drawables the same way they read the per-category styles, so an incremental re-shape
 * smooths and hatches against the exact settings the full build baked in.
 *
 * @param desaturationDarkening the fraction of brightness removed from the desaturation palette;
 *                              0 leaves the Independent shades untouched, 1 goes to black
 */
public record GlobalStyle(HatchStyle hatch, BorderSmoothingStyle borderSmoothing,
        double desaturationDarkening) {
}
