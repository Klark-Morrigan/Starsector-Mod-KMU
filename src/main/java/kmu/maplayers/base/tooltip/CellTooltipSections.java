package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a cell tooltip's body is divided into blocks: a heading naming what follows it, over the lines it
 * names. Composed here rather than by each body, because whether a heading appears at all is a rule
 * about the block and not about the body holding it - a heading left standing over no lines reads as a
 * block whose contents failed to resolve, which tells the player something untrue.
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
     * @param sections    the body being built, appended to in place
     * @param headingText the heading naming the block
     * @param sectionRows the block's own lines; empty leaves the body untouched
     */
    public static void appendSection(
            List<TooltipSection> sections,
            String headingText,
            List<TooltipRow> sectionRows) {

        if (sectionRows.isEmpty()) {
            return;
        }
        var rows = new ArrayList<TooltipRow>();

        // The heading carries neither crest nor value: it names the block rather than being one of the
        // entries inside it. What parts it from the block above is the block it opens, not anything the
        // line itself asks for, so a heading is authored exactly as any other line is.
        rows.add(CellTooltipRows.buildTopTierRow(
            null,
            headingText,
            CellTooltipRows.NO_SCORE));

        rows.addAll(sectionRows);
        sections.add(new TooltipSection(rows));
    }

    /**
     * Appends a block holding {@code bannerRow} alone - a line speaking for the hovered system as a
     * whole ({@link CellTooltipRows#buildBannerRow}) rather than entering it in a list - and nothing
     * at all when there is none to state.
     *
     * <p>A block of one line is what a banner wants: what it says answers a different question from
     * whatever is listed beneath it, so it is parted from that rather than read as its opening entry.
     * Stated here because it is a rule about the box's shape, which two bodies stating it apart would
     * eventually state differently.
     *
     * <p>Takes the banner as an {@link Optional} because a resolver answering "does this system have
     * one" is what every caller is holding - so the absence rule stays here beside the empty-block
     * rule above rather than being spelled at each call site. The bound is open because such a
     * resolver answers with whichever kind of line it builds - a banner is a centred one - and a
     * block only ever reads what it is handed.
     *
     * @param sections  the body being built, appended to in place
     * @param bannerRow the line to state, or empty when the system has nothing to state
     */
    public static void appendBannerSection(
            List<TooltipSection> sections,
            Optional<? extends TooltipRow> bannerRow) {

        bannerRow.ifPresent(row -> sections.add(new TooltipSection(List.of(row))));
    }
}
