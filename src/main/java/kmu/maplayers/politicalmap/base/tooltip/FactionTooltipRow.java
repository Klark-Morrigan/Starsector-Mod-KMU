package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipRows;

/**
 * The line naming one faction inside a hovered system's breakdown: its crest, its name, and whatever
 * the line has to say about it in the value column.
 *
 * <p>A faction is named on a tooltip line for several different reasons - the one claiming a system,
 * the ones contesting it, the one holding it by decree - and each of those is a different fact about
 * the same faction. How the faction itself appears is therefore not decided here but taken from
 * {@link FactionPresentation}: this settles only which shape of line the fact is stated on, and what
 * the line is about stays with whoever is making that point.
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

        var presentation = FactionPresentation.resolvePresentation(sector, factionId);

        return CellTooltipRows.buildNestedRow(
            presentation.crestSpritePath(),
            presentation.fullName(),
            valueText);
    }

    /**
     * Builds the banner naming a faction, centred under the box's title rather than laid as an entry in
     * its table - for a verdict about the hovered system as a whole that happens to name a faction.
     *
     * <p>The same crest and the same name as the entry line above, resolved through the same reads, so a
     * faction named in the banner and the same faction named in the breakdown below cannot appear as two
     * different factions. What differs is only where the line sits, which is the point being made about
     * it rather than anything about the faction.
     *
     * @param sector    the sector the faction's name and crest are read from
     * @param factionId the id of the faction the banner names; an id the sector no longer knows is shown
     *                  as itself rather than leaving the line nameless
     * @return the row, ready to add to a body and to be qualified by the caller
     */
    public static TooltipRow.CentredRow buildFactionBannerRow(SectorAPI sector, String factionId) {
        var presentation = FactionPresentation.resolvePresentation(sector, factionId);

        return CellTooltipRows.buildBannerRow(
            presentation.crestSpritePath(),
            presentation.fullName());
    }
}
