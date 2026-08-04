package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.RenderStyle;

/**
 * The concrete style one bloc draws under this pass: the category bundle it paints from,
 * paired with the adjustment applied on top of it.
 *
 * <p>Where {@link BlocStyleDecision} says only <em>whether</em> a bloc recedes to the
 * independent style, this is that decision mapped onto the pass's actual theme. Resolved once
 * per bloc and read by everything that draws it - its fill, its national border, and its
 * cells' interior seams - so those can never diverge.
 *
 * @param style      the category bundle this bloc paints from
 * @param adjustment the mute and desaturation applied on top of it
 */
public record BlocStyling(
    CategoryStyle style,
    ElementStyleAdjustment adjustment) {

    /**
     * Maps a resolved decision onto the pass's theme: the independent bundle when the decision
     * recedes the bloc to it, the faction bundle otherwise.
     *
     * @param renderStyle the pass's theme
     * @param decision    the view-agnostic style decision for this bloc
     * @return the bundle and adjustment this bloc draws under
     */
    public static BlocStyling resolveFrom(RenderStyle renderStyle, BlocStyleDecision decision) {

        var factionStyle = renderStyle.categoryStyle(PoliticalMapCategory.FACTION);
        if (!decision.usesIndependentStyle()) {
            return new BlocStyling(factionStyle, decision.adjustment());
        }
        return new BlocStyling(
            applyDesaturatedFillOpacity(
                renderStyle.categoryStyle(PoliticalMapCategory.INDEPENDENT),
                factionStyle,
                decision.adjustment()),
            decision.adjustment());
    }

    // Holds every desaturated bloc's fill at the one faction fill opacity, so a sector drawn
    // under desaturation reads as a single uniform surface separated by colour alone rather than
    // by two fill weights. Independent space carries a lighter fill of its own so it recedes
    // behind a faction's fill when the map paints in full colour, but once desaturation has already
    // sunk a bloc to the shared grey that second cue only fractures the background: neighbouring
    // greys at different weights read as two kinds of empty. The rest of the independent bundle -
    // both border opacities and both widths - still applies, since those distinguish independent
    // territory without breaking the fill's uniformity.
    private static CategoryStyle applyDesaturatedFillOpacity(
            CategoryStyle independentStyle,
            CategoryStyle factionStyle,
            ElementStyleAdjustment adjustment) {

        if (!adjustment.shouldDesaturate()) {
            return independentStyle;
        }
        return new CategoryStyle(
            new ElementStyle(
                independentStyle.fill().colour(),
                factionStyle.fill().opacity()),
            independentStyle.outer(),
            independentStyle.outerWidth(),
            independentStyle.inner(),
            independentStyle.innerWidth());
    }
}
