package kmu.maplayers.base.tooltip;

import java.util.List;
import java.util.Objects;

/**
 * One entry of a cell-tooltip block: the thing being listed, and whatever it is made up of. An alliance
 * over its member factions is one; a lone faction, or anything else that breaks down no further, is the
 * same entry carrying no members.
 *
 * <p>Two types rather than one entry that nests itself, because the box has exactly two tiers and this
 * is what makes those the only two representable. A member line cannot itself carry members, so a third
 * indent nobody has laid out is a compile error at the call site rather than a rule policed by whatever
 * happens to draw the block.
 *
 * <p>An entry with no members is the ordinary case, not a degenerate one: it is what a block of plain
 * lines is made of. Whether an entry breaks down at all is therefore settled by whoever resolves it -
 * the one place that knows the kind of thing it is - and the block below simply lays out whatever
 * members it is handed.
 *
 * @param line        the entry itself - what a reader is being told about
 * @param memberLines what it is made up of, in the order they are read; empty for an entry that breaks
 *                    down no further
 */
public record CellTooltipEntry(
    CellTooltipEntryLine line,
    List<CellTooltipEntryLine> memberLines) {

    /**
     * Copies the members and rejects an entry with no line of its own, at construction. The copy is what
     * keeps an entry from changing under a block that has already been handed it - a resolver ordinarily
     * reuses the list it built the members in.
     */
    public CellTooltipEntry {
        Objects.requireNonNull(line, "line");
        memberLines = List.copyOf(memberLines);
    }

    /**
     * Builds an entry that breaks down no further - what a block of plain lines is made of. Members are
     * layered on with {@link #nesting} by a resolver that has them, so a caller listing flat content
     * never states an emptiness it has nothing to say about.
     *
     * @param line the entry itself
     * @return the entry, carrying no members
     */
    public static CellTooltipEntry createEntry(CellTooltipEntryLine line) {
        return new CellTooltipEntry(line, List.of());
    }

    /**
     * Returns a copy of this entry made up of {@code memberLines}, read beneath it.
     *
     * @param memberLines what this entry is made up of, in the order they are read
     * @return an otherwise-identical entry carrying those members
     */
    public CellTooltipEntry nesting(List<CellTooltipEntryLine> memberLines) {
        return new CellTooltipEntry(line, memberLines);
    }
}
