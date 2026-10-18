package kmu.maplayers.base.tooltip.content;

import kmlib.starsector.entities.EntityNameplate;
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
 * the box. A layer knows what its block lists; the laying-out ({@code kmu.maplayers.base.tooltip.layout})
 * knows the tier, the colours, and the value column a listed thing is laid in - bar the one colour a
 * status states because the colour is itself its finding ({@link CellTooltipQualifier#drawsFindingIn}).
 * So two layers listing unrelated content still list it alike, and a body cannot quietly author a third
 * look by reaching for the line vocabulary itself.
 *
 * <p>Nothing here is faction-shaped. The mark is a texture path a caller may simply not have, so a list
 * of things that carry none - industries, conditions, hazards - is this same shape with a null in it.
 *
 * @param mark       the mark the line opens on and how it is coloured, or null for a line carrying none.
 *                   One value rather than a path beside a colouring, so a line showing no mark has
 *                   nowhere to state how one would have been drawn
 * @param name       what the line is called, or the shape of a name the box withholds - one or the
 *                   other, never both and never neither
 * @param indexPlace where the line falls in the ordering it belongs to and what that place decided, run
 *                   on after its name, or null where the line has no place worth stating
 * @param noteText   a remark about the thing on the line that is not a finding about it - how current
 *                   what the box says about it is, say - run on at the end of the line, or null where
 *                   the line makes none
 * @param qualifier  the status called out after the line's name, or null when it states none
 * @param value      what the block counts this line in, and how that number reads
 * @param isAside    whether the line is a note about the list rather than one of the things in it - the
 *                   arithmetic of a term its members share, say. Such a line is drawn quiet down to its
 *                   name, and only the number it arrives at stays a finding
 */
public record CellTooltipEntryLine(
    CellTooltipMark mark,
    CellTooltipLineName name,
    CellTooltipIndexPlace indexPlace,
    String noteText,
    CellTooltipQualifier qualifier,
    CellTooltipLineValue value,
    boolean isAside) {

    /**
     * What a line counted in nothing states in place of a number - a status line, a heading, a row
     * whose figure the box is withholding. Rendered as-is it draws nothing and measures zero width, so
     * the value column collapses for that line rather than reserving room for an absence.
     *
     * <p>A value rather than a null because the line takes one account of what it is counted in: a
     * null would reach a measurement or a draw as an absence nothing can lay out, well past the point
     * that could say which line was meant.
     */
    public static final String NO_SCORE = "";

    // What a line carries in each of the parts it does not use. Named one per part rather than passed
    // as bare nulls, so the factories below say what a line has none of instead of handing the
    // constructor a row of unexplained absences a reader has to count off against the components -
    // which is a count that goes wrong the moment a part is added.
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

    // What a number nothing earned is marked as once statesUncountedValue is laid on a line.
    private static final boolean IS_VALUE_UNCOUNTED = true;

    /**
     * Rejects a line missing its name or its value at construction, where the caller that composed it
     * is still on the stack. A block lays every line through the same two columns, so a line arriving
     * with nothing for one of them would surface inside a measurement or a draw, well past the point
     * that could say which line was meant.
     */
    public CellTooltipEntryLine {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
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
     * @param valueText what the block counts this line in, or {@link #NO_SCORE} for a
     *                  line carrying no number
     * @return the bare line
     */
    public static CellTooltipEntryLine createLine(
            CellTooltipMark mark,
            String labelText,
            String valueText) {

        return createBareLine(
            mark,
            CellTooltipLineName.createSpokenName(labelText),
            createWordedValue(valueText));
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

        return createBareLine(
            mark,
            CellTooltipLineName.createSpokenName(labelText),
            createCountedValue(countedValue));
    }

    /**
     * Builds a counted line naming something the sector map draws, led by the glyph the map marks it
     * with.
     *
     * <p>Takes the entity's nameplate whole rather than its name and glyph apart, so the glyph can only
     * have come from the entity the line names - and every box marks a thing on the map by the one
     * rule.
     *
     * @param entity       the entity the line names
     * @param countedValue what the block counts this line in
     * @return the bare line, stating that number
     */
    public static CellTooltipEntryLine createCountedEntityLine(EntityNameplate entity, int countedValue) {

        return createCountedLine(
            CellTooltipMark.resolveMarkForMapIcon(entity.mapIcon()),
            entity.displayName(),
            countedValue);
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
     * @param valueText           what the block counts this line in, or {@link #NO_SCORE}
     *                            for a line carrying no number
     * @return the bare line, its name blocked out
     */
    public static CellTooltipEntryLine createRedactedLine(
            CellTooltipMark mark,
            List<Integer> redactedWordLengths,
            String valueText) {

        return createBareLine(
            mark,
            new CellTooltipLineName.Withheld(redactedWordLengths),
            createWordedValue(valueText));
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

        return createBareLine(
            mark,
            new CellTooltipLineName.Withheld(redactedWordLengths),
            createCountedValue(countedValue));
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
     * a caller whose finding is introduced by a word, marked by a picture of what it names, or drawn
     * in a colour that is itself the fact.
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
     * <p>Refused outright on a line whose name is {@linkplain CellTooltipLineName.Withheld withheld}.
     * There is no name to pick a stretch out of, and the blocks drawn in its place stand for words
     * rather than spelling them - so a range into one could only be gilding whatever happened to be that
     * far along. The refusal stays a runtime one because the line is one type whichever name it carries:
     * a caller that read the name through {@link CellTooltipLineName#selectByCase} knows which it has.
     *
     * @param startIndex where the finding begins in the label, counted in characters from its start
     * @param endIndex   the character position just past the finding's last
     * @return an otherwise-identical line reading that stretch of its name as a finding
     */
    public CellTooltipEntryLine callsOutInLabel(int startIndex, int endIndex) {

        var labelFinding = new CellTooltipLabelFinding(startIndex, endIndex);
        var parts = new LineParts(this);

        parts.name = name.selectByCase(
            spokenName -> new CellTooltipLineName.Spoken(spokenName.labelText(), labelFinding),
            withheldName -> {
                throw new IllegalStateException("a withheld name has no stretch to call out");
            });

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

        parts.value = new CellTooltipLineValue(
            value.valueText(),
            value.countedValue(),
            value.valueWorkingText(),
            IS_VALUE_UNCOUNTED);

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

        parts.value = new CellTooltipLineValue(
            value.valueText(),
            value.countedValue(),
            valueWorkingText,
            value.isValueUncounted());

        return parts.buildLine();
    }

    // The plainest line holding a name and a value: no place, no note, no status, and one of the
    // things the block lists. What every factory above builds before any refinement is laid on it.
    private static CellTooltipEntryLine createBareLine(
            CellTooltipMark mark,
            CellTooltipLineName name,
            CellTooltipLineValue value) {

        return new CellTooltipEntryLine(
            mark,
            name,
            NO_PLACE,
            NO_NOTE,
            NO_QUALIFIER,
            value,
            IS_LISTED_IN_ITS_OWN_RIGHT);
    }

    // A value handed over already worded, which the block's arithmetic does not add up.
    private static CellTooltipLineValue createWordedValue(String valueText) {
        return new CellTooltipLineValue(valueText, NO_COUNT, NO_WORKING, IS_VALUE_EARNED);
    }

    // A value worded here from the number it states, so the line holds the very number it shows.
    private static CellTooltipLineValue createCountedValue(int countedValue) {

        return new CellTooltipLineValue(
            formatCountedValue(countedValue),
            countedValue,
            NO_WORKING,
            IS_VALUE_EARNED);
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
        private CellTooltipLineName name;
        private CellTooltipIndexPlace indexPlace;
        private String noteText;
        private CellTooltipQualifier qualifier;
        private CellTooltipLineValue value;
        private boolean isAside;

        private LineParts(CellTooltipEntryLine line) {

            mark = line.mark();
            name = line.name();
            indexPlace = line.indexPlace();
            noteText = line.noteText();
            qualifier = line.qualifier();
            value = line.value();
            isAside = line.isAside();
        }

        private CellTooltipEntryLine buildLine() {

            return new CellTooltipEntryLine(
                mark,
                name,
                indexPlace,
                noteText,
                qualifier,
                value,
                isAside);
        }
    }
}
