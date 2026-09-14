package kmu.maplayers.base.theme;

/**
 * How the map answers a hover: the {@link HoverGlowStyle} halo blooming off the loops that are
 * lit and the {@link HoverWashStyle} lift on the cells inside them, both drawn in one
 * {@code colour}.
 *
 * <p>One colour for both, and one taken from the lit subject's own palette rather than a
 * fixed highlight colour, because the pair is a single answer at two scales - "this system,
 * inside this cluster". Two colours would read as two unrelated effects, and a fixed
 * colour would say nothing about whose cells are lit. This picks only which of that
 * owner's palette shades to use: a highlight that draws nothing at all is one whose weights are
 * dialed to nothing, or one whose read is gated before it ever reaches a style.
 *
 * <p>Sector-wide, so this is global-tier ({@link GlobalStyle}) rather than per category: a
 * highlight is feedback, and feedback that changed weight depending on which kind of cell was
 * under it would read as the map responding unevenly. The tier is held once per kind of hover the
 * map answers, since one cell under the pointer and a scatter of cells across the sector need
 * different weights of the same effect.
 */
public record HoverHighlightStyle(
    ElementPaintSelection colour,
    HoverGlowStyle glow,
    HoverWashStyle wash) {
}
