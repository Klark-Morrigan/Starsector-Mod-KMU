package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.ui.widgets.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipRows;

/**
 * The line naming one faction inside a hovered system's breakdown: its crest, its name, and whatever
 * the line has to say about it in the value column.
 *
 * <p>A faction is named on a tooltip line for several different reasons - the one claiming a system,
 * the ones contesting it, the one holding it by decree - and each of those is a different fact about
 * the same faction. Resolving the crest and the name per reason would let two lines in one box
 * present the same faction differently, so how a faction appears on a line is settled once here and
 * what the line is about stays with whoever is making that point.
 */
public final class FactionTooltipRow {

    private FactionTooltipRow() {
    }

    /**
     * Builds the line naming a faction, indented under the block it belongs to.
     *
     * @param sector    the sector the faction's name and crest are read from
     * @param factionId the id of the faction the line names; an id the sector no longer knows is
     *                  shown as itself rather than leaving the line nameless
     * @param valueText the right-aligned value, or {@link CellTooltipRows#NO_SCORE} for a line
     *                  carrying none
     * @return the row, ready to add to a body
     */
    public static TooltipRow.TableRow buildFactionRow(
            SectorAPI sector,
            String factionId,
            String valueText) {

        var faction = sector.getFaction(factionId);

        return CellTooltipRows.buildNestedRow(
            FactionCrests.resolveCrestPath(faction),
            TooltipFactionNames.resolveLongName(faction, factionId),
            valueText);
    }
}
