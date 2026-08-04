package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.TooltipRow;

import java.util.List;

/**
 * How a cell tooltip's body is divided into blocks: a heading naming what follows it, parted from
 * whatever sits above. Composed here rather than by each body, because whether a heading appears at
 * all is a rule about the block and not about the body holding it - a heading left standing over no
 * lines reads as a block whose contents failed to resolve, which tells the player something untrue.
 *
 * <p>Held apart from {@link CellTooltipRows} because the two answer different questions - that decides
 * how one line reads, this how a run of them is grouped - so a body states only which blocks it has
 * and in what order, and two layers dividing different content still divide it alike.
 */
public final class CellTooltipSections {

    private CellTooltipSections() {
    }

    /**
     * Appends a block - its heading over its lines - to a body, and nothing at all when the block has
     * no lines. Adding every block through here is what leaves the order they read in stated by the
     * order of the calls, rather than by a rule spread across the body making them.
     *
     * @param rows        the body being built, appended to in place
     * @param headingText the heading naming the block
     * @param sectionRows the block's own lines; empty leaves the body untouched
     */
    public static void appendSection(
            List<TooltipRow> rows,
            String headingText,
            List<TooltipRow> sectionRows) {

        if (sectionRows.isEmpty()) {
            return;
        }
        // The heading opens a section, parting its block from the one above it, and carries neither
        // crest nor value: it names the block rather than being one of the entries inside it.
        rows.add(CellTooltipRows
            .buildTopTierRow(null, headingText, CellTooltipRows.NO_SCORE)
            .opensSection());

        rows.addAll(sectionRows);
    }
}
