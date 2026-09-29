package kmu.maplayers.ownermap.render.clusters;

import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.render.style.OwnerCategories;

import java.awt.Color;

/**
 * The resolved paint scheme one build styles every cell and cluster group from: the theme (its
 * global tier plus one style per category), the painting layer's categories the theme was read
 * for, and the three palettes an unowned cell can paint in - the plain neutral, the sunk one a
 * desaturated owner recolours to, and the lifted one a spared cell takes under a spotlight.
 *
 * <p>All three palettes are resolved once per build rather than per cell. The neutral is a palette
 * here rather than the bare colour it is made of for that reason: an unowned cell wants the pair,
 * and building one per cell would allocate on the most numerous cell type the map has - the
 * uninhabited backdrop covering every corner of the sector nothing holds.
 *
 * <p>Held on the built clusters so an incremental re-shape bakes a rebuilt cell against the
 * same scheme the full build used, and every global knob resolves once off the one theme.
 *
 * @param renderStyle         the theme, one style per category {@code categories} declares
 * @param categories          the painting layer's categories: which one an unowned cell falls
 *                            to, and which one a desaturated owner's fill opacity is held at
 * @param neutralPalette      the shared neutral in both slots, as
 *                            {@link kmu.maplayers.ownermap.render.style.MapPalettes#resolveNeutralPalette}
 *                            builds it - an unowned cell names no owner, so whichever slot an
 *                            element picks paints the same shade, which is what lets
 *                            {@link #readNeutralColour()} read either
 * @param desaturationPalette the shades a receded owner sinks to
 * @param presencePalette     the shades an unowned cell the spotlight spares lifts to
 */
public record MapStyling(
    RenderStyle renderStyle,
    OwnerCategories categories,
    OwnerPalette neutralPalette,
    OwnerPalette desaturationPalette,
    OwnerPalette presencePalette) {

    // The shade every slot of the placeholder below holds. A bare mid grey rather than a layer's
    // unowned shade: the placeholder stands in for a build that never got as far as reading a
    // sector, so there is no reading to take a colour from - and nothing paints from it either
    // way. Named apart from the neutral this record's own slot carries, which is a real reading of
    // a real layer and must not be confused with a stand-in for the absence of one.
    private static final Color PLACEHOLDER_COLOUR = Color.GRAY;

    // All three of the placeholder's palettes, at that one shade in both slots. One value shared
    // between them rather than three equal pairs, since none is ever read and a second would only
    // invite a reader to hunt for the difference between them.
    private static final OwnerPalette PLACEHOLDER_PALETTE =
        new OwnerPalette(PLACEHOLDER_COLOUR, PLACEHOLDER_COLOUR);

    /**
     * The inert scheme a build that never resolved a theme carries - the fallback the render path
     * falls back on after a failed first build.
     *
     * <p>Every value in it is a stand-in that is never read. The null theme is the honest record
     * of the failure (no theme was resolved), and the render path skips an empty overlay before it
     * would reach the global tier; the null categories say the same of the layer's declaration,
     * which nothing classifies against without a theme to index. The palettes are there because
     * the slots must hold something, not because anything paints them.
     *
     * <p>Named here rather than spelt out by the caller for the same reason
     * {@code ContentInputs.createEmpty()} is: "no scheme yet" is one value every such pass shares,
     * and a caller assembling it by hand is choosing five stand-ins that only look arbitrary until
     * one of them turns out not to be.
     *
     * @return the placeholder scheme
     */
    public static MapStyling createEmpty() {
        return new MapStyling(
            null,
            null,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE);
    }

    /**
     * The one shade an unowned cell paints in, for a caller that wants the colour rather than
     * the pair - the hovered-cell highlight, which resolves a single shade.
     *
     * <p>Read off the neutral palette's primary slot rather than stored beside it, so there is one
     * neutral in this record and no second copy of it to fall out of step. Either slot answers:
     * the pair holds the same shade twice by construction, an unowned cell having no owner to
     * take two shades from.
     *
     * @return the shared neutral colour
     */
    public Color readNeutralColour() {
        return neutralPalette.primaryColour();
    }
}
