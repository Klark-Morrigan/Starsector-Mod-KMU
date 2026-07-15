package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.factions.StarsectorFactionColors;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.settings.DesaturationProfileChoice;
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
    public static FactionPalette resolveEffectivePalette(BlocStyleAdjustment adjustment,
            DominantOwner owner, FactionPalette desaturationPalette) {
        return adjustment.desaturate()
                ? desaturationPalette
                : new FactionPalette(owner.primaryColor(), owner.secondaryColor());
    }

    /**
     * Picks the palette shade the player pointed an element at: the secondary (dark)
     * shade for a SECONDARY choice, the primary (bright) shade for a PRIMARY choice, or
     * null for NONE ("No color") so the caller skips that element.
     */
    public static Color pickPaletteColor(FactionPaletteChoice choice, Color primaryColor,
            Color secondaryColor) {
        return switch (choice) {
            case PRIMARY -> primaryColor;
            case SECONDARY -> secondaryColor;
            case NONE -> null;
        };
    }

    /**
     * Overrides the desaturation profile away from the spotlit bloc's own palette when the two
     * would otherwise collide. The {@code INDEPENDENT} profile desaturates every receded bloc to
     * the Independent faction's two shades; spotlight the Independent faction and its
     * full-strength fill is that same pair, so the spotlit bloc and the entire receded background
     * would paint one indistinguishable colour. In that one case the profile falls back to
     * {@code NEUTRAL}, so the receded ground greys out distinctly while spotlit Independent keeps
     * its colour. Every other spotlight, an un-filtered pass (null selection), and the
     * {@code NEUTRAL} profile pass through unchanged, so the override bites only the reported case.
     */
    public static DesaturationProfileChoice resolveSpotlightSafeDesaturationProfile(
            DesaturationProfileChoice profile, String selectedBlocId) {
        return profile == DesaturationProfileChoice.INDEPENDENT
                && Factions.INDEPENDENT.equals(selectedBlocId)
                ? DesaturationProfileChoice.NEUTRAL
                : profile;
    }

    /**
     * Resolves the desaturation palette a desaturated bloc recolours to, from the given
     * profile: Independent forges the Independent faction's own two shades (the same pair
     * a real independent owner's DominantOwner carries), so a desaturated bloc reads
     * exactly as independent-held space; Neutral is the shared neutral color in both
     * slots, the flat gray unowned space draws in. Takes the profile and the neutral color
     * as parameters (rather than reading KmuLunaSettings itself) so the mapping is a pure
     * lookup; the caller reads the live setting once per pass and hands it in.
     */
    public static FactionPalette resolveDesaturationPalette(DesaturationProfileChoice profile,
            SectorAPI sector, Color neutralColor) {
        return switch (profile) {
            case INDEPENDENT -> StarsectorFactionColors.resolvePalette(sector, Factions.INDEPENDENT);
            case NEUTRAL -> new FactionPalette(neutralColor, neutralColor);
        };
    }
}
