package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.RenderStyle;

/**
 * The concrete style one owner draws under this pass: the category bundle it paints from, paired
 * with the adjustment applied on top of it.
 *
 * <p>Where {@link OwnerStyleDecision} says only <em>which</em> category an owner draws in, this is
 * that decision mapped onto the pass's actual theme. Resolved once per owner and read by everything
 * that draws it - its fill, its cluster border, and its cells' interior seams - so those can never
 * diverge.
 *
 * @param style      the category bundle this owner paints from
 * @param adjustment the mute and desaturation applied on top of it
 */
public record OwnerStyling(
    CategoryStyle style,
    ElementStyleAdjustment adjustment) {

    /**
     * Maps a resolved decision onto the pass's theme: the bundle of the category the decision
     * names, with a desaturated owner's fill held at the full-strength category's opacity.
     *
     * @param renderStyle the pass's theme
     * @param categories  the painting layer's categories, naming the full-strength one
     * @param decision    the theme-free style decision for this owner
     * @return the bundle and adjustment this owner draws under
     */
    public static OwnerStyling resolveFrom(
            RenderStyle renderStyle,
            OwnerCategories categories,
            OwnerStyleDecision decision) {

        var style = renderStyle.categoryStyle(decision.category());
        var fullStrengthCategory = categories.resolveFullStrengthCategory();

        if (!decision.adjustment().shouldDesaturate()
                || fullStrengthCategory.equals(decision.category())) {
            return new OwnerStyling(style, decision.adjustment());
        }
        return new OwnerStyling(
            applyFullStrengthFillOpacity(style, renderStyle.categoryStyle(fullStrengthCategory)),
            decision.adjustment());
    }

    // Holds every desaturated owner's fill at the one full-strength fill opacity, so a sector drawn
    // under desaturation reads as a single uniform surface separated by colour alone rather than by
    // two fill weights. A quieter category carries a lighter fill of its own so it recedes behind a
    // full-strength fill when the map paints in full colour, but once desaturation has already sunk
    // an owner to the shared grey that second cue only fractures the background: neighbouring greys
    // at different weights read as two kinds of empty. The rest of the quieter bundle - both border
    // opacities and both widths - still applies, since those distinguish its cells without breaking
    // the fill's uniformity.
    private static CategoryStyle applyFullStrengthFillOpacity(
            CategoryStyle style,
            CategoryStyle fullStrengthStyle) {

        return new CategoryStyle(
            new ElementStyle(
                style.fill().colour(),
                fullStrengthStyle.fill().opacity()),
            style.outer(),
            style.outerWidth(),
            style.inner(),
            style.innerWidth());
    }
}
