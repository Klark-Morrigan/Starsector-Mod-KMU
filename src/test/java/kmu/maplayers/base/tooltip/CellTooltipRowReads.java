package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.TooltipRow;

/**
 * How a test reads one line of a cell tooltip, and the tier values the vocabulary lays lines out at.
 * Shared because a row is authored in one place ({@link CellTooltipRows}) and asserted in several - the
 * vocabulary's own tests plus every resolver that contributes a single line - and a copy of the indent
 * per test class is a value that can drift from the one the tooltip actually draws at while every copy
 * goes on agreeing with itself.
 *
 * <p>The values are restated here rather than read from {@link CellTooltipRows}, whose own are private:
 * a constant read from the class under test would be edited alongside it and could never fail, while
 * these have to be changed deliberately when the layout does.
 */
public final class CellTooltipRowReads {

    /** The inset a nested line draws at - what makes it read as belonging to the line above it. */
    public static final float MEMBER_INDENT = 14f;

    /** Where a top-tier or standalone line sits: flush at the box's left content edge. */
    public static final float NO_INDENT = 0f;

    /** Float comparison slack for the indents above, which are laid out in UI units. */
    public static final float TOLERANCE = 0.001f;

    private CellTooltipRowReads() {
    }

    /**
     * Reads one of a line's label runs by position. The runs are a sequence read as one sentence, so a
     * test names which stretch of the line it is about rather than reaching through the row's content.
     *
     * @param row      the line to read
     * @param runIndex the run's position in the label, in reading order
     * @return that run's text and the colour it draws in
     */
    public static TextSpan readLabelRun(TooltipRow row, int runIndex) {
        return row.labelTextSpans().get(runIndex);
    }
}
