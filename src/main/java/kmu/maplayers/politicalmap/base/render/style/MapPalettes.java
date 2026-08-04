package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.Colours;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.factions.StarsectorFactionColours;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import java.awt.Color;

/**
 * The shared palette resolution for the political map: the pure "which colours does an
 * element paint in" rules that both the fills/borders and the cluster-name labels read, so
 * a name can never drift from the space it labels. Kept apart from the theme records (which
 * only hold player choices) and from the drawables build (which owns geometry), so this is
 * the one home for turning a style choice plus a bloc's recede into concrete colours.
 */
public final class MapPalettes {

    // Resolves only; never instantiated.
    private MapPalettes() {
    }

    /**
     * The two shades a bloc actually paints in under its style adjustment: its holder's own
     * bright and dark shades normally, or the pass's shared desaturation palette when the
     * adjustment desaturates the bloc. The single home for the "desaturate swaps the
     * palette" rule, so a cell's seams, a faction's fill and border, and the bloc's name
     * all recolour off one decision rather than three copies of it.
     */
    public static FactionPalette resolveEffectivePalette(
            ElementStyleAdjustment adjustment,
            DominantHolder holder,
            FactionPalette desaturationPalette) {
        return resolveEffectivePalette(
            adjustment,
            holder.resolvePalette(),
            desaturationPalette);
    }

    /**
     * The two shades any piece of ground paints in under its style adjustment, stated over the
     * shades themselves rather than over whoever holds them: the ground's own pair normally, the
     * pass's shared desaturation palette once the adjustment desaturates it.
     *
     * <p>Ground with no holder has shades all the same - a factionless cell's two neutral slots -
     * yet still recolours by the same rule, so the swap is expressed over a palette and the
     * holder-keyed form above resolves to this one. That keeps "desaturate swaps the palette" a
     * single rule no matter what the un-desaturated shades came from.
     */
    public static FactionPalette resolveEffectivePalette(
            ElementStyleAdjustment adjustment,
            FactionPalette ownPalette,
            FactionPalette desaturationPalette) {
        return adjustment.shouldDesaturate()
            ? desaturationPalette
            : ownPalette;
    }

    /**
     * The palette a piece of ownerless ground draws in: the shared neutral colour in both
     * slots, so whichever slot an element names it paints neutral. Stated once here because
     * "factionless ground has no palette of its own" is one rule, and a caller spelling the
     * colour twice is stating it again rather than reading it.
     */
    public static FactionPalette resolveNeutralPalette(Color neutralColour) {
        return new FactionPalette(neutralColour, neutralColour);
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
            FactionPalette palette) {

        if (!(paintSelection instanceof FactionPaletteSlot slot)) {
            return null;
        }
        return switch (slot) {
            case PRIMARY -> palette.primaryColour();
            case SECONDARY -> palette.secondaryColour();
        };
    }

    /**
     * Picks the palette shade of a piece of ground itself: its holder's, or the shared neutral
     * colour when nothing owns it - a factionless system (decivilised, or uninhabited) has no
     * palette of its own, so both shades resolve neutral, exactly as its cell's own outline
     * draws. Null for a NONE ("No color") choice, so the caller skips the element.
     *
     * <p>Reads the holder's authored shades untouched, with no per-bloc adjustment folded in,
     * so this answers "whose ground is this" rather than "how is this ground painted right
     * now". The two diverge under a recede: ground the map has sunk to grey still belongs to
     * its holder, and an element whose whole job is to name that holder should say so.
     */
    public static Color pickHolderPaletteColour(
            ElementPaintSelection paintSelection,
            DominantHolder holder,
            Color neutralColour) {
        return pickPaletteColour(
            paintSelection,
            holder == null
                ? resolveNeutralPalette(neutralColour)
                : holder.resolvePalette());
    }

    /**
     * Resolves the one uniform palette every desaturated bloc recolours to: the Independent
     * faction's own two shades, each scaled toward black by {@code darkeningStrength}. Independent's
     * authored colour and the mid-grey genuine independent space paints in are the same grey, so
     * painting a desaturated faction in Independent's shades unchanged would make it read as
     * independent-held space; darkening sinks the receded ground to a distinctly darker grey that
     * sits behind it, separated by value rather than a hue neither grey has. The strength is the
     * fraction of brightness removed - 0 leaves the Independent shades untouched, 0.3 draws them
     * 30% darker, 1 goes to black. Takes the sector and strength as parameters (rather than reading
     * KmuPoliticalMapSettings itself) so the mapping is a pure lookup; the caller reads the live
     * setting once per pass and hands it in.
     */
    public static FactionPalette resolveDesaturationPalette(
            SectorAPI sector,
            double darkeningStrength) {

        var keepFactor = (float) (1.0 - darkeningStrength);
        var independent = StarsectorFactionColours.resolvePalette(sector, Factions.INDEPENDENT);

        return new FactionPalette(
            Colours.darken(independent.primaryColour(), keepFactor),
            Colours.darken(independent.secondaryColour(), keepFactor));
    }
}
