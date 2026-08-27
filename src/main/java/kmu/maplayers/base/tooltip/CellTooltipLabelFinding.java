package kmu.maplayers.base.tooltip;

/**
 * The stretch of a line's own name that is one of the box's findings rather than part of what the
 * thing is called - a station named <em>Abandoned Station</em> saying what it is in its first word.
 *
 * <p>Held as character positions rather than as the words themselves, because the name is the only
 * copy of the name: a stretch carrying its own text could be drawn where the label says something
 * else, and the box would quietly disagree with the map about what a place is called. Positions can
 * only ever pick out what is already there.
 *
 * <p>One stretch rather than a list of them. A line states at most one finding inside its name, so
 * the part is a value the line either carries or does not - and a reading of what a colony is cannot
 * come out longer for a colony that happens to say the same word twice.
 *
 * @param startIndex where the finding begins in the label, counted in characters from its start
 * @param endIndex   the character position just past the finding's last, so the two read as the
 *                   half-open range every substring of the label is taken by
 */
public record CellTooltipLabelFinding(
    int startIndex,
    int endIndex) {

    /**
     * Rejects a range that picks out nothing, where the caller that resolved it is still on the
     * stack. A negative start or an end at or before it otherwise surfaces inside the draw that
     * splits the label, well past the point that could say which line was meant - and an empty range
     * is a caller that found no finding and should be stating none at all.
     */
    public CellTooltipLabelFinding {

        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex must not be negative");
        }
        if (endIndex <= startIndex) {
            throw new IllegalArgumentException("endIndex must be past startIndex");
        }
    }
}
