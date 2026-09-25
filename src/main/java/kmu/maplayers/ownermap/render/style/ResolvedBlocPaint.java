package kmu.maplayers.ownermap.render.style;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.awt.Color;

/**
 * Everything one bloc's elements paint from this pass: the category bundle, the adjustment applied
 * over it, and the two shades those resolve against once the adjustment has had its say.
 *
 * <p>The three travel together because no element can be painted from fewer. A colour is a slot
 * picked out of the palette, and an alpha is the style's own opacity scaled by the mute - so a
 * caller holding two of the three still has to reach for the third, and every caller reaching
 * separately is a chance for a fill to be muted while the border beside it is not.
 *
 * <p>{@link #pickPaintOf} is therefore the read, rather than the three parts: the fill, the
 * cluster border and the interior seam differ only in which element of the bundle they name, so
 * naming that element is the whole of what a caller has left to say.
 *
 * <p>Where {@link OwnerStyling} answers which bundle a bloc draws from, this is that answer carried
 * down to the shades. The palette is resolved on the way in rather than per element, since the
 * desaturation swap is one decision for the whole bloc.
 *
 * @param style      the category bundle this bloc paints from
 * @param adjustment the mute and desaturation applied over it
 * @param palette    the two shades its elements pick from, the desaturation swap already applied
 */
public record ResolvedBlocPaint(
    CategoryStyle style,
    ElementStyleAdjustment adjustment,
    FactionPalette palette) {

    /**
     * What a bloc somebody holds paints from: its holder's own two shades under the styling
     * resolved for it.
     *
     * @param styling             the bundle and adjustment this bloc draws under
     * @param holder              whose shades it paints in when nothing desaturates it
     * @param desaturationPalette the shades the pass sinks a desaturated bloc to
     * @return the paint source for that bloc
     */
    public static ResolvedBlocPaint resolveFrom(
            OwnerStyling styling,
            SystemOwner holder,
            FactionPalette desaturationPalette) {

        return resolveFrom(
            styling.style(),
            styling.adjustment(),
            holder.resolvePalette(),
            desaturationPalette);
    }

    /**
     * The same over shades stated outright rather than read off a holder, for a cell nobody holds:
     * its own pair is the neutral, or the neutral lifted where a spotlight spared it, and neither
     * belongs to anyone the pass could ask.
     *
     * @param style               the category bundle the cell paints from
     * @param adjustment          the mute and desaturation applied over it
     * @param ownPalette          the shades it paints in when nothing desaturates it
     * @param desaturationPalette the shades the pass sinks a desaturated element to
     * @return the paint source for that cell
     */
    public static ResolvedBlocPaint resolveFrom(
            CategoryStyle style,
            ElementStyleAdjustment adjustment,
            FactionPalette ownPalette,
            FactionPalette desaturationPalette) {

        return new ResolvedBlocPaint(
            style,
            adjustment,
            MapPalettes.resolveEffectivePalette(adjustment, ownPalette, desaturationPalette));
    }

    /**
     * The shade one element paints in, or null for a "No color" choice so the caller skips it.
     *
     * <p>Offered beside the paint below for the caller that needs the colour before it has anything
     * to paint: a bloc whose fill and border are both switched off bakes no geometry at all, and
     * asking that question is what stops the trace being paid for.
     *
     * @param element the element of this bloc's bundle to resolve
     * @return its colour, or null where the player pointed it at no shade
     */
    public Color pickColourOf(ElementStyle element) {
        return MapPalettes.pickPaletteColour(element.colour(), palette);
    }

    /**
     * What one element of this bloc draws: its shade out of the palette, at its own opacity scaled
     * by the bloc's mute.
     *
     * @param element the element of this bloc's bundle to resolve
     * @return its paint, carrying a null colour where the player pointed it at no shade
     */
    public UiElementPaint pickPaintOf(ElementStyle element) {
        return new UiElementPaint(pickColourOf(element), adjustment.muteOpacity(element.opacity()));
    }
}
