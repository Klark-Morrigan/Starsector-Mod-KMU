package kmu.maplayers.ownermap.owners.holders.factions;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.FactionColours;
import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.factions.FactionNameForm;
import kmlib.starsector.factions.FactionNames;
import kmlib.starsector.factions.SectorFactions;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategory;
import kmu.maplayers.ownermap.owners.holders.base.SectorBlocPalettes;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;

import java.awt.Color;

/**
 * The owner reading of a layer painting one faction as one owner: everything the tier asks about an
 * owner is read off the faction whose ID it is.
 *
 * <p>A faction's shades and crest are its own authored ones, and its name is its own in the player's
 * chosen form.
 *
 * <p>Holds the sector the rebuild was asked about and reads it live on every answer, so a reading
 * built over a second sector reports that sector's factions rather than the running game's.
 */
public final class FactionOwnerReading
        implements OwnerReading {

    private final SectorAPI sector;

    /**
     * @param sector the sector a faction's shades, name and crest are read from; null yields no
     *               shades, no names and no crests, and the fallback neutrals
     */
    public FactionOwnerReading(SectorAPI sector) {
        this.sector = sector;
    }

    // Read straight off the faction: the owner ID is the faction ID, so there is no colour faction to
    // resolve through a grouping first.
    @Override
    public OwnerPalette resolvePalette(String ownerId) {

        return SectorBlocPalettes.readFactionPalette(sector, ownerId);
    }

    @Override
    public String resolveName(String ownerId, OwnerNameFormatChoice nameFormat) {

        return FactionNames.resolveName(
            SectorFactions.findFaction(sector, ownerId),
            nameFormat == OwnerNameFormatChoice.SHORT
                ? FactionNameForm.SHORT
                : FactionNameForm.LONG);
    }

    // A faction with no authored crest keeps its row and draws its name alone.
    @Override
    public String resolveCrestPath(String ownerId) {

        return FactionCrests.resolveCrestPath(SectorFactions.findFaction(sector, ownerId));
    }

    // Nothing recedes of the layer's own accord. A backdrop needs a figure to sit behind, and with
    // every owner a lone faction there is none: receding every faction would sink the whole sector at
    // once, which reads as the layer having failed rather than as an answer.
    @Override
    public ElementStyleAdjustment resolveStyleAdjustment(
            String ownerId,
            ContentInputs contentInputs) {

        return ElementStyleAdjustment.NONE;
    }

    // Genuine independent space always takes the independent category. A faction the recede has
    // desaturated takes it too: desaturation means "read as backdrop", so the faction adopts the
    // independent borders and seams paired with the desaturation palette the same adjustment carries,
    // rather than sitting at full border weight with only its colour greyed. Muting never moves the
    // category; it only dims the active style.
    //
    // The test reads the passed adjustment - every reason to recede already unioned into it - rather
    // than a toggle of its own, so the bundle and the palette can never disagree about whether a
    // faction is desaturated.
    @Override
    public MapStyleCategory resolveCategory(String ownerId, ElementStyleAdjustment adjustment) {

        var isReadAsBackdrop = Factions.INDEPENDENT.equals(ownerId)
            || adjustment.shouldDesaturate();

        return isReadAsBackdrop
            ? HolderCategory.INDEPENDENT
            : HolderCategory.FACTION;
    }

    // The neutral faction's own colour, which is what a system nobody holds has always painted in.
    @Override
    public Color resolveUnownedColour() {

        return FactionColours.resolveNeutralColour(sector);
    }

    // Independent's own two shades, which the tier darkens into the receded backdrop so it sits below
    // genuine independent space - a spotlit faction, even Independent at full strength, therefore
    // reads distinctly against it.
    @Override
    public OwnerPalette resolveRecedePalette() {

        return SectorBlocPalettes.adoptFactionPalette(
            FactionColours.resolvePalette(SectorFactions.findFaction(sector, Factions.INDEPENDENT)));
    }
}
