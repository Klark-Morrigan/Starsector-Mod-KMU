package kmu.maplayers.ownermap.render.style;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerReading;

import java.awt.Color;

/**
 * The resolved paint scheme one build styles every cell and cluster group from: the theme (its
 * global tier plus one style per category), the trace its cluster borders are drawn by, the
 * painting layer's categories the theme was read for, and the three palettes an unowned cell can
 * paint in - the plain neutral, the sunk one a desaturated owner recolours to, and the lifted one a
 * spared cell takes under a spotlight.
 *
 * <p>All three palettes are resolved once per build rather than per cell. The neutral is a palette
 * here rather than the bare colour it is made of for that reason: an unowned cell wants the pair,
 * and building one per cell would allocate on the most numerous cell type the map has - the
 * uninhabited backdrop covering every corner of the sector nothing holds.
 *
 * <p>The trace rides here rather than being read by each cluster group, so every border one build
 * draws - and every one the incremental refresh later redraws - is traced under one reading of its
 * knobs.
 *
 * <p>Held on the built clusters so an incremental re-shape bakes a rebuilt cell against the
 * same scheme the full build used, and every global knob resolves once off the one theme.
 *
 * @param renderStyle         the theme, one style per category {@code categories} declares
 * @param borderTrace         the trace every cluster border of the build is drawn by
 * @param categories          the painting layer's categories: which one an unowned cell falls
 *                            to, and which one a desaturated owner's fill opacity is held at
 * @param neutralPalette      the shared neutral in both slots, as
 *                            {@link MapPalettes#resolveNeutralPalette} builds it - an unowned cell
 *                            names no owner, so whichever slot an element picks paints the same
 *                            shade, which is what lets {@link #readNeutralColour()} read either
 * @param desaturationPalette the shades a receded owner sinks to
 * @param presencePalette     the shades an unowned cell the spotlight spares lifts to
 */
public record MapStyling(
    RenderStyle renderStyle,
    ClusterBorderTrace borderTrace,
    OwnerCategories categories,
    ColourPair neutralPalette,
    ColourPair desaturationPalette,
    ColourPair presencePalette) {

    // The shade every slot of the placeholder below holds. A bare mid grey rather than a layer's
    // unowned shade: the placeholder stands in for a build that never got as far as reading a
    // sector, so there is no reading to take a colour from - and nothing paints from it either
    // way. Named apart from the neutral this record's own slot carries, which is a real reading of
    // a real layer and must not be confused with a stand-in for the absence of one.
    private static final Color PLACEHOLDER_COLOUR = Color.GRAY;

    // All three of the placeholder's palettes, at that one shade in both slots. One value shared
    // between them rather than three equal pairs, since none is ever read and a second would only
    // invite a reader to hunt for the difference between them.
    private static final ColourPair PLACEHOLDER_PALETTE =
        new ColourPair(PLACEHOLDER_COLOUR, PLACEHOLDER_COLOUR);

    /**
     * The inert scheme a build that never resolved a theme carries - the fallback the render path
     * falls back on after a failed first build.
     *
     * <p>Every value in it is a stand-in that is never read. The null theme is the honest record
     * of the failure (no theme was resolved), and the render path skips an empty overlay before it
     * would reach the global tier; the null trace and categories say the same of the knobs and the
     * layer's declaration, which nothing traces or classifies against without a theme to index. The
     * palettes are there because the slots must hold something, not because anything paints them.
     *
     * <p>Named here rather than spelt out by the caller for the same reason
     * {@code ContentInputs.createEmpty()} is: "no scheme yet" is one value every such pass shares,
     * and a caller assembling it by hand is choosing six stand-ins that only look arbitrary until
     * one of them turns out not to be.
     *
     * @return the placeholder scheme
     */
    public static MapStyling createEmpty() {
        return new MapStyling(
            null,
            null,
            null,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE,
            PLACEHOLDER_PALETTE);
    }

    /**
     * The paint scheme one build styles everything from: the whole theme read once through the
     * single reader seam for the layer's own categories, the border trace, the reading's unowned
     * shade, and the two palettes a spotlight separates its subject from its backdrop with.
     *
     * <p>Read once per build and handed to every part of it, so the production fills, the tracing
     * overlay and the names over either cannot be styled by two readings of the same knobs.
     *
     * @param categories    the painting layer's categories the theme is read for
     * @param reading       the painting layer's reading, which names the unowned and recede shades
     * @param contentInputs the sidebar picks the rebuild sampled, which a category's style may ride
     *                      on
     * @return the scheme the build styles by
     */
    public static MapStyling readFromLunaSettings(
            OwnerCategories categories,
            OwnerReading reading,
            ContentInputs contentInputs) {

        var renderStyle = RenderStyleReader.readRenderStyle(categories, contentInputs);
        var neutralColour = reading.resolveUnownedColour();

        return new MapStyling(
            renderStyle,
            ClusterBorderTrace.readFromLunaSettings(),
            categories,
            MapPalettes.resolveNeutralPalette(neutralColour),
            // The receded background desaturates to one uniform palette, the reading's recede
            // shades darkened by the live setting so it sits below the quieter owners' own space -
            // a spotlit owner at full strength therefore reads distinctly against it.
            MapPalettes.resolveDesaturationPalette(
                reading.resolveRecedePalette(),
                renderStyle.global().desaturationDarkening()),
            // The other end of that separation: the neutral a spared unowned cell paints in,
            // lifted toward white so it clears the greys the recede just sank. Resolved beside its
            // counterpart, both once per build, so the two ends cannot be read from different
            // snapshots of the same two knobs.
            MapPalettes.resolvePresencePalette(
                neutralColour,
                renderStyle.global().presenceLightening()));
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

    /**
     * The style an unowned cell draws in: the category the layer declares for a settled or an
     * empty cell, looked up in this build's theme.
     *
     * <p>Stated once because the production fills and the border-tracing overlay both classify
     * unowned cells, and the overlay is only worth reading while it shows the cells the map would.
     *
     * @param isSettled whether something stands in the cell's system, as
     *                  {@link UnownedStyleResolver#isSettledSystem} answers it
     * @return the cell's category style
     */
    public CategoryStyle resolveUnownedStyle(boolean isSettled) {
        return renderStyle.categoryStyle(categories.resolveUnownedCategory(isSettled));
    }
}
