package kmu.maplayers.ownermap.holding;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.factions.StarsectorFactionColours;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;
import kmu.maplayers.ownermap.render.style.SectorBlocPalettes;

import java.awt.Color;

/**
 * The owner reading of a layer that paints holders: an owner is a bloc - a lone faction, or a group
 * of factions folded into one - and everything the tier asks about it is read off the faction it
 * paints as, under the grouping the holding was folded by.
 *
 * <p>A bloc's shades and crest are its colour faction's - a group's leading member, or a lone
 * faction itself - so a group needs no palette of its own. Its name is the group's where it is a
 * group and its faction's otherwise, so a lone faction reads the same whichever grouping it was
 * folded under. Independent space draws in the independent category whatever the grouping; a lone
 * faction that desaturates joins it there, since desaturation means "read as backdrop", while a
 * group always keeps full strength so it stands out against that backdrop. A lone faction recedes
 * of its layer's own accord only where some group exists for it to be the backdrop to.
 *
 * <p>Under the identity grouping no bloc is a group, so the group rules above never fire and the
 * reading is plain faction holding; that is what lets every layer painting holders share this one
 * reading and differ only by the grouping it hands in.
 *
 * <p>Holds the sector and the grouping the rebuild sampled, so every answer is a lookup over that
 * one snapshot - the grouping can be a live read of the game, and a reading that sampled it again
 * per question could colour, name and recede one bloc off three different memberships.
 */
public final class HolderOwnerReading
        implements OwnerReading {

    private final SectorAPI sector;
    private final HolderGrouping grouping;
    private final SectorBlocPalettes palettes;

    /**
     * @param sector   the sector a bloc's colour faction, name and crest are read from; null yields
     *                 no shades, no names and no crests, and the fallback neutrals
     * @param grouping the grouping the rebuild folded factions into blocs under, sampled once
     */
    public HolderOwnerReading(SectorAPI sector, HolderGrouping grouping) {

        this.sector = sector;
        this.grouping = grouping;
        this.palettes = new SectorBlocPalettes(sector, grouping);
    }

    @Override
    public OwnerPalette resolvePalette(String ownerId) {

        return palettes.readBlocPalette(ownerId);
    }

    // A group reads by its own name; a lone faction by its own, in the player's chosen form, so a
    // lone faction's label never drifts between the groupings it can be folded under. A faction
    // that will not resolve carries no name.
    @Override
    public String resolveName(String ownerId, FactionNameFormatChoice nameFormat) {

        var groupName = grouping.resolveGroupName(ownerId);
        if (groupName != null) {
            return groupName;
        }

        var faction = sector == null
            ? null
            : sector.getFaction(ownerId);

        return faction == null
            ? null
            : resolveFactionName(faction, nameFormat);
    }

    // The colour faction's crest - a group's leading member, or a lone faction's own - so one lookup
    // serves a grouped and an ungrouped layer alike. A bloc with no authored crest keeps its row and
    // draws its name alone.
    @Override
    public String resolveCrestPath(String ownerId) {

        return sector == null
            ? null
            : FactionCrests.resolveCrestPath(
                sector.getFaction(grouping.resolveColourFactionId(ownerId)));
    }

    // Only a lone faction recedes of the layer's own accord, and only where some group exists: with
    // no group there is no figure, and receding every bloc would sink the whole sector at once -
    // which reads as the layer having failed rather than as an answer. How far such a faction
    // recedes is the painting view's own backdrop, sampled into the rebuild's preferences. Under the
    // identity grouping nothing recedes.
    @Override
    public ElementStyleAdjustment resolveStyleAdjustment(
            String ownerId,
            ContentInputs contentInputs) {

        if (!grouping.hasAnyGroupedBloc() || grouping.isGroupedBloc(ownerId)) {
            return ElementStyleAdjustment.NONE;
        }
        return contentInputs.viewRecedeAdjustment();
    }

    // Genuine independent space always takes the independent category. A lone faction the recede
    // has desaturated takes it too: desaturation means "read as backdrop", so the bloc adopts the
    // independent borders and seams paired with the desaturation palette the same adjustment
    // carries, rather than sitting at full border weight with only its colour greyed. A group always
    // paints at full strength so it stands out. Muting never moves the category; it only dims the
    // active style, so a lone faction that neither desaturates nor mutes reads exactly as a faction.
    //
    // The test reads the passed adjustment - every reason to recede already unioned into it - rather
    // than a toggle of its own, so the bundle and the palette can never disagree about whether a bloc
    // is desaturated.
    @Override
    public MapStyleCategory resolveCategory(String ownerId, ElementStyleAdjustment adjustment) {

        var isIndependent = Factions.INDEPENDENT.equals(ownerId)
            || (!grouping.isGroupedBloc(ownerId) && adjustment.shouldDesaturate());

        return isIndependent
            ? OwnerMapCategory.INDEPENDENT
            : OwnerMapCategory.FACTION;
    }

    // The neutral faction's own colour, which is what a system nobody holds has always painted in.
    @Override
    public Color resolveUnownedColour() {

        return StarsectorFactionColours.resolveNeutralColour(sector);
    }

    // Independent's own two shades, which the tier darkens into the receded backdrop so it sits below
    // genuine independent space - a spotlit bloc, even Independent at full strength, therefore reads
    // distinctly against it.
    @Override
    public OwnerPalette resolveRecedePalette() {

        var independent = StarsectorFactionColours.resolvePalette(sector, Factions.INDEPENDENT);
        return new OwnerPalette(independent.primaryColour(), independent.secondaryColour());
    }

    // The faction's name in the player's chosen format: the abbreviated display name for Short, the
    // long-form title for Full (the default). getDisplayName is a faction's short name and
    // getDisplayNameLong its full title; both may be blank, which the caller then treats as an
    // unresolved name.
    private static String resolveFactionName(
            FactionAPI faction,
            FactionNameFormatChoice nameFormat) {

        return nameFormat == FactionNameFormatChoice.SHORT
            ? faction.getDisplayName()
            : faction.getDisplayNameLong();
    }
}
