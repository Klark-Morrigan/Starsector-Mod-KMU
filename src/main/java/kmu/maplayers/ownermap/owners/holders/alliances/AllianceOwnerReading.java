package kmu.maplayers.ownermap.owners.holders.alliances;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategory;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.factions.FactionOwnerReading;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;

import java.awt.Color;

/**
 * The owner reading of a layer painting alliances: a bloc is an alliance where the grouping folds
 * factions into one, and a lone faction everywhere else.
 *
 * <p>It states only what an alliance adds and asks {@link FactionOwnerReading} for the rest. An
 * alliance wears the shades and crest of the member the grouping names its colour faction, and reads
 * by the alliance's own name. A lone faction reads exactly as it does on a layer painting factions.
 *
 * <p>An alliance always keeps full strength so it stands out against the backdrop. A lone faction is
 * that backdrop: it recedes of the layer's own accord, but only where some alliance exists for it to
 * sit behind - with none, it keeps full strength for the reason {@link FactionOwnerReading} recedes
 * nothing.
 *
 * <p>Holds the grouping the rebuild sampled, which is the snapshot {@link OwnerReading} asks for.
 * An alliance's colour faction is resolved through it alone; the faction reading asked after that
 * takes the ID as a faction's own.
 */
public final class AllianceOwnerReading
        implements OwnerReading {

    private final HolderGrouping grouping;
    private final FactionOwnerReading factions;

    /**
     * @param sector   the sector a member faction's shades, name and crest are read from; null yields
     *                 no shades, no names and no crests, and the fallback neutrals
     * @param grouping the grouping the rebuild folded factions into blocs under, sampled once
     */
    public AllianceOwnerReading(SectorAPI sector, HolderGrouping grouping) {

        this.grouping = grouping;
        this.factions = new FactionOwnerReading(sector);
    }

    // The colour faction's shades: an alliance's leading member, or a lone faction itself.
    @Override
    public OwnerPalette resolvePalette(String ownerId) {

        return factions.resolvePalette(grouping.resolveColourFactionId(ownerId));
    }

    // An alliance reads by its own name; a lone faction by its own, so it never drifts between the
    // groupings it can be folded under.
    @Override
    public String resolveName(String ownerId, OwnerNameFormatChoice nameFormat) {

        var groupName = grouping.resolveGroupName(ownerId);

        return groupName != null
            ? groupName
            : factions.resolveName(ownerId, nameFormat);
    }

    // The colour faction's crest, for the reason the shades are.
    @Override
    public String resolveCrestPath(String ownerId) {

        return factions.resolveCrestPath(grouping.resolveColourFactionId(ownerId));
    }

    // How far a lone faction recedes is the painting view's own backdrop, sampled into the rebuild's
    // preferences.
    @Override
    public ElementStyleAdjustment resolveStyleAdjustment(
            String ownerId,
            ContentInputs contentInputs) {

        if (!grouping.hasAnyGroupedBloc() || grouping.isGroupedBloc(ownerId)) {
            return ElementStyleAdjustment.NONE;
        }
        return contentInputs.viewRecedeAdjustment();
    }

    // An alliance always paints at full strength so it stands out; a lone faction takes the
    // category it would on a layer painting factions.
    @Override
    public MapStyleCategory resolveCategory(String ownerId, ElementStyleAdjustment adjustment) {

        return grouping.isGroupedBloc(ownerId)
            ? HolderCategory.FACTION
            : factions.resolveCategory(ownerId, adjustment);
    }

    @Override
    public Color resolveUnownedColour() {

        return factions.resolveUnownedColour();
    }

    @Override
    public OwnerPalette resolveRecedePalette() {

        return factions.resolveRecedePalette();
    }
}
