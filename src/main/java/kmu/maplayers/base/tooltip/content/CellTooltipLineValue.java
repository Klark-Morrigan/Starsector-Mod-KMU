package kmu.maplayers.base.tooltip.content;

import java.util.Objects;

/**
 * What a listed line states in its value column: the words for its value, the number behind them
 * where the block counts one, the working that number came out of, and whether anything earned it.
 *
 * <p>One part rather than four of the line's own, because everything here answers one question - what
 * the line is counted in - and is read together by whatever fills the value column or sums what a
 * listing left out. The line's other parts are about its name and say nothing about its number.
 *
 * @param valueText        what the block counts the line in, or {@link CellTooltipEntryLine#NO_SCORE}
 *                         for a line carrying no number
 * @param countedValue     the number the value states, where it is one the block's own arithmetic adds
 *                         up, or null where the value is not such a number. Never a second statement of
 *                         the value: a counted line is built from the number and words the value out of
 *                         it, so the two cannot come to disagree
 * @param valueWorkingText the arithmetic the number came out of, stated before it, or null where the
 *                         line shows its number alone
 * @param isValueUncounted whether the number is one nothing earned - what an account recorded for the
 *                         thing on the line rather than anything it did. Only the number quietens: the
 *                         line is one of the things the list holds and is named as loudly as its
 *                         neighbours
 */
public record CellTooltipLineValue(
    String valueText,
    Integer countedValue,
    String valueWorkingText,
    boolean isValueUncounted) {

    /**
     * Rejects a missing value at construction, where the caller that composed it is still on the
     * stack. A block lays every line through the same value column, so a line arriving with nothing
     * for it would surface inside a measurement or a draw, well past the point that could say which
     * line was meant. A line carrying no number states {@link CellTooltipEntryLine#NO_SCORE}, which is
     * a value the column collapses for rather than an absence.
     */
    public CellTooltipLineValue {
        Objects.requireNonNull(valueText, "valueText");
    }
}
