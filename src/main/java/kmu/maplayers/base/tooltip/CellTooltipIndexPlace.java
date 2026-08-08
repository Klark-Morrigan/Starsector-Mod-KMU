package kmu.maplayers.base.tooltip;

import java.util.Objects;

/**
 * Where a line falls in the ordering it belongs to, and what that place did for it - a number the
 * reader is shown, and whether the ordering settled anything by it.
 *
 * <p>Held as one value because the two are never separately true: a place with no number states
 * nothing, and a number with no outcome cannot say whether it was the reason this line beat another.
 * Carried apart on the line they could drift - an outcome left behind by a re-numbered place would
 * mark the wrong line as having won - and nothing about a pair of loose components says they must be
 * set together.
 *
 * @param text    the place as the reader sees it, unspaced - the line parts it from the name when it
 *                is laid out
 * @param outcome what the place did for the line; {@link CellTooltipIndexOutcome#UNCONTESTED} where
 *                it settled nothing, which is the ordinary case
 */
public record CellTooltipIndexPlace(
    String text,
    CellTooltipIndexOutcome outcome) {

    /**
     * Rejects a numberless place at construction: a line with no place to state carries none at all
     * rather than one that says nothing, so an empty text here is a caller that meant to pass
     * nothing. An outcome handed over as null reads as one nothing turned on, so a hand-built place
     * cannot fail inside a draw over a part it never meant to state.
     */
    public CellTooltipIndexPlace {
        Objects.requireNonNull(text, "text");
        outcome = outcome == null ? CellTooltipIndexOutcome.UNCONTESTED : outcome;
    }
}
