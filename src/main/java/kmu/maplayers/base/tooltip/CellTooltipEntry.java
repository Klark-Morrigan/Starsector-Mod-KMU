package kmu.maplayers.base.tooltip;

import java.util.List;
import java.util.Objects;

/**
 * One entry of a cell-tooltip block: the thing being listed, whatever it is made up of, and which of
 * the two ways those belong to it. An alliance over its member factions is one; a market over the
 * factors its score is summed from, each over its own tiers, is another; a lone faction, or anything
 * else that breaks down no further, is the same entry carrying nothing.
 *
 * <p>An entry is made up of entries rather than of lines, so how deep a listing goes is settled by the
 * subject matter and not by the model holding it. What a block lists is the only side that knows whether
 * the things in it break down, and how far - so a two-tier ranking and a three-tier breakdown are the
 * same construct listed to different depths, laid out by one walk that reads the tier off how deep it
 * has gone ({@link CellTooltipBody}).
 *
 * <p>Depth alone does not say what a listed thing is, which is why the two ways are told apart. Members
 * {@linkplain #grouping gathered} under a line are peers of it - one answer stated at two granularities,
 * as an alliance and the factions in it are - while entries {@linkplain #nesting nested} under it are
 * the account of why that line reads as it does. Both sit inset beneath their parent; only the second
 * stands a further step under the box's own voice ({@link CellTooltipEntryLevel}). Stated by whoever
 * resolves the entry, since that is the only side that knows which relation it is.
 *
 * <p>An entry with nothing under it is the ordinary case, not a degenerate one: it is what a block of
 * plain lines is made of. Whether an entry breaks down at all is therefore settled by whoever resolves
 * it - the one place that knows the kind of thing it is - and the block below simply lays out whatever
 * it is handed.
 *
 * @param line                    the entry itself - what a reader is being told about
 * @param children                what it is made up of, in the order they are read; empty for an entry
 *                                that breaks down no further
 * @param isSubordinatingChildren whether those children are its account rather than its peers; always
 *                                false where it has none
 */
public record CellTooltipEntry(
    CellTooltipEntryLine line,
    List<CellTooltipEntry> children,
    boolean isSubordinatingChildren) {

    // Which relation each refinement below states, named rather than passed as a bare flag so the two
    // factories read as what they mean at the point they are written.
    private static final boolean SUBORDINATES_CHILDREN = true;
    private static final boolean GROUPS_CHILDREN = false;

    /**
     * Copies the children, rejects an entry with no line of its own, and settles the relation of an
     * entry that lists nothing, at construction. The copy is what keeps an entry from changing under a
     * block that has already been handed it - a resolver ordinarily reuses the list it built the
     * children in. The relation is forced where there are no children because it then describes
     * nothing, and an entry that carries an answer to an unasked question compares unequal to the same
     * entry built the other way.
     */
    public CellTooltipEntry {
        Objects.requireNonNull(line, "line");
        children = List.copyOf(children);
        isSubordinatingChildren = !children.isEmpty() && isSubordinatingChildren;
    }

    /**
     * Builds an entry that breaks down no further - what a block of plain lines is made of. What an entry
     * is made up of is layered on with {@link #nesting} or {@link #grouping} by a resolver that has it,
     * so a caller listing flat content never states an emptiness it has nothing to say about.
     *
     * @param line the entry itself
     * @return the entry, carrying nothing beneath it
     */
    public static CellTooltipEntry createEntry(CellTooltipEntryLine line) {
        return new CellTooltipEntry(line, List.of(), GROUPS_CHILDREN);
    }

    /**
     * Returns a copy of this entry gathering {@code children} beneath it as its peers - the same kind of
     * statement it is, read at a finer granularity. The factions inside an alliance are the case: the
     * alliance's line and its members both answer who holds the system, so they read inset beneath it
     * without being demoted under the box's voice.
     *
     * @param children what this entry gathers, in the order they are read
     * @return an otherwise-identical entry carrying them as peers
     */
    public CellTooltipEntry grouping(List<CellTooltipEntry> children) {
        return new CellTooltipEntry(line, children, GROUPS_CHILDREN);
    }

    /**
     * Returns a copy of this entry made up of {@code children}, read beneath it as its account - what
     * the line above breaks down into. Each of those carries whatever it is itself made up of, so a
     * resolver states one level per call rather than flattening a breakdown it is only part-way through.
     *
     * @param children what this entry is made up of, in the order they are read
     * @return an otherwise-identical entry carrying them as its account
     */
    public CellTooltipEntry nesting(List<CellTooltipEntry> children) {
        return new CellTooltipEntry(line, children, SUBORDINATES_CHILDREN);
    }
}
