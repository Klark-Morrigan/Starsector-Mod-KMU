package kmu.maplayers.base.theme;

/**
 * How the map answers the cursor: the {@link HoverGlowStyle} halo around the hovered
 * cluster and the {@link HoverWashStyle} lift on the hovered cell, both drawn in one
 * {@code colour}.
 *
 * <p>One colour for both, and one taken from the hovered cell's own palette rather than a
 * fixed highlight colour, because the pair is a single answer at two scales - "this system,
 * inside this cluster". Two colours would read as two unrelated effects, and a fixed
 * colour would say nothing about whose cell the cursor is on. This picks only which of that
 * owner's palette shades to use; switching the highlight off is a separate enable toggle,
 * which gates the cursor read itself rather than just blanking the colour.
 *
 * <p>Sector-wide, so this is global-tier ({@link GlobalStyle}) rather than per category: the
 * highlight is the cursor's feedback, and feedback that changed weight depending on which
 * kind of cell was under the pointer would read as the map responding unevenly.
 */
public record HoverHighlightStyle(
    ElementPaintSelection colour,
    HoverGlowStyle glow,
    HoverWashStyle wash) {
}
