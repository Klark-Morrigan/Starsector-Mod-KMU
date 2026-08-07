package kmu.maplayers.base.tooltip;

import java.util.Objects;

/**
 * One thing a cell-tooltip block lists: what it is called, the mark it is shown by, whatever the block
 * counts it in, any working that number came out of, and any status called out beside it. What is
 * listed - and nothing whatever about how it is laid.
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
 * @param iconSpritePath   the leading mark's texture path, or null for a line carrying none
 * @param labelText        what the line is called
 * @param qualifierText    the status called out at the end of the line, or null when it states none
 * @param valueText        what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for a
 *                         line carrying no number
 * @param valueWorkingText the arithmetic the number came out of, stated before it, or null where the
 *                         line shows its number alone
 */
public record CellTooltipEntryLine(
    String iconSpritePath,
    String labelText,
    String qualifierText,
    String valueText,
    String valueWorkingText) {

    // What a line states nothing beside its name and its number carries in the qualifier slot. Named
    // rather than passed as a bare null, so the factory below reads as "this line calls nothing out"
    // instead of as an unexplained absence.
    private static final String NO_QUALIFIER = null;

    // What a line showing its number alone carries where the working would go. Named for the same
    // reason the absence above is: the factory says the line shows no working rather than passing an
    // unexplained null.
    private static final String NO_WORKING = null;

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
            valueText,
            NO_WORKING);
    }

    /**
     * Whether this line leads with a mark at all. The one place the absence is judged, so whatever reads
     * a listing to decide something about its marks - whether a block reserves the crest gutter, say -
     * and whatever lays the line out afterwards cannot disagree over a line neither of them can show a
     * mark for.
     *
     * @return true where the line carries a mark to lead with
     */
    public boolean hasMark() {
        return iconSpritePath != null;
    }

    /**
     * Returns a copy of this line calling {@code qualifierText} out at its end - a status stated on the
     * line it is about rather than on a line of its own, such as why this line outranks a higher-scoring
     * one beneath it.
     *
     * @param qualifierText the status called out at the end of the line, unspaced - the line parts it
     *                      from its label when it is laid out
     * @return an otherwise-identical line ending on that status
     */
    public CellTooltipEntryLine qualifiedWith(String qualifierText) {
        return new CellTooltipEntryLine(
            iconSpritePath,
            labelText,
            qualifierText,
            valueText,
            valueWorkingText);
    }

    /**
     * Returns a copy of this line stating {@code valueWorkingText} before its number - the arithmetic
     * the number came out of, such as the rate one of a counted thing is worth. The block draws the two
     * apart, so a reader can tell the finding from the working behind it at a glance.
     *
     * <p>Stated as its own part rather than folded into the value's text, because the two halves read
     * differently and the line is the only thing that knows where one ends: run together in one string
     * they can only be drawn in one shade, and a block splitting a value back apart would be guessing
     * at a separator its author never stated.
     *
     * @param valueWorkingText the arithmetic behind the line's number, unspaced - the line parts it from
     *                         the number when it is laid out
     * @return an otherwise-identical line showing that working
     */
    public CellTooltipEntryLine derivesValueFrom(String valueWorkingText) {
        return new CellTooltipEntryLine(
            iconSpritePath,
            labelText,
            qualifierText,
            valueText,
            valueWorkingText);
    }
}
