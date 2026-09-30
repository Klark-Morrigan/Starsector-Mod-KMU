package kmu.maplayers.ownermap.render.style;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.factions.StarsectorFactionColours;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SystemOwner;

/**
 * The live binding of {@link BlocPaletteReader}: a bloc's two shades read off the faction it
 * paints in.
 *
 * <p>A bloc is not always a faction - a group is named by a synthetic ID no
 * {@code FactionAPI} answers to - so the grouping names the colour faction first and the sector is
 * asked for that. The two shades are the faction's own authored UI pair, bright into the primary
 * slot and dark into the secondary, so whatever is drawn for a bloc draws it in the same colours its
 * fills and borders already give it. This is where a faction's palette becomes an
 * {@link OwnerPalette}: the tier paints owners, and a faction is one source of an owner's shades.
 *
 * <p>Holds the sector and grouping the pass sampled rather than reading either live, so everything
 * one pass colours for a bloc is coloured off the one snapshot the fills were.
 */
public final class SectorBlocPalettes implements BlocPaletteReader {

    private final SectorAPI sector;
    private final HolderGrouping grouping;

    /**
     * @param sector   the sector the colour faction is read from
     * @param grouping the grouping that names each bloc's colour faction, sampled once by the
     *                 pass so everything drawn for a bloc resolves its colours identically
     */
    public SectorBlocPalettes(SectorAPI sector, HolderGrouping grouping) {
        this.sector = sector;
        this.grouping = grouping;
    }

    /**
     * A faction's authored pair as the owner shades the tier paints in - the one place a faction's
     * palette crosses into the tier's type, slot for slot.
     *
     * @param palette the faction's bright and dark shades
     * @return the same two shades as an owner's
     */
    public static OwnerPalette adoptFactionPalette(FactionPalette palette) {
        return new OwnerPalette(palette.primaryColour(), palette.secondaryColour());
    }

    @Override
    public OwnerPalette readBlocPalette(String blocId) {

        var palette = StarsectorFactionColours.findPalette(
            sector,
            grouping.resolveColourFactionId(blocId));

        // No colour faction is the degenerate case every caller drops the bloc on: a bloc gone
        // from the sector has no shades, and painting it a stand-in colour would put colour on the
        // map for something the map cannot name.
        return palette == null
            ? null
            : adoptFactionPalette(palette);
    }

    /**
     * The render-ready owner for one bloc: its ID paired with the two shades its cells may draw in.
     *
     * <p>The colour faction is the grouping's, not the bloc: a faction bloc is its own colour
     * faction and a group's bloc takes its leading member's - so resolving it here keeps the bloc
     * ID, which for a group is not a faction ID, out of the {@code FactionAPI} lookup. Which map
     * element uses which slot is the player's choice, made downstream in the render stages.
     *
     * <p>Every holder source turns a bloc into an owner this way, and the filter's own presence
     * resolver reuses it under a synthetic key.
     *
     * @param blocId the bloc to colour, carried on the returned owner as its ID
     * @return the render-ready owner, or null when the colour faction does not resolve, which
     *         drops the system as unowned
     */
    public SystemOwner resolveOwnerOf(String blocId) {

        var palette = readBlocPalette(blocId);
        return palette == null
            ? null
            : new SystemOwner(blocId, palette);
    }
}
