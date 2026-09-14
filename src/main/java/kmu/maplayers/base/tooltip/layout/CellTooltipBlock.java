package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;

import java.util.List;

/**
 * One block of a cell tooltip's body as its layer stated it: the lines the block opens with, and
 * whatever it lists beneath them. A heading over the things it names is one; a line speaking for the
 * hovered system as a whole, listing nothing, is the other.
 *
 * <p>Held as the layer's statement rather than as drawn lines because the box is laid out more than
 * once. What a block lists is settled while the sector is being read; how much of that listing there is
 * room to draw is not known until the box has been measured against the screen, and a body already
 * flattened into lines could only be cut by unpicking rows it can no longer tell apart
 * ({@link CellTooltipBlocks}).
 *
 * <p>One shape for both kinds rather than two, because the difference between them is exactly a listing
 * of nothing: a banner is its own line and no entries, a heading is its own line over its entries, and
 * a walk that lays out both has no case to branch on. What separates them on screen - a banner speaking
 * across the box, a heading standing in the table - is settled in the line each was opened with, by
 * whoever composed it.
 *
 * @param openingRows the block's own lines, drawn above everything it lists; never empty
 * @param entries     what the block lists, in the order they are read; empty for a block that lists
 *                    nothing
 */
record CellTooltipBlock(
    List<TooltipRow> openingRows,
    List<CellTooltipEntry> entries) {

    /**
     * Copies both lists, so a layer that went on appending to either cannot reshape a block the box has
     * already been handed.
     */
    CellTooltipBlock {
        openingRows = List.copyOf(openingRows);
        entries = List.copyOf(entries);
    }
}
