package kmu.maplayers.ownermap.render.style;

import kmlib.colour.Colours;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.awt.Color;

/**
 * The shared palette resolution for an owner map: the pure "which colours does an
 * element paint in" rules that both the fills/borders and the cluster-name labels read, so
 * a name can never drift from the space it labels. Kept apart from the theme records (which
 * only hold player choices) and from the drawables build (which owns geometry), so this is
 * the one home for turning a style choice plus an owner's recede into concrete colours.
 */
public final class MapPalettes {

    // Resolves only; never instantiated.
    private MapPalettes() {
    }

    /**
     * The two shades an owner actually paints in under its style adjustment: its own two shades
     * normally, or the pass's shared desaturation palette when the adjustment desaturates the
     * owner. The single home for the "desaturate swaps the palette" rule, so a cell's seams, an
     * owner's fill and border, and the owner's name all recolour off one decision rather than
     * three copies of it.
     */
    public static OwnerPalette resolveEffectivePalette(
            ElementStyleAdjustment adjustment,
            SystemOwner owner,
            OwnerPalette desaturationPalette) {
        return resolveEffectivePalette(
            adjustment,
            owner.palette(),
            desaturationPalette);
    }

    /**
     * The two shades any cell paints in under its style adjustment, stated over the
     * shades themselves rather than over whoever owns them: the cell's own pair normally, the
     * pass's shared desaturation palette once the adjustment desaturates it.
     *
     * <p>A cell with no owner has shades all the same - two neutral slots -
     * yet still recolours by the same rule, so the swap is expressed over a palette and the
     * owner-keyed form above resolves to this one. That keeps "desaturate swaps the palette" a
     * single rule no matter what the un-desaturated shades came from.
     */
    public static OwnerPalette resolveEffectivePalette(
            ElementStyleAdjustment adjustment,
            OwnerPalette ownPalette,
            OwnerPalette desaturationPalette) {
        return adjustment.shouldDesaturate()
            ? desaturationPalette
            : ownPalette;
    }

    /**
     * The palette an ownerless cell draws in: the shared neutral colour in both
     * slots, so whichever slot an element names it paints neutral. Stated once here because
     * "an unowned cell has no palette of its own" is one rule, and a caller spelling the
     * colour twice is stating it again rather than reading it.
     */
    public static OwnerPalette resolveNeutralPalette(Color neutralColour) {
        return new OwnerPalette(neutralColour, neutralColour);
    }

    /**
     * Picks the palette shade the player pointed an element at: the secondary (dark) shade for a
     * SECONDARY selection, the primary (bright) shade for a PRIMARY one, or null for no selection
     * at all so the caller skips that element. This is where a slot becomes a colour.
     *
     * <p>Takes the theme's opaque {@link ElementPaintSelection} rather than a shade outright,
     * since that is the form every style carries a selection in. This is the one place the two
     * meet, so the styles stay free of casts and a selection that is absent - the no-colour
     * state - or belongs to another layer's option set resolves to no shade instead of reaching
     * the switch at all.
     *
     * <p>Takes the pair as a palette rather than as two colours because the slots are only ever
     * meaningful together: a caller holds the pair already, and splitting it at the call site
     * puts two same-typed arguments in an order nothing but their names distinguishes.
     */
    public static Color pickPaletteColour(
            ElementPaintSelection paintSelection,
            OwnerPalette palette) {

        if (!(paintSelection instanceof FactionPaletteSlot slot)) {
            return null;
        }
        return switch (slot) {
            case PRIMARY -> palette.primaryColour();
            case SECONDARY -> palette.secondaryColour();
        };
    }

    /**
     * Picks the palette shade of a cell itself: its owner's, or the shared neutral
     * colour when nothing owns it - an unowned system (decivilised, or uninhabited) has no
     * palette of its own, so both shades resolve neutral, exactly as its cell's own outline
     * draws. Null for a NONE ("No color") choice, so the caller skips the element.
     *
     * <p>Reads the owner's shades untouched, with no per-owner adjustment folded in,
     * so this answers "whose cell is this" rather than "how is this cell painted right
     * now". The two diverge under a recede: a cell the map has sunk to grey still belongs to
     * its owner, and an element whose whole job is to name that owner should say so.
     */
    public static Color pickHolderPaletteColour(
            ElementPaintSelection paintSelection,
            SystemOwner owner,
            Color neutralColour) {
        return pickPaletteColour(
            paintSelection,
            owner == null
                ? resolveNeutralPalette(neutralColour)
                : owner.palette());
    }

    /**
     * Resolves the one uniform palette every desaturated owner recolours to: the painting layer's
     * recede shades, each scaled toward black by {@code darkeningStrength}. On the layers painting
     * holders those shades are Independent's, and Independent's authored colour and the mid-grey
     * genuine independent space paints in are the same grey, so painting a desaturated faction in
     * them unchanged would make it read as independent-held space; darkening sinks the receded fills
     * to a distinctly darker grey that sits behind it, separated by value rather than a hue neither
     * grey has. The strength is the fraction of brightness removed - 0 leaves the shades untouched,
     * 0.3 draws them 30% darker, 1 goes to black. Takes the shades and strength as parameters
     * (rather than reading either itself) so the mapping is a pure lookup; the caller reads both once
     * per pass and hands them in.
     *
     * @param recedePalette     the shades a receded owner sinks toward, from the owner reading
     * @param darkeningStrength the fraction of brightness removed
     * @return the palette every desaturated owner recolours to
     */
    public static OwnerPalette resolveDesaturationPalette(
            OwnerPalette recedePalette,
            double darkeningStrength) {

        var keepFactor = (float) (1.0 - darkeningStrength);

        return new OwnerPalette(
            Colours.darken(recedePalette.primaryColour(), keepFactor),
            Colours.darken(recedePalette.secondaryColour(), keepFactor));
    }

    /**
     * Resolves the palette an unowned cell the spotlight spares paints in: the shared neutral
     * colour washed toward white by {@code lighteningStrength}, in both slots as the plain neutral
     * palette holds it.
     *
     * <p>The counterpart to {@link #resolveDesaturationPalette}, and the reason both exist. That
     * one sinks the receded background below the quieter owners' space; this one lifts a spared
     * cell above it. Sparing the recede alone does not separate the two, because the neutral an
     * unowned cell paints in and the grey the background sinks from are the same grey to begin
     * with - so the spared cell would sit at the value the background started at, which is exactly
     * where the eye stops telling them apart.
     *
     * <p>Washed toward white rather than recoloured, on RGB alone. The cell has no owner to
     * borrow a hue from, and giving it one would state an ownership the layer sparing it is
     * reporting it does not have; a neutral grey moved toward white is the same grey, brighter.
     *
     * @param neutralColour      the shared colour every unowned cell paints in
     * @param lighteningStrength the fraction of the way to white; 0 yields the plain neutral
     *                           palette, 1 yields white
     * @return the two shades a spared cell paints in
     */
    public static OwnerPalette resolvePresencePalette(
            Color neutralColour,
            double lighteningStrength) {

        return resolveNeutralPalette(
            Colours.blendRgbTowards(neutralColour, Color.WHITE, (float) lighteningStrength));
    }
}
