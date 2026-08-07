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
 * @param indexText        the line's place in the ordering it belongs to, run on after its name in
 *                         the quiet shade, or null where the line has no place worth stating
 * @param indexOutcome     what that place did for the line - nothing, or the winning or losing of
 *                         what the ordering settles; {@link CellTooltipIndexOutcome#UNCONTESTED} for
 *                         a place that decided nothing, and for a line stating none at all
 * @param qualifierText    the status called out at the end of the line, or null when it states none
 * @param valueText        what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for a
 *                         line carrying no number
 * @param valueWorkingText the arithmetic the number came out of, stated before it, or null where the
 *                         line shows its number alone
 * @param isWorkingOnly    whether the line is working throughout rather than one of the things the
 *                         block lists - an aside stating how a number above it was arrived at. Such a
 *                         line reads in the quiet shade its name and all, and only the number it
 *                         arrives at stays a finding
 */
public record CellTooltipEntryLine(
    String iconSpritePath,
    String labelText,
    String indexText,
    CellTooltipIndexOutcome indexOutcome,
    String qualifierText,
    String valueText,
    String valueWorkingText,
    boolean isWorkingOnly) {

    // What a line with no place to state carries in the index slot, for the same reason the two
    // absences below are named: the factory says what the plainest line has rather than passing
    // three unexplained nulls a reader has to count off against the components.
    private static final String NO_INDEX = null;

    // What a line the block lists in its own right carries: it is a finding rather than the working
    // behind one, which is the ordinary case and what every factory below builds.
    private static final boolean IS_A_FINDING = false;

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

        // An outcome handed over as null reads as one nothing turned on, so a hand-built line cannot
        // fail inside a draw over a part it never meant to state.
        indexOutcome = indexOutcome == null ? CellTooltipIndexOutcome.UNCONTESTED : indexOutcome;
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
            NO_INDEX,
            CellTooltipIndexOutcome.UNCONTESTED,
            NO_QUALIFIER,
            valueText,
            NO_WORKING,
            IS_A_FINDING);
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
            indexText,
            indexOutcome,
            qualifierText,
            valueText,
            valueWorkingText,
            isWorkingOnly);
    }

    /**
     * Returns a copy of this line stating {@code indexText} after its name - where the thing on the
     * line falls in whatever ordering it belongs to, such as the listing that settles a tie between
     * two of them.
     *
     * <p>Read quietly rather than in the qualifier's gold, and set before it, because it identifies
     * the line rather than saying anything about it: a reader scanning names should meet it as part
     * of the name and not as a second finding. Its own part rather than run into the label, so the
     * block can draw it in that quieter shade at all.
     *
     * @param indexText     the line's place in its ordering, unspaced - the line parts it from the
     *                      name when it is laid out
     * @param indexOutcome  what that place did for the line, which is what decides whether it reads
     *                      as a bare identifier or as the reason this line beat another
     * @return an otherwise-identical line stating that place
     */
    public CellTooltipEntryLine indexedAt(
            String indexText,
            CellTooltipIndexOutcome indexOutcome) {

        return new CellTooltipEntryLine(
            iconSpritePath,
            labelText,
            indexText,
            indexOutcome,
            qualifierText,
            valueText,
            valueWorkingText,
            isWorkingOnly);
    }

    /**
     * Returns a copy of this line read as working throughout - an aside stating how a number above it
     * was arrived at, rather than one of the things the block lists.
     *
     * <p>The box already parts a finding from the working behind it inside a value, in the shade each
     * is drawn in. A line that is <em>all</em> working - the arithmetic of a term the list above it
     * shares - says the same thing about itself, so it takes the same quiet shade for its name as its
     * working does, and only the number it arrives at stays bright. Left as a fact about the line
     * rather than a colour, so which shade means "working" is settled in one place for both.
     *
     * @return an otherwise-identical line read as the working behind a number rather than as a finding
     */
    public CellTooltipEntryLine readsAsWorking() {
        return new CellTooltipEntryLine(
            iconSpritePath,
            labelText,
            indexText,
            indexOutcome,
            qualifierText,
            valueText,
            valueWorkingText,
            true);
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
            indexText,
            indexOutcome,
            qualifierText,
            valueText,
            valueWorkingText,
            isWorkingOnly);
    }
}
