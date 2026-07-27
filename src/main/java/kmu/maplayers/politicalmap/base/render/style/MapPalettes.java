package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.color.Colors;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.factions.StarsectorFactionColors;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.settings.FactionPaletteChoice;

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
     * The two shades a bloc actually paints in under its style adjustment: its owner's own
     * bright and dark shades normally, or the pass's shared desaturation palette when the
     * adjustment desaturates the bloc. The single home for the "desaturate swaps the
     * palette" rule, so a cell's seams, a faction's fill and border, and the bloc's name
     * all recolour off one decision rather than three copies of it.
     */
    public static FactionPalette resolveEffectivePalette(
            BlocStyleAdjustment adjustment,
            DominantOwner owner,
            FactionPalette desaturationPalette) {
        return resolveEffectivePalette(
                adjustment,
                new FactionPalette(owner.primaryColor(), owner.secondaryColor()),
                desaturationPalette);
    }

    /**
     * The two shades any piece of ground paints in under its style adjustment, stated over the
     * shades themselves rather than over whoever holds them: the ground's own pair normally, the
     * pass's shared desaturation palette once the adjustment desaturates it.
     *
     * <p>Ground with no owner has shades all the same - a factionless cell's two neutral slots -
     * yet still recolours by the same rule, so the swap is expressed over a palette and the
     * owner-keyed form above resolves to this one. That keeps "desaturate swaps the palette" a
     * single rule no matter what the un-desaturated shades came from.
     */
    public static FactionPalette resolveEffectivePalette(
            BlocStyleAdjustment adjustment,
            FactionPalette ownPalette,
            FactionPalette desaturationPalette) {
        return adjustment.desaturate()
                ? desaturationPalette
                : ownPalette;
    }

    /**
     * Picks the palette shade the player pointed an element at: the secondary (dark)
     * shade for a SECONDARY choice, the primary (bright) shade for a PRIMARY choice, or
     * null for NONE ("No color") so the caller skips that element.
     */
    public static Color pickPaletteColor(
            FactionPaletteChoice choice,
            Color primaryColor,
            Color secondaryColor) {
        return switch (choice) {
            case PRIMARY -> primaryColor;
            case SECONDARY -> secondaryColor;
            case NONE -> null;
        };
    }

    /**
     * Picks the palette shade of a piece of ground itself: its owner's, or the shared neutral
     * colour when nothing owns it - a factionless system (decivilised, or uninhabited) has no
     * palette of its own, so both shades resolve neutral, exactly as its cell's own outline
     * draws. Null for a NONE ("No color") choice, so the caller skips the element.
     *
     * <p>Reads the owner's authored shades untouched, with no per-bloc adjustment folded in,
     * so this answers "whose ground is this" rather than "how is this ground painted right
     * now". The two diverge under a recede: ground the map has sunk to grey still belongs to
     * its owner, and an element whose whole job is to name that owner should say so.
     */
    public static Color pickOwnerPaletteColor(
            FactionPaletteChoice choice, 
            DominantOwner owner,
            Color neutralColor) {
        return owner == null
                ? pickPaletteColor(choice, neutralColor, neutralColor)
                : pickPaletteColor(choice, owner.primaryColor(), owner.secondaryColor());
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
     * KmuLunaSettings itself) so the mapping is a pure lookup; the caller reads the live setting once
     * per pass and hands it in.
     */
    public static FactionPalette resolveDesaturationPalette(
            SectorAPI sector,
            double darkeningStrength) {
        var keepFactor = (float) (1.0 - darkeningStrength);
        var independent = StarsectorFactionColors.resolvePalette(sector, Factions.INDEPENDENT);
        return new FactionPalette(
                Colors.darken(independent.primaryColor(), keepFactor),
                Colors.darken(independent.secondaryColor(), keepFactor));
    }
}
