package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.base.tooltip.CellTooltipRows;

/**
 * A faction as something a hovered system's breakdown lists: its crest, its name, and whatever the block
 * listing it counts it in.
 *
 * <p>The adapter between a political fact and the layer-blind {@linkplain CellTooltipEntryLine line}
 * model a block is populated from - what turns a faction id into words a list can hold. A line rather
 * than a whole entry, because every caller has something further to say about it: what hangs beneath
 * it, or how loudly its number reads. How the faction itself appears is not decided here but taken
 * from {@link FactionPresentation}, so a faction listed on one line and the same faction named
 * anywhere else in the box cannot appear as two; what the line is about stays with whoever is making
 * that point.
 */
public final class FactionTooltipLine {

    private FactionTooltipLine() {
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
            resolveCrestMark(presentation),
            presentation.fullName(),
            valueText);
    }

    /**
     * Builds that same line from the number the block counts the faction in, for the ordinary case of a
     * faction listed at a score - which is a number the block's own arithmetic adds up.
     *
     * <p>The count travels rather than words for it so the line carries the figure it shows: a listing
     * too long to draw whole is closed by a row summing what it left out, and only lines holding their
     * number can be summed.
     *
     * @param sector       the sector the faction's name and crest are read from
     * @param factionId    the id of the faction the line names; an id the sector no longer knows is
     *                     shown as itself rather than leaving the line nameless
     * @param countedValue what the block counts this line in
     * @return the line, ready to be listed or nested
     */
    public static CellTooltipEntryLine buildCountedFactionLine(
            SectorAPI sector,
            String factionId,
            int countedValue) {

        var presentation = FactionPresentation.resolvePresentation(sector, factionId);

        return CellTooltipEntryLine.createCountedLine(
            resolveCrestMark(presentation),
            presentation.fullName(),
            countedValue);
    }

    // The mark a faction's line opens on: its crest as its asset authored it, and none at all for a
    // faction with no authored crest. Shared by both shapes of line so a faction cannot be marked one
    // way where its number is a score and another way where it is not.
    private static CellTooltipMark resolveCrestMark(FactionPresentation presentation) {
        return CellTooltipMark.resolveMarkAsAuthored(presentation.crestSpritePath());
    }
}
