package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A cell tooltip's body as its layer stated it: the blocks in reading order, and how deep the player
 * asked them to be read. What a layer hands back from one composition ({@link ComposedCellBody}), and
 * what the box lays out - once for a body that fits, and again for one that does not.
 *
 * <p>Held as blocks rather than as drawn lines because how much of the body there is room for is not a
 * question composition can answer. A box is sized by its content and then clamped to the screen, so
 * what a hovered system holds decides the height and only the screen decides what fits - which is known
 * after the reading, not during it. Laying out from the blocks each time is what lets the same body be
 * drawn whole, drawn compressed, or drawn with its longest listings stood in for, from one read of the
 * sector.
 *
 * <p>Two cuts are spent here, and they answer different questions. The detail level is the player's
 * standing choice about how far into the system to read, so it is applied the same way over every
 * system: a tier the level does not admit is not drawn anywhere, and nothing says it was left out - the
 * hint at the foot of the box already offers it. The entry allowance is the box's answer to one system
 * being too large for the screen, so what it leaves out <em>is</em> stated, on a row standing for it
 * ({@link WithheldEntriesLine}).
 *
 * <p>The allowance is spent at every listing rather than on the blocks' own entries alone. A box runs
 * long by depth as much as by breadth - a colony under each faction, the terms under each colony - and
 * an allowance reaching only the top of the body would drop whole factions while leaving every term of
 * the one that survived. Spent at each level, the same number takes the tail off whichever listings are
 * long, wherever they sit.
 *
 * <p>What is dropped is always the tail, which is the low-scoring end: every listing in the box is
 * ranked, so the entries a reader would look for first are the ones that survive. And at least the
 * first entry of a listing always does, so nothing is left standing over an empty account.
 */
public final class CellTooltipBlocks {

    /**
     * The allowance of a box drawn whole - larger than any listing, so nothing is stood in for. What
     * the box asks for first, since a body that fits must never be cut.
     */
    static final int NO_ENTRY_LIMIT = Integer.MAX_VALUE;

    /**
     * The narrowest a listing can be drawn at: its first entry alone, over a row standing for the rest.
     * Below this a heading would stand over nothing at all, which reads as a block whose contents
     * failed to resolve rather than as a box short of room. Where the search for an allowance starts.
     */
    static final int LEAST_ENTRY_ALLOWANCE = 1;

    // What a body with no listing at all is stood against when the box bounds its search for an
    // allowance: there is nothing to take a tail off, so every allowance draws the same body.
    private static final int NO_LISTING = 0;

    /**
     * A layer with nothing to say about the system. Carried by {@link ComposedCellBody#NOTHING}, and
     * the one body that draws no box at all - so the level it would have been read at is never asked
     * for, there being no listing to cut.
     */
    static final CellTooltipBlocks NOTHING =
        new CellTooltipBlocks(HoverTooltipDetailLevel.FACTIONS, List.of());

    // How deep every block of this body may be read - one choice about the box rather than about any
    // block in it, so a body drawn at two depths is a state the player has no way to ask for and no
    // way to read.
    private final HoverTooltipDetailLevel detailLevel;

    private final List<CellTooltipBlock> blocks;

    CellTooltipBlocks(HoverTooltipDetailLevel detailLevel, List<CellTooltipBlock> blocks) {
        this.detailLevel = detailLevel;
        this.blocks = List.copyOf(blocks);
    }

    /**
     * The blocks this body reads as when there is room for all of it - what the box draws over nearly
     * every system, and what a reader of one layer's composition is asking about.
     *
     * @return the blocks as drawn, in reading order; empty where the layer had nothing to say
     */
    public List<TooltipSection> readSections() {
        return readBodyWithin(NO_ENTRY_LIMIT).sections();
    }

