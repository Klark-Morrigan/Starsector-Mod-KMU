package kmu.maplayers.base.tooltip;

import kmlib.text.KmlibNumbers;

import java.util.List;
import java.util.Objects;

/**
 * One thing a cell-tooltip block lists: what it is called - or the shape of a name withheld from it -
 * the mark it is shown by, whatever the block counts it in, any working that number came out of, and
 * any status it calls out - after its name, or inside the name where the name already says it. What is
 * listed - and nothing whatever about how it is laid.
 *
 * <p>Held as a value rather than as a built line because the two decisions belong on opposite sides of
 * the box. A layer knows what its block lists; the block ({@link CellTooltipBody}) knows the tier,
 * the colours, and the value column a listed thing is laid in. So two layers listing
 * unrelated content still list it alike, and a body cannot quietly author a third look by reaching for
 * the line vocabulary itself.
 *
 * <p>Nothing here is faction-shaped. The mark is a texture path a caller may simply not have, so a list
 * of things that carry none - industries, conditions, hazards - is this same shape with a null in it.
 *
 * @param mark                the mark the line opens on and how it is coloured, or null for a line
 *                            carrying none. One value rather than a path beside a colouring, so a line
 *                            showing no mark has nowhere to state how one would have been drawn
 * @param labelText           what the line is called, or null where the name is withheld and the line
 *                            carries its shape instead
 * @param redactedWordLengths how many characters each word of the withheld name ran to, in reading
 *                            order, or null where the line says its name outright. The name itself
 *                            never reaches the line: what is held stands for the words without being
 *                            them, so there is nothing here for a later change to draw
 * @param labelFinding        the stretch of the name that is itself one of the box's findings, or null
 *                            where the name says none - a line calls at most one out inside its own
 *                            name
 * @param indexPlace          where the line falls in the ordering it belongs to and what that place
 *                            decided, run on after its name, or null where the line has no place worth
 *                            stating
 * @param noteText            a remark about the thing on the line that is not a finding about it -
 *                            how current what the box says about it is, say - run on at the end of the
 *                            line, or null where the line makes none
 * @param qualifier           the status called out after the line's name, or null when it states none
 * @param valueText           what the block counts this line in, or {@link CellTooltipRows#NO_SCORE}
 *                            for a line carrying no number
 * @param countedValue        the number the value states, where it is one the block's own arithmetic
 *                            adds up, or null where the line's value is not such a number. Never a
 *                            second statement of the value: a counted line is built from the number
 *                            and words the value out of it, so the two cannot come to disagree
 * @param valueWorkingText    the arithmetic the number came out of, stated before it, or null where
 *                            the line shows its number alone
 * @param isAside             whether the line is a note about the list rather than one of the things
 *                            in it - the arithmetic of a term its members share, say. Such a line is
 *                            drawn quiet down to its name, and only the number it arrives at stays a
 *                            finding
 * @param isValueUncounted    whether the line's number is one nothing earned - what an account
 *                            recorded for the thing on the line rather than anything it did. Only the
 *                            number quietens: the line is one of the things the list holds and is
 *                            named as loudly as its neighbours
 */
