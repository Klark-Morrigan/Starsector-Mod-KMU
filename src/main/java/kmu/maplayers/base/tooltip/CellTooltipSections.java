package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a cell tooltip's body is divided into blocks, and how a block lays out what it lists: a heading
 * naming what follows it, over the entries it names and whatever those entries are made up of.
 *
 * <p>A block takes {@linkplain CellTooltipEntry entries} rather than built lines, which is the whole
 * point of it. <em>What</em> a block lists, and how far each of those things breaks down, is the layer's -
 * it is the only side that knows the subject matter - while which shape each line is laid in follows from
 * how deep the walk here found it, drawn from the one vocabulary. So two layers listing unrelated content
 * still list it alike, and neither can author a tier of its own by reaching past the entries it hands
 * over.
 *
 * <p>Whether a heading appears at all is likewise a rule about the block and not about the body holding
 * it: a heading left standing over no entries reads as a block whose contents failed to resolve, which
 * tells the player something untrue.
 *
 * <p>So is whether the crest gutter is reserved. The gutter is one column shared by everything a block
 * lists, and the box measures it across every block at once, so a block listing nothing that carries a
 * mark would open its lines behind a gutter another block's crests widened - an indent under its own
 * heading, standing for a column none of its lines can fill.
 *
 * <p>Held apart from {@link CellTooltipRows} because the two answer different questions - that decides
 * how one line reads, this which lines a block is and which shape each takes - so a body states only
 * which blocks it has, in what order, and what each lists.
 */
public final class CellTooltipSections {

    private CellTooltipSections() {
    }

    /**
     * Appends a block - its heading over its entries - to a body, and nothing at all when the block
     * lists nothing. Adding every block through here is what leaves the order they read in stated by the
     * order of the calls, rather than by a rule spread across the body making them.
     *
     * @param sections    the body being built, appended to in place
     * @param headingText the heading naming the block
     * @param entries     what the block lists, in the order they are read; empty leaves the body
     *                    untouched
     */
    public static void appendSection(
            List<TooltipSection> sections,
            String headingText,
            List<CellTooltipEntry> entries) {

        if (entries.isEmpty()) {
            return;
        }
        var rows = new ArrayList<TooltipRow>();
        rows.add(CellTooltipRows.buildSectionHeadingRow(headingText));
        appendEntryRows(rows, entries, CellTooltipRows.LISTED_DEPTH, hasAnyMark(entries));
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

    // Lays a listing out in reading order: each entry's own line, then everything it is made up of
    // directly beneath it, before the next entry at this level. Depth-first is what puts a breakdown
    // where a reader looks for it - under the thing it breaks down - and the depth carried along is the
    // only thing the vocabulary needs to lay each line at the right tier, so any shape of listing draws
    // through this one walk.
    private static void appendEntryRows(
            List<TooltipRow> rows,
            List<CellTooltipEntry> entries,
            int depth,
            boolean isReservingCrestColumn) {

        for (var entry : entries) {
            rows.add(CellTooltipRows.buildListedRow(entry.line(), depth, isReservingCrestColumn));
            appendEntryRows(rows, entry.children(), depth + 1, isReservingCrestColumn);
        }
    }

    // Whether anything the block lists, however deep, leads with a mark. Answered over the whole block
    // because the crest gutter is one column shared by all of its lines: reserved for a block where some
    // line carries a mark, so the markless ones stay aligned with it, and dropped for a block where none
    // does, since a gutter nothing fills is read as the block's lines being indented under their own
    // heading rather than as an empty column. The whole listing counts, not just its top level - a
    // breakdown carrying the marks would otherwise open its gutter under lines laid clear of it.
    private static boolean hasAnyMark(List<CellTooltipEntry> entries) {
        for (var entry : entries) {
            if (entry.line().hasMark() || hasAnyMark(entry.children())) {
                return true;
            }
        }
        return false;
    }
}
