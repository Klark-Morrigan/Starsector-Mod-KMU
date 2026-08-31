package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A cell tooltip's body under construction: the blocks stated so far, in the order they will read, and
 * the depth every one of them is laid out to. A block is a heading naming what follows it, over the
 * entries it names and whatever those entries are made up of.
 *
 * <p>The two travel as one thing because every block needs both, and a body handing them over per
 * block is a body free to state them differently: a layer that appended one block to the wrong list
 * would drop it silently, and one that passed a level to four blocks and forgot the fifth would draw a
 * box that is two depths at once. Opened once and appended to, neither can be restated at all.
 *
 * <p>A block takes {@linkplain CellTooltipEntry entries} rather than built lines, which is the whole
 * point of it. <em>What</em> a block lists, how far each of those things breaks down, and whether what it
 * carries are its peers or its account, is the layer's - it is the only side that knows the subject
 * matter - while which shape each line is laid in follows from the {@linkplain CellTooltipEntryLevel
 * level} the walk here found it at, drawn from the one vocabulary. So two layers listing unrelated
 * content still list it alike, and neither can author a tier of its own by reaching past the entries it
 * hands over.
 *
 * <p>Whether a heading appears at all is likewise a rule about the block and not about the layer
 * appending it: a heading left standing over no entries reads as a block whose contents failed to
 * resolve, which tells the player something untrue.
 *
 * <p>How far into what it lists a block is read is neither the block's nor the layer's, but the
 * player's, arriving as the {@linkplain HoverTooltipDetailLevel detail level} the body was opened at. A
 * layer hands over one tree however deep it was asked for, and the walk lays out as much of it as that
 * level admits - so the same tree reads at four depths without any layer holding four accounts of a
 * system that could come to disagree with each other.
 *
 * <p>What is drawn is settled here and nowhere else, so a layer that stops composing a tier the level
 * would not admit - which is worth doing wherever a tier is expensive to work out - changes nothing
 * about the box. The cut answers a tier it was never handed exactly as it answers one it declines.
 *
 * <p>Nothing here is answered about a block's marks. A mark rides inside the label of the line carrying
 * it ({@link CellTooltipRows}), so the walk states only what is part of what and how deep it went - what
 * a block lists has no bearing on where another block's lines open.
 *
 * <p>Held apart from {@link CellTooltipRows} because the two answer different questions - that decides
 * how one line reads, this which lines a block is and which shape each takes - so a layer states only
 * which blocks its body has, in what order, and what each lists.
 */
public final class CellTooltipBody {

    // The blocks stated so far, in reading order. Held rather than handed back and forth, because the
    // order the box reads in is exactly the order of the calls that filled it.
    private final List<TooltipSection> sections = new ArrayList<>();

    // How deep every block of this body may be read. Fixed for the body's whole life: the level is one
    // choice about the box rather than about any block in it, so a body drawn at two depths is a state
    // the player has no way to ask for and no way to read.
    private final HoverTooltipDetailLevel detailLevel;

    private CellTooltipBody(HoverTooltipDetailLevel detailLevel) {
        this.detailLevel = detailLevel;
    }

    /**
     * Opens an empty body to be read at {@code detailLevel}.
     *
     * @param detailLevel how deep the player has asked the box to read
     * @return the body, ready for its first block
     */
    public static CellTooltipBody openBody(HoverTooltipDetailLevel detailLevel) {
        return new CellTooltipBody(detailLevel);
    }

    /**
     * Appends a block - its heading over its entries - and nothing at all when the block lists nothing.
     *
     * @param headingText the heading naming the block
     * @param entries     what the block lists, in the order they are read; empty leaves the body
     *                    untouched
     */
    public void appendSection(String headingText, List<CellTooltipEntry> entries) {

        if (entries.isEmpty()) {
            return;
        }
        sections.add(TooltipSection
            .createSection(List.of(CellTooltipRows.buildSectionHeadingRow(headingText)))
            .nesting(resolveEntrySections(entries, CellTooltipEntryLevel.LISTED_LEVEL)));
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
     * <p>Untouched by the detail level: a banner speaks in the box's own voice, which every level
     * admits, so a system that has something to state about itself states it at every depth.
     *
     * @param bannerRow the line to state, or empty when the system has nothing to state
     */
    public void appendBannerSection(Optional<? extends TooltipRow> bannerRow) {
        bannerRow.ifPresent(row -> sections.add(TooltipSection.createSection(List.of(row))));
    }

    /**
     * The blocks this body came to, in reading order - what the box draws.
     *
     * @return the blocks, empty where the layer had nothing to say about the system
     */
    public List<TooltipSection> readSections() {
        return List.copyOf(sections);
    }

    // Lays a listing out in reading order: each entry as a nested block of its own line over everything
    // it is made up of, before the next entry at this level. Depth-first is what puts a breakdown where
    // a reader looks for it - under the thing it breaks down - and the level carried along is the only
    // thing the vocabulary needs to lay each line where it belongs, so any shape of listing draws
    // through this one walk.
    //
    // Nested rather than flattened because the grouping is what the box spaces by: an entry that broke
    // down into an account of its own is one thing, and the next entry at its tier stands clear of the
    // whole of it rather than of its last line. The walk states only what is part of what; how far that
    // sets two of them apart - and that a nested parting never piles onto the one above it - is the
    // widget's.
    //
    // The detail cut is one question per tier rather than one per line: neither relation ever lifts a
    // line back towards the box's own voice, so nothing beneath a tier the level has declined could be
    // admitted either, and the walk stops there rather than descending to reject each line in turn.
    private List<TooltipSection> resolveEntrySections(
            List<CellTooltipEntry> entries,
            CellTooltipEntryLevel level) {

        if (!level.isAdmittedBy(detailLevel)) {
            return List.of();
        }
        var entrySections = new ArrayList<TooltipSection>();
        for (var entry : entries) {

            entrySections.add(TooltipSection
                .createSection(List.of(CellTooltipRows.buildListedRow(entry.line(), level)))
                .nesting(resolveEntrySections(entry.children(), resolveChildLevel(entry, level))));
        }
        return entrySections;
    }

    // Where the things one entry carries stand. Both relations set them in a step, so the listing reads
    // as a tree either way; only an entry whose children are its account puts them a step further under
    // the box's voice, so an alliance's member factions stay as loud as the alliance while a market
    // beneath one of them quietens. Read off the entry rather than off the depth reached, since depth
    // cannot tell the two apart - and read here, since the walk is the one place holding both the entry
    // and the level it was found at.
    private static CellTooltipEntryLevel resolveChildLevel(
            CellTooltipEntry entry,
            CellTooltipEntryLevel level) {

        return entry.isSubordinatingChildren()
            ? level.subordinatedUnder()
            : level.groupedUnder();
    }
}