public record CellTooltipEntryLine(
    CellTooltipMark mark,
    String labelText,
    List<Integer> redactedWordLengths,
    CellTooltipLabelFinding labelFinding,
    CellTooltipIndexPlace indexPlace,
    String noteText,
    CellTooltipQualifier qualifier,
    String valueText,
    Integer countedValue,
    String valueWorkingText,
    boolean isAside,
    boolean isValueUncounted) {

    // What a line carries in each of the parts it does not use. Named one per part rather than passed
    // as bare nulls, so the factories below say what a line has none of instead of handing the
    // constructor a row of unexplained absences a reader has to count off against the components -
    // which is a count that goes wrong the moment a part is added. The first two are the one exclusive
    // pair: a line says its name and holds no redaction, or withholds it and holds no name.
    private static final String NO_NAME = null;
    private static final List<Integer> NO_REDACTION = null;
    private static final CellTooltipLabelFinding NO_LABEL_FINDING = null;
    private static final CellTooltipIndexPlace NO_PLACE = null;
    private static final String NO_NOTE = null;
    private static final CellTooltipQualifier NO_QUALIFIER = null;
    private static final String NO_WORKING = null;

    // What a line whose value is not a number the block adds up carries in its place - a status, a
    // rate, a size, or no value at all. Named for the same reason the absences above are, and read by
    // whatever has to stand for lines it could not show: nothing here is a number to sum.
    private static final Integer NO_COUNT = null;

    // What an ordinary line is: one of the things the block lists rather than a note about them, and
    // carrying a number it earned. Both are the plain case and what every factory below builds.
    private static final boolean IS_LISTED_IN_ITS_OWN_RIGHT = false;
    private static final boolean IS_VALUE_EARNED = false;

    /**
     * Rejects a valueless line, and one that neither states a name nor withholds one, at construction,
     * where the caller that composed it is still on the stack. A block lays every line through the same
     * two columns, so a line arriving with nothing for one of them would surface inside a measurement or
     * a draw, well past the point that could say which line was meant. A line carrying no number states
     * {@link CellTooltipRows#NO_SCORE}, which is a value the column collapses for rather than an
     * absence.
     *
     * <p>A name and a redaction of one are refused together for the same reason each is refused alone:
     * the label takes one account of what the line is called, so a line holding both would leave
     * whatever draws it to pick - and the same line would come out named on one surface and blocked out
     * on another.
     *
     * <p>The lengths are copied, so a caller that derived them from a list it goes on using cannot
     * reshape a name the box has already stated.
     */
    public CellTooltipEntryLine {
        Objects.requireNonNull(valueText, "valueText");

        if ((labelText == null) == (redactedWordLengths == null)) {
            throw new IllegalArgumentException("a line states its name or withholds it, never both");
        }
        if (redactedWordLengths != null) {
            redactedWordLengths = List.copyOf(redactedWordLengths);
        }
    }

    /**
     * Builds the plainest listed thing there is: a mark, a name, and a number, calling nothing out.
     * The qualifier is layered on with {@link #qualifiedWith} where a line has one, so a caller states
     * what its line <em>has</em> rather than passing a placeholder for the part it does not use.
     *
     * <p>How the mark is coloured is settled by the mark itself ({@link CellTooltipMark}) rather than
     * layered on afterwards, so a line carrying none cannot state a colouring for it.
     *
     * @param mark      the mark the line opens on and how it is coloured, or null for a line carrying
     *                  none
     * @param labelText what the line is called
     * @param valueText what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for a
     *                  line carrying no number
     * @return the bare line
     */
    public static CellTooltipEntryLine createLine(
            CellTooltipMark mark,
            String labelText,
            String valueText) {

        return new CellTooltipEntryLine(
            mark,
            labelText,
            NO_REDACTION,
            NO_LABEL_FINDING,
            NO_PLACE,
            NO_NOTE,
            NO_QUALIFIER,
            valueText,
            NO_COUNT,
            NO_WORKING,
            IS_LISTED_IN_ITS_OWN_RIGHT,
            IS_VALUE_EARNED);
    }

    /**
     * Builds the same plain line from the number its value states rather than from words for it - for
     * a caller listing something the block's own arithmetic counts, which is most of what a box lists.
     *
     * <p>The number is worded here rather than by the caller, so a line that holds one holds the very
     * number its value shows. That is what lets a listing be stood for when it cannot all be drawn: a
     * row saying how much was left out can only add up lines that carry the figure they state, and a
     * count passed in beside separately-worded text would be free to disagree with it.
     *
     * @param mark         the mark the line opens on and how it is coloured, or null for a line
     *                     carrying none
     * @param labelText    what the line is called
     * @param countedValue what the block counts this line in
     * @return the bare line, stating that number
     */
    public static CellTooltipEntryLine createCountedLine(
            CellTooltipMark mark,
            String labelText,
            int countedValue) {

        return new CellTooltipEntryLine(
            mark,
            labelText,
            NO_REDACTION,
            NO_LABEL_FINDING,
            NO_PLACE,
            NO_NOTE,
            NO_QUALIFIER,
            formatCountedValue(countedValue),
            countedValue,
            NO_WORKING,
            IS_LISTED_IN_ITS_OWN_RIGHT,
            IS_VALUE_EARNED);
    }

    /**
     * Builds a listed thing whose name is withheld: a mark, the shape of the name it is not showing, and
     * a number. Listed rather than left out, so whatever the thing contributed to the block's arithmetic
     * is accounted for on a line of its own instead of surfacing as a difference nothing explains.
     *
     * <p>A separate factory rather than a refinement over a named line, because the name must not reach
     * the line at all: a redaction layered on afterwards would be a line that had held the name and let
     * it go, and every value between would be free to draw it.
     *
     * <p>The lengths are the caller's to derive - it holds the name and this deliberately never does.
     *
     * @param mark                the mark the line opens on and how it is coloured, or null for a line
     *                            carrying none
     * @param redactedWordLengths how many characters each word of the withheld name ran to, in reading
     *                            order
     * @param valueText           what the block counts this line in, or {@link CellTooltipRows#NO_SCORE}
     *                            for a line carrying no number
     * @return the bare line, its name blocked out
     */
    public static CellTooltipEntryLine createRedactedLine(
            CellTooltipMark mark,
            List<Integer> redactedWordLengths,
            String valueText) {

        return new CellTooltipEntryLine(
            mark,
            NO_NAME,
            redactedWordLengths,
            NO_LABEL_FINDING,
            NO_PLACE,
            NO_NOTE,
            NO_QUALIFIER,
            valueText,
            NO_COUNT,
            NO_WORKING,
            IS_LISTED_IN_ITS_OWN_RIGHT,
            IS_VALUE_EARNED);
    }

    /**
     * Builds that same blocked-out line from the number its value states - for a withheld thing the
     * block's arithmetic counts all the same, which is the ordinary case: what is kept back is the
     * name, never the figure beside it.
     *
     * <p>The number is worded here for the reason the named line's is
     * ({@link #createCountedLine}), and the two are worded through one call, so a listing holding
     * both shapes cannot spell one score two ways.
     *
     * @param mark                the mark the line opens on and how it is coloured, or null for a line
     *                            carrying none
     * @param redactedWordLengths how many characters each word of the withheld name ran to, in reading
     *                            order
     * @param countedValue        what the block counts this line in
     * @return the bare line, its name blocked out and that number stated
     */
    public static CellTooltipEntryLine createRedactedCountedLine(
            CellTooltipMark mark,
            List<Integer> redactedWordLengths,
            int countedValue) {

        return new CellTooltipEntryLine(
            mark,
            NO_NAME,
            redactedWordLengths,
            NO_LABEL_FINDING,
            NO_PLACE,
            NO_NOTE,
            NO_QUALIFIER,
            formatCountedValue(countedValue),
            countedValue,
            NO_WORKING,
            IS_LISTED_IN_ITS_OWN_RIGHT,
            IS_VALUE_EARNED);
    }

    /**
     * Words a number the way every counted line in the box words its own, for whatever has to state a
     * figure of its own about lines it is standing in for.
     *
     * <p>Offered beside the factories that spend it so a summing caller cannot arrive at a total
     * spelled unlike the figures it was summed from - which is the one way a stand-in row could read
     * as belonging to a different list from the one it closes.
     *
     * @param countedValue the number to word
     * @return the number as a counted line states it
     */
    public static String formatCountedValue(int countedValue) {
        return KmlibNumbers.formatGroupedInteger(countedValue);
    }

    /**
     * Whether this line opens on an image run - a mark set at the head of its label, before the words -
     * rather than on its name. The one place the absence is judged, so a line the game gives no mark for
     * cannot end up opening on an image run with nothing to load.
     *
     * @return true where the line carries a mark to lead with
     */
    public boolean hasMark() {
        return mark != null;
    }

    /**
     * Whether this line withholds its name and shows the shape of it instead of saying what the thing is
     * called. The one place that reading is judged, so a surface cannot go looking for a name that is
     * not there - and a line saying its name outright cannot be drawn as though something had been kept
     * back.
     *
     * @return true where the line's name is withheld
     */
    public boolean hasRedactedName() {
        return redactedWordLengths != null;
    }

    /**
     * Returns a copy of this line calling one finding out after its name - a status stated on the
     * line it is about rather than on a line of its own, such as why this line outranks a
     * higher-scoring one beneath it.
     *
     * <p>The plainest of the statuses {@link CellTooltipQualifier} composes, and the one nearly every
     * caller wants, so it is stated as the words it says rather than as a value a caller has to build
     * first.
     *
     * @param findingText the status called out after the line's name, unspaced - the line parts it
     *                    from its label when it is laid out
     * @return an otherwise-identical line ending on that status
     */
    public CellTooltipEntryLine qualifiedWith(String findingText) {
        return callsOut(CellTooltipQualifier.stateFinding(findingText));
    }

    /**
     * Returns a copy of this line calling {@code qualifier} out after its name - the same status, for
     * a caller whose finding is introduced by a word or marked by a picture of what it names.
     *
     * @param qualifier the status called out after the line's name
     * @return an otherwise-identical line ending on that status
     */
    public CellTooltipEntryLine callsOut(CellTooltipQualifier qualifier) {
        var parts = new LineParts(this);
        parts.qualifier = qualifier;
        return parts.buildLine();
    }

    /**
     * Returns a copy of this line reading the stretch of its own name between {@code startIndex} and
     * {@code endIndex} as one of the box's findings - a station called <em>Abandoned Station</em>
     * saying in its first word what the line would otherwise have called out after it.
     *
     * <p>Layered on like the {@linkplain #qualifiedWith status after the name} because it is the same
     * finding drawn somewhere else: the word has qualified in every sense, and only where it is laid
     * differs. So a line carrying both states two findings about two different things, and neither
     * displaces the other.
     *
     * <p>A range into the label rather than the words themselves, for the reason
     * {@link CellTooltipLabelFinding} holds one: the name is the only copy of the name, and a stretch
     * free to carry its own text is a stretch free to disagree with it.
     *
     * <p>Refused outright on a line whose name is {@linkplain #hasRedactedName withheld}. There is no
     * name to pick a stretch out of, and the blocks drawn in its place stand for words rather than
     * spelling them - so a range into one could only be gilding whatever happened to be that far along.
     *
     * @param startIndex where the finding begins in the label, counted in characters from its start
     * @param endIndex   the character position just past the finding's last
     * @return an otherwise-identical line reading that stretch of its name as a finding
     */
    public CellTooltipEntryLine callsOutInLabel(int startIndex, int endIndex) {

        if (hasRedactedName()) {
            throw new IllegalStateException("a withheld name has no stretch to call out");
        }
        if (endIndex > labelText.length()) {
            throw new IllegalArgumentException("endIndex must not run past the label");
        }
        var parts = new LineParts(this);

        parts.labelFinding = new CellTooltipLabelFinding(startIndex, endIndex);

        return parts.buildLine();
    }

    /**
     * Returns a copy of this line remarking {@code noteText} at its end - something about the thing on
     * the line that is not a finding about it, such as how current what the box says about it is.
     *
     * <p>Read in the quiet shade rather than the qualifier's gold, on the same reasoning a value's
     * working is: the box parts what it has found from what it is saying about its own account,
     * and a note is the second. A reader scanning for findings should pass over it, and a reader
     * asking how much to trust the line should find it exactly where the line is.
     *
     * <p>Laid behind the qualifier for the same parting: the findings a line carries are about the
     * thing on it, so they stay run on after its name, and the box's aside about its own account
     * closes the line rather than splitting them from the name they qualify.
     *
     * @param noteText the remark, unspaced - the line parts it from what precedes it when it is laid
     *                 out
     * @return an otherwise-identical line carrying that remark
     */
    public CellTooltipEntryLine notedWith(String noteText) {
        var parts = new LineParts(this);
        parts.noteText = noteText;
        return parts.buildLine();
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

        var parts = new LineParts(this);
        parts.indexPlace = new CellTooltipIndexPlace(indexText, indexOutcome);
        return parts.buildLine();
    }

    /**
     * Returns a copy of this line read as a note about the list rather than one of the things in it -
     * the arithmetic of a term its members share, say, stated once beneath them.
     *
     * <p>The box already parts a finding from the arithmetic behind it inside a value, in the shade
     * each is drawn in. A line that is arithmetic all the way to its name says the same thing about
     * itself, so it takes that quiet shade throughout and only the number it arrives at stays a
     * finding. Left as a fact about the line rather than a colour, so which shade means what is
     * settled in one place for every line that reads quietly.
     *
     * @return an otherwise-identical line read as a note about the list rather than a member of it
     */
    public CellTooltipEntryLine readsAsAside() {
        var parts = new LineParts(this);
        parts.isAside = true;
        return parts.buildLine();
    }

    /**
     * Returns a copy of this line whose number is one nothing earned - what an account recorded for
     * the thing on the line rather than anything it did.
     *
     * <p>The narrower of the two quiet readings, and the difference is the point: unlike an
     * {@linkplain #readsAsAside aside}, this line <em>is</em> one of the things the list holds, so it
     * is named as loudly as its neighbours and only its number quietens. A thing an account passed
     * over carries a nought that is the account's statement about it and not its own - drawn as
     * loudly as the numbers around it, that nought reads as a figure it competed with and lost on,
     * inviting exactly the comparison it cannot bear.
     *
     * @return an otherwise-identical line whose number reads as one nothing earned
     */
    public CellTooltipEntryLine statesUncountedValue() {
        var parts = new LineParts(this);
        parts.isValueUncounted = true;
        return parts.buildLine();
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
        var parts = new LineParts(this);
        parts.valueWorkingText = valueWorkingText;
        return parts.buildLine();
    }

    /**
     * One line's parts, held apart so a refinement above can restate the single part it is about.
     *
     * <p>The components are enumerated four times in this file and nowhere beyond it - the record
     * header, these fields, the constructor reading a line into them, and the one building a line
     * back out. That cost is fixed: it does not grow with the refinements above, each of which
     * states only the part it is about. Spelled out per refinement instead, a part added to a line
     * would have to be threaded through all seven, and the refinement that got missed would not fail
     * to compile - it would silently drop the new part from any line it was applied to, surfacing as
     * a mark or a qualifier that vanishes when some unrelated status is layered on afterwards.
     *
     * <p>Mutable and private, which a value this package hands out could not be: it lives for the
     * three statements of one refinement and is never reachable from a built line.
     */
    private static final class LineParts {

        private CellTooltipMark mark;
        private String labelText;
        private List<Integer> redactedWordLengths;
        private CellTooltipLabelFinding labelFinding;
        private CellTooltipIndexPlace indexPlace;
        private String noteText;
        private CellTooltipQualifier qualifier;
        private String valueText;
        private Integer countedValue;
        private String valueWorkingText;
        private boolean isAside;
        private boolean isValueUncounted;

        private LineParts(CellTooltipEntryLine line) {
            mark = line.mark();
            labelText = line.labelText();
            redactedWordLengths = line.redactedWordLengths();
            labelFinding = line.labelFinding();
            indexPlace = line.indexPlace();
            noteText = line.noteText();
            qualifier = line.qualifier();
            valueText = line.valueText();
            countedValue = line.countedValue();
            valueWorkingText = line.valueWorkingText();
            isAside = line.isAside();
            isValueUncounted = line.isValueUncounted();
        }

        private CellTooltipEntryLine buildLine() {
            return new CellTooltipEntryLine(
                mark,
                labelText,
                redactedWordLengths,
                labelFinding,
                indexPlace,
                noteText,
                qualifier,
                valueText,
                countedValue,
                valueWorkingText,
                isAside,
                isValueUncounted);
        }
    }
}
