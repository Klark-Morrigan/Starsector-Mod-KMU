package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.LabelRun;
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

    /** Where a top-tier line sits: at no indent of its own, before the crest gutter is reserved. */
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
     * @return that run - a stretch of text and the colour it draws in, or an image set among the words
     */
    public static LabelRun readLabelRun(TooltipRow row, int runIndex) {
        return row.labelRuns().get(runIndex);
    }

    /**
     * Reads one of a line's label runs as a stretch of text, for a test asserting on what a run says or
     * the colour it draws in rather than on the run as a whole. A run that turns out to be an image
     * fails the cast, which is the right answer: a test reaching for words found a line that does not
     * say them where it expected.
     *
     * @param row      the line to read
     * @param runIndex the run's position in the label, in reading order
     * @return that run's text and the colour it draws in
     */
    public static TextSpan readLabelTextRun(TooltipRow row, int runIndex) {
        return (TextSpan) readLabelRun(row, runIndex);
    }
}
