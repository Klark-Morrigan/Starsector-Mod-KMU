package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.theme.RenderStyle;

import java.awt.Color;

/**
 * The resolved paint scheme one build styles every cell and territory from: the theme (its
 * global tier plus one style per category), and the three palettes a factionless cell can paint
 * in - the plain neutral, the sunk one a desaturated bloc recolours to, and the lifted one a
 * spared cell takes under a spotlight.
 *
 * <p>All three are resolved once per build rather than per cell. The neutral is a palette here
 * rather than the bare colour it is made of for that reason: a factionless cell wants the pair,
 * and building one per cell would allocate on the most numerous cell type the map has - the
 * uninhabited backdrop covering every corner of the sector nothing holds.
 *
 * <p>Held on the built territories so an incremental re-shape bakes a rebuilt cell against the
 * same scheme the full build used, and every global knob resolves once off the one theme.
 *
 * @param neutralPalette     the shared neutral in both slots, as
 *                           {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes#resolveNeutralPalette}
 *                           builds it - a factionless cell names no faction, so whichever slot an
 *                           element picks paints the same shade, which is what lets
 *                           {@link #readNeutralColour()} read either
 * @param desaturationPalette the shades a receded bloc sinks to
 * @param presencePalette    the shades a factionless cell the spotlight spares lifts to
 */
public record MapStyling(
    RenderStyle renderStyle,
    FactionPalette neutralPalette,
    FactionPalette desaturationPalette,
    FactionPalette presencePalette) {

    // The shade every slot of the placeholder below holds. A bare mid grey rather than the neutral
    // faction's own: the placeholder stands in for a build that never got as far as reading a
    // sector, so there is no faction to read a colour from - and nothing paints from it either
    // way. Named apart from the neutral this record's own slot carries, which is a real read of a
    // real faction and must not be confused with a stand-in for the absence of one.
    private static final Color PLACEHOLDER_COLOUR = Color.GRAY;

    // All three of the placeholder's palettes, at that one shade in both slots. One value shared
    // between them rather than three equal pairs, since none is ever read and a second would only
    // invite a reader to hunt for the difference between them.
    private static final FactionPalette PLACEHOLDER_PALETTE =
        new FactionPalette(PLACEHOLDER_COLOUR, PLACEHOLDER_COLOUR);

    /**
     * The inert scheme a build that never resolved a theme carries - the fallback the render path
     * falls back on after a failed first build.
     *
     * <p>Every value in it is a stand-in that is never read. The null theme is the honest record
     * of the failure (no theme was resolved), and the render path skips an empty overlay before it
     * would reach the global tier; the palettes are there because the slots must hold something,
     * not because anything paints them.
     *
     * <p>Named here rather than spelt out by the caller for the same reason
     * {@code ContentInputs.createEmpty()} is: "no scheme yet" is one value every such pass shares,
     * and a caller assembling it by hand is choosing four stand-ins that only look arbitrary until
     * one of them turns out not to be.
     *
     * @return the placeholder scheme
     */
    public static MapStyling createEmpty() {
        return new MapStyling(
            null,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE);
    }

    /**
     * The one shade a factionless cell paints in, for a caller that wants the colour rather than
     * the pair - the hovered-cell highlight, which resolves a single shade.
     *
     * <p>Read off the neutral palette's primary slot rather than stored beside it, so there is one
     * neutral in this record and no second copy of it to fall out of step. Either slot answers:
     * the pair holds the same shade twice by construction, a factionless cell having no faction to
     * take two shades from.
     *
     * @return the shared neutral colour
     */
    public Color readNeutralColour() {
        return neutralPalette.primaryColour();
    }
}
