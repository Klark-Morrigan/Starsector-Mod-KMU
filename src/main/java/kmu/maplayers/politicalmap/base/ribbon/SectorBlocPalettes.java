package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * The live binding of {@link BlocPaletteReader}: a bloc's two shades read off the faction it
 * paints in.
 *
 * <p>A bloc is not always a faction - an alliance bloc is named by a synthetic id no
 * {@code FactionAPI} answers to - so the grouping names the colour faction first and the sector
 * is asked for that. The two shades are the faction's own authored UI pair, bright into the
 * market segments and dark into the interjections, so a band draws a bloc in the same colours
 * the fills and borders around it already give that bloc.
 *
 * <p>Holds the sector and grouping the pass sampled rather than reading either live, so every
 * band in a rebuild is coloured off the one snapshot the fills were.
 */
public final class SectorBlocPalettes implements BlocPaletteReader {

    private final SectorAPI sector;
    private final HolderGrouping grouping;

    /**
     * @param sector   the sector the colour faction is read from
     * @param grouping the grouping that names each bloc's colour faction, sampled once by the
     *                 pass so the band and the fill resolve a bloc's colours identically
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

        // No colour faction is the degenerate case the rule drops the bloc on: a bloc gone from
        // the sector has no shades, and painting it a stand-in colour would put a run on the band
        // for something the map cannot name.
        return faction == null
            ? null
            : new FactionPalette(faction.getBrightUIColor(), faction.getDarkUIColor());
    }
}
