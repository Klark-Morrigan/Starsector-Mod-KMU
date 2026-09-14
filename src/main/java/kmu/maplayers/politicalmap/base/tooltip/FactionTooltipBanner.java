package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.layout.CellTooltipRows;

/**
 * A faction as a verdict about the hovered system as a whole: its crest and its name centred under the
 * box's title rather than laid as an entry in its table.
 *
 * <p>Held apart from {@link FactionTooltipLine} because the two make opposite points about the same
 * faction - one enters it in a list, the other has it speak for the system - and a shared builder is
 * what would eventually let a verdict be laid as a finding. The crest and the name are resolved through
 * the same {@link FactionPresentation} read either way, so a faction named in the banner and the same
 * faction named in the breakdown below cannot appear as two.
 */
public final class FactionTooltipBanner {

    private FactionTooltipBanner() {
    }

    /**
     * Builds the banner naming a faction, for a verdict about the hovered system that happens to name
     * one.
     *
     * @param sector    the sector the faction's name and crest are read from
     * @param factionId the ID of the faction the banner names; an ID the sector no longer knows is shown
     *                  as itself rather than leaving the line nameless
     * @return the row, ready to add to a body and to be qualified by the caller
     */
    public static TooltipRow.CentredRow buildFactionBanner(SectorAPI sector, String factionId) {
        var presentation = FactionPresentation.resolvePresentation(sector, factionId);

        return CellTooltipRows.buildBannerRow(
            presentation.crestSpritePath(),
            presentation.fullName());
    }
}
