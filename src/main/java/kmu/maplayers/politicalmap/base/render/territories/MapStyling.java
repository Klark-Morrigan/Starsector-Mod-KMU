package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.theme.RenderStyle;

import java.awt.Color;

/**
 * The resolved paint scheme one build styles every cell and territory from: the theme (its
 * global tier plus one style per category), the shared neutral colour an unfilled palette slot
 * or a factionless cell falls back to, and the two palettes a spotlight pulls apart with - the
 * one a desaturated bloc sinks to, and the one a spared factionless cell lifts to.
 *
 * <p>Both palettes are resolved once here rather than per cell, so every cell recolouring under
 * a spotlight reads the same two ends of it, and an incremental re-shape bakes a rebuilt cell
 * against the pair the full build used.
 *
 * <p>Held on the built territories so an incremental re-shape bakes a rebuilt cell against the
 * same scheme the full build used, and every global knob resolves once off the one theme.
 */
public record MapStyling(
    RenderStyle renderStyle,
    Color neutralColour,
    FactionPalette desaturationPalette,
    FactionPalette presencePalette) {

    // The shade every colour slot of the placeholder below holds. A bare mid grey rather than the
    // neutral faction's own: the placeholder stands in for a build that never got as far as
    // reading a sector, so there is no faction to read a colour from - and nothing paints from it
    // either way. Named apart from the neutral this record's own slot carries, which is a real
    // read of a real faction and must not be confused with a stand-in for the absence of one.
    private static final Color PLACEHOLDER_COLOUR = Color.GRAY;

    // Both of the placeholder's palettes, at that one shade in both slots. One value shared
    // between them rather than two equal pairs, since neither is ever read and a second would only
    // invite a reader to hunt for the difference between them.
    private static final FactionPalette PLACEHOLDER_PALETTE =
        new FactionPalette(PLACEHOLDER_COLOUR, PLACEHOLDER_COLOUR);

    /**
     * The inert scheme a build that never resolved a theme carries - the fallback the render path
     * falls back on after a failed first build.
     *
     * <p>Every value in it is a stand-in that is never read. The null theme is the honest record
     * of the failure (no theme was resolved), and the render path skips an empty overlay before it
     * would reach the global tier; the colour and palettes are there because the slots must hold
     * something, not because anything paints them.
     *
     * <p>Named here rather than spelt out by the caller for the same reason
     * {@link FilterSnapshot#unfiltered()} is: "no scheme yet" is one value every such pass shares,
     * and a caller assembling it by hand is choosing four stand-ins that only look arbitrary until
     * one of them turns out not to be.
     *
     * @return the placeholder scheme
     */
    public static MapStyling createEmpty() {
        return new MapStyling(
            null,
            PLACEHOLDER_COLOUR,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE);
    }
}
