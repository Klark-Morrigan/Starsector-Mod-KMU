package kmu.maplayers.base.tooltip;

import java.util.List;
import java.util.Objects;

/**
 * One entry of a cell-tooltip block: the thing being listed, and whatever it is made up of. An alliance
 * over its member factions is one; a market over the factors its score is summed from, each over its own
 * tiers, is another; a lone faction, or anything else that breaks down no further, is the same entry
 * carrying nothing.
 *
 * <p>An entry is made up of entries rather than of lines, so how deep a listing goes is settled by the
 * subject matter and not by the model holding it. What a block lists is the only side that knows whether
 * the things in it break down, and how far - so a two-tier ranking and a three-tier breakdown are the
 * same construct listed to different depths, laid out by one walk that reads the tier off how deep it
 * has gone ({@link CellTooltipSections}).
 *
 * <p>An entry with nothing under it is the ordinary case, not a degenerate one: it is what a block of
 * plain lines is made of. Whether an entry breaks down at all is therefore settled by whoever resolves
 * it - the one place that knows the kind of thing it is - and the block below simply lays out whatever
 * it is handed.
 *
 * @param line     the entry itself - what a reader is being told about
 * @param children what it is made up of, in the order they are read; empty for an entry that breaks
 *                 down no further
 */
public record CellTooltipEntry(
    CellTooltipEntryLine line,
    List<CellTooltipEntry> children) {

    /**
     * Copies the children and rejects an entry with no line of its own, at construction. The copy is what
     * keeps an entry from changing under a block that has already been handed it - a resolver ordinarily
     * reuses the list it built the children in.
     */
    public CellTooltipEntry {
        Objects.requireNonNull(line, "line");
        children = List.copyOf(children);
    }

    /**
     * Builds an entry that breaks down no further - what a block of plain lines is made of. What an entry
     * is made up of is layered on with {@link #nesting} by a resolver that has it, so a caller listing
     * flat content never states an emptiness it has nothing to say about.
     *
     * @param line the entry itself
     * @return the entry, carrying nothing beneath it
     */
    public static CellTooltipEntry createEntry(CellTooltipEntryLine line) {
        return new CellTooltipEntry(line, List.of());
    }

    /**
     * Returns a copy of this entry made up of {@code children}, read beneath it. Each of those carries
     * whatever it is itself made up of, so a resolver states one level per call rather than flattening a
     * breakdown it is only part-way through.
     *
     * @param children what this entry is made up of, in the order they are read
     * @return an otherwise-identical entry carrying them
     */
    public CellTooltipEntry nesting(List<CellTooltipEntry> children) {
        return new CellTooltipEntry(line, children);
    }
}
