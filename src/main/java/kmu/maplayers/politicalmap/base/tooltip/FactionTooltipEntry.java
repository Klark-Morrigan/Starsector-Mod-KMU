package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipRows;

/**
 * A faction as something a hovered system's breakdown lists: its crest, its name, and whatever the block
 * listing it counts it in.
 *
 * <p>The adapter between a political fact and the layer-blind {@linkplain CellTooltipEntry entry} model
 * a block is populated with - what turns a faction id into a thing a list can hold. How the faction
 * itself appears is not decided here but taken from {@link FactionPresentation}, so a faction listed on
 * one line and the same faction named anywhere else in the box cannot appear as two; what the line is
 * about stays with whoever is making that point.
 */
public final class FactionTooltipEntry {

    private FactionTooltipEntry() {
    }

    /**
     * Builds the line naming a faction, for a caller composing an entry it is one part of - a member of
     * an alliance, say.
     *
     * @param sector    the sector the faction's name and crest are read from
     * @param factionId the id of the faction the line names; an id the sector no longer knows is shown
     *                  as itself rather than leaving the line nameless
     * @param valueText what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for a
     *                  line carrying no number
     * @return the line, ready to be listed or nested
     */
    public static CellTooltipEntryLine buildFactionLine(
            SectorAPI sector,
            String factionId,
            String valueText) {

        var presentation = FactionPresentation.resolvePresentation(sector, factionId);

        return CellTooltipEntryLine.createLine(
            presentation.crestSpritePath(),
            presentation.fullName(),
            valueText);
    }

    /**
     * Builds the faction as an entry in its own right - a faction that breaks down no further, which is
     * every faction listed anywhere but under an alliance.
     *
     * @param sector    the sector the faction's name and crest are read from
     * @param factionId the id of the faction the entry names; an id the sector no longer knows is shown
     *                  as itself rather than leaving the entry nameless
     * @param valueText what the block counts this entry in, or {@link CellTooltipRows#NO_SCORE} for an
     *                  entry carrying no number
     * @return the entry, ready to add to a block
     */
    public static CellTooltipEntry buildFactionEntry(
            SectorAPI sector,
            String factionId,
            String valueText) {

        return CellTooltipEntry.createEntry(buildFactionLine(sector, factionId, valueText));
    }
}