    /**
     * Whether the layer found anything to say about the system at all, which is what settles whether a
     * box is drawn: a lone system name repeats what the cursor already sits on.
     *
     * @return true where the body holds no block whatsoever
     */
    boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * Lays this body out with no listing drawing more than {@code entryAllowance} of its entries, the
     * rest stood for by one row apiece.
     *
     * @param entryAllowance the most entries any one listing may draw; floored at the first entry, so
     *                       no listing is emptied. {@link #NO_ENTRY_LIMIT} draws the body whole
     * @return the blocks as drawn and how many entries they left out
     */
    DrawnCellBody readBodyWithin(int entryAllowance) {

        return new BodyWalk(detailLevel, Math.max(LEAST_ENTRY_ALLOWANCE, entryAllowance))
            .walkBlocks(blocks);
    }

    /**
     * How many entries the longest listing anywhere in this body holds, at the depth the level admits.
     *
     * <p>What bounds the search for an allowance: no allowance above this takes a tail off anything, so
     * a larger one draws the very body {@link #NO_ENTRY_LIMIT} does.
     *
     * @return the longest listing's entry count; zero for a body that lists nothing
     */
    int countLongestListing() {

        var longestListing = NO_LISTING;
        for (var block : blocks) {
            longestListing = Math.max(
                longestListing,
                countLongestListing(block.entries(), CellTooltipEntryLevel.LISTED_LEVEL));
        }
        return longestListing;
    }

    // The longest listing at or beneath one the walk has reached, counted under the same cut the
    // laying-out applies: a tier the level does not admit is never drawn, so its length has no say in
    // how far the allowance has to come down.
    private int countLongestListing(
            List<CellTooltipEntry> entries,
            CellTooltipEntryLevel level) {

        if (!level.isAdmittedBy(detailLevel)) {
            return NO_LISTING;
        }
        var longestListing = entries.size();

        for (var entry : entries) {
            longestListing = Math.max(
                longestListing,
                countLongestListing(entry.children(), resolveChildLevel(entry, level)));
        }
        return longestListing;
    }

    // Where the things one entry carries stand. Both relations set them in a step, so the listing reads
    // as a tree either way; only an entry whose children are its account puts them a step further under
    // the box's voice, so an alliance's member factions stay as loud as the alliance while a market
    // beneath one of them quietens. Read off the entry rather than off the depth reached, since depth
    // cannot tell the two apart.
    private static CellTooltipEntryLevel resolveChildLevel(
            CellTooltipEntry entry,
            CellTooltipEntryLevel level) {

        return entry.isSubordinatingChildren()
            ? level.subordinatedUnder()
            : level.groupedUnder();
    }

    /**
     * One laying-out of a body, in progress: the depth it is read to, the most entries any listing may
     * draw, and how many it has left out so far.
     *
     * <p>Bound together and used once because the tally is a fact about the whole walk - the box states
     * one figure for everything it withheld - while the two cuts are fixed for its whole length. A walk
     * that carried them line by line could apply one depth to one block and another to the next, which
     * is a box that is two readings of one system at once.
     */
    private static final class BodyWalk {

        // What the tally has counted before the walk has withheld anything.
        private static final int NOTHING_WITHHELD = 0;

        private final HoverTooltipDetailLevel detailLevel;
        private final int entryAllowance;

        private int withheldEntryCount = NOTHING_WITHHELD;

        private BodyWalk(HoverTooltipDetailLevel detailLevel, int entryAllowance) {
            this.detailLevel = detailLevel;
            this.entryAllowance = entryAllowance;
        }

        // The body as one block per statement the layer made: each block's own lines, over as much of
        // what it lists as this walk admits.
        private DrawnCellBody walkBlocks(List<CellTooltipBlock> blocks) {

            var sections = new ArrayList<TooltipSection>(blocks.size());

            for (var block : blocks) {
                sections.add(TooltipSection
                    .createSection(block.openingRows())
                    .nesting(walkEntries(block.entries(), CellTooltipEntryLevel.LISTED_LEVEL)));
            }
            return new DrawnCellBody(sections, withheldEntryCount);
        }

        // Lays a listing out in reading order: each entry as a nested block of its own line over
        // everything it is made up of, before the next entry at this level. Depth-first is what puts a
        // breakdown where a reader looks for it - under the thing it breaks down - and the level
        // carried along is the only thing the vocabulary needs to lay each line where it belongs, so
        // any shape of listing draws through this one walk.
        //
        // Nested rather than flattened because the grouping is what the box spaces by: an entry that
        // broke down into an account of its own is one thing, and the next entry at its tier stands
        // clear of the whole of it rather than of its last line. The walk states only what is part of
        // what; how far that sets two of them apart is the widget's.
        //
        // The detail cut is one question per tier rather than one per line: neither relation ever lifts
        // a line back towards the box's own voice, so nothing beneath a tier the level has declined
        // could be admitted either, and the walk stops there rather than descending to reject each line
        // in turn.
        private List<TooltipSection> walkEntries(
                List<CellTooltipEntry> entries,
                CellTooltipEntryLevel level) {

            if (!level.isAdmittedBy(detailLevel)) {
                return List.of();
            }
            var drawnEntryCount = Math.min(entries.size(), entryAllowance);
            var sections = new ArrayList<TooltipSection>(drawnEntryCount);

            for (var entry : entries.subList(0, drawnEntryCount)) {
                sections.add(TooltipSection
                    .createSection(List.of(CellTooltipRows.buildListedRow(entry.line(), level)))
                    .nesting(walkEntries(entry.children(), resolveChildLevel(entry, level))));
            }
            appendWithheldSection(sections, entries.subList(drawnEntryCount, entries.size()), level);

            return sections;
        }

        // Closes a listing the allowance cut short with the row standing for its tail, laid at the very
        // level the entries it stands for would have been laid at - so it reads as the last of them
        // rather than as a note in the box's own voice.
        //
        // The tally is spent here, where the entries are actually dropped, so the figure at the foot of
        // the box counts exactly what the rows above it stand for.
        private void appendWithheldSection(
                List<TooltipSection> sections,
                List<CellTooltipEntry> withheldEntries,
                CellTooltipEntryLevel level) {

            if (withheldEntries.isEmpty()) {
                return;
            }
            withheldEntryCount += withheldEntries.size();

            sections.add(TooltipSection.createSection(List.of(CellTooltipRows.buildListedRow(
                WithheldEntriesLine.buildLine(withheldEntries),
                level))));
        }
    }
}
