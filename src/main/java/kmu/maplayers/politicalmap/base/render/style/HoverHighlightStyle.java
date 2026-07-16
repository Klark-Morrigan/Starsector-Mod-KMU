package kmu.maplayers.politicalmap.base.render.style;

import kmu.settings.FactionPaletteChoice;

/**
 * How the map answers the cursor: the {@link HoverGlowStyle} halo around the hovered
 * territory and the {@link HoverWashStyle} lift on the hovered cell, both drawn in one
 * {@code color}.
 *
 * <p>One colour for both, and one taken from the hovered ground's own palette rather than a
 * fixed highlight colour, because the pair is a single answer at two scales - "this system,
 * inside this territory". Two colours would read as two unrelated effects, and a fixed
 * colour would say nothing about whose ground the cursor is on. {@link
 * FactionPaletteChoice#NONE} turns the whole highlight off, the same way it hides any other
 * map element.
 *
 * <p>Sector-wide, so this is global-tier ({@link GlobalStyle}) rather than per category: the
 * highlight is the cursor's feedback, and feedback that changed weight depending on which
 * kind of ground was under the pointer would read as the map responding unevenly.
 */
public record HoverHighlightStyle(
        FactionPaletteChoice color,
        HoverGlowStyle glow,
        HoverWashStyle wash) {
}
