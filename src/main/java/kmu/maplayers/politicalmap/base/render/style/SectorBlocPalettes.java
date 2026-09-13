package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * The live binding of {@link BlocPaletteReader}: a bloc's two shades read off the faction it
 * paints in.
 *
 * <p>A bloc is not always a faction - an alliance bloc is named by a synthetic ID no
 * {@code FactionAPI} answers to - so the grouping names the colour faction first and the sector is
 * asked for that. The two shades are the faction's own authored UI pair, so whatever is drawn for
 * a bloc draws it in the same colours its fills and borders already give it.
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

    @Override
    public FactionPalette readBlocPalette(String blocId) {

        var faction = sector == null
            ? null
            : sector.getFaction(grouping.resolveColourFactionId(blocId));

        // No colour faction is the degenerate case every caller drops the bloc on: a bloc gone
        // from the sector has no shades, and painting it a stand-in colour would put colour on the
        // map for something the map cannot name.
        return faction == null
            ? null
            : new FactionPalette(faction.getBrightUIColor(), faction.getDarkUIColor());
    }
}
