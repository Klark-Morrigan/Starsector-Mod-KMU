package kmu.maplayers.base.tooltip;

import java.util.Objects;

/**
 * One thing a cell-tooltip block lists: what it is called, the mark it is shown by, whatever the block
 * counts it in, and any status called out beside it. What is listed - and nothing whatever about how it
 * is laid.
 *
 * <p>Held as a value rather than as a built line because the two decisions belong on opposite sides of
 * the box. A layer knows what its block lists; the block ({@link CellTooltipSections}) knows the tier,
 * the colours, the crest gutter, and the value column a listed thing is laid in. So two layers listing
 * unrelated content still list it alike, and a body cannot quietly author a third look by reaching for
 * the line vocabulary itself.
 *
 * <p>Nothing here is faction-shaped. The mark is a texture path a caller may simply not have, so a list
 * of things that carry none - industries, conditions, hazards - is this same shape with a null in it.
 *
 * @param iconSpritePath the leading mark's texture path, or null for a line carrying none
 * @param labelText      what the line is called
 * @param qualifierText  the status called out at the end of the line, or null when it states none
 * @param valueText      what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for a
 *                       line carrying no number
 */
public record CellTooltipEntryLine(
    String iconSpritePath,
    String labelText,
    String qualifierText,
    String valueText) {

    // What a line states nothing beside its name and its number carries in the qualifier slot. Named
    // rather than passed as a bare null, so the factory below reads as "this line calls nothing out"
    // instead of as an unexplained absence.
    private static final String NO_QUALIFIER = null;

    /**
     * Rejects a nameless or valueless line at construction, where the caller that composed it is still
     * on the stack. Both are required because a block lays every line through the same two columns: a
     * null arriving in either would surface inside a measurement or a draw, well past the point that
     * could say which line was meant. A line carrying no number states {@link CellTooltipRows#NO_SCORE},
     * which is a value the column collapses for rather than an absence.
     */
    public CellTooltipEntryLine {
        Objects.requireNonNull(labelText, "labelText");
        Objects.requireNonNull(valueText, "valueText");
    }

    /**
     * Builds the plainest listed thing there is: a mark, a name, and a number, calling nothing out.
     * The qualifier is layered on with {@link #qualifiedWith} where a line has one, so a caller states
     * what its line <em>has</em> rather than passing a placeholder for the part it does not use.
     *
     * @param iconSpritePath the leading mark's texture path, or null for a line carrying none
     * @param labelText      what the line is called
     * @param valueText      what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for
     *                       a line carrying no number
     * @return the bare line
     */
    public static CellTooltipEntryLine createLine(
            String iconSpritePath,
            String labelText,
            String valueText) {

        return new CellTooltipEntryLine(
            iconSpritePath,
            labelText,
            NO_QUALIFIER,
            valueText);
    }

    /**
     * Returns a copy of this line calling {@code qualifierText} out at its end - a status stated on the
     * line it is about rather than on a line of its own, such as why this line outranks a higher-scoring
     * one beneath it.
     *
     * @param qualifierText the status called out at the end of the line
     * @return an otherwise-identical line ending on that status
     */
    public CellTooltipEntryLine qualifiedWith(String qualifierText) {
        return new CellTooltipEntryLine(
            iconSpritePath,
            labelText,
            qualifierText,
            valueText);
    }
}
