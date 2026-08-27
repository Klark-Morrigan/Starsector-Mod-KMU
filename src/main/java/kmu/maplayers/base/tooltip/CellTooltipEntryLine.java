package kmu.maplayers.base.tooltip;

import java.util.Objects;

/**
 * One thing a cell-tooltip block lists: what it is called, the mark it is shown by, whatever the block
 * counts it in, any working that number came out of, and any status it calls out - after its name, or
 * inside the name where the name already says it. What is listed - and nothing whatever about how it
 * is laid.
 *
 * <p>Held as a value rather than as a built line because the two decisions belong on opposite sides of
 * the box. A layer knows what its block lists; the block ({@link CellTooltipSections}) knows the tier,
 * the colours, and the value column a listed thing is laid in. So two layers listing
 * unrelated content still list it alike, and a body cannot quietly author a third look by reaching for
 * the line vocabulary itself.
 *
 * <p>Nothing here is faction-shaped. The mark is a texture path a caller may simply not have, so a list
 * of things that carry none - industries, conditions, hazards - is this same shape with a null in it.
 *
 * @param mark             the mark the line opens on and how it is coloured, or null for a line
 *                         carrying none. One value rather than a path beside a colouring, so a line
 *                         showing no mark has nowhere to state how one would have been drawn
 * @param labelText        what the line is called
 * @param labelFinding     the stretch of the name that is itself one of the box's findings, or null
 *                         where the name says none - a line calls at most one out inside its own name
 * @param indexPlace       where the line falls in the ordering it belongs to and what that place
 *                         decided, run on after its name, or null where the line has no place worth
 *                         stating
 * @param noteText         a remark about the thing on the line that is not a finding about it -
 *                         how current what the box says about it is, say - run on at the end of the
 *                         line, or null where the line makes none
 * @param qualifierText    the status called out after the line's name, or null when it states none
 * @param valueText        what the block counts this line in, or {@link CellTooltipRows#NO_SCORE} for a
 *                         line carrying no number
 * @param valueWorkingText the arithmetic the number came out of, stated before it, or null where the
 *                         line shows its number alone
 * @param isAside          whether the line is a note about the list rather than one of the things in
 *                         it - the arithmetic of a term its members share, say. Such a line is drawn
 *                         quiet down to its name, and only the number it arrives at stays a finding
 * @param isValueUncounted whether the line's number is one nothing earned - what an account recorded
 *                         for the thing on the line rather than anything it did. Only the number
 *                         quietens: the line is one of the things the list holds and is named as
 *                         loudly as its neighbours
 */
public record CellTooltipEntryLine(
    CellTooltipMark mark,
    String labelText,
    CellTooltipLabelFinding labelFinding,
    CellTooltipIndexPlace indexPlace,
    String noteText,
    String qualifierText,
    String valueText,
    String valueWorkingText,
    boolean isAside,
    boolean isValueUncounted) {

    // What the plainest line carries in each of the parts it does not use. Named one per part rather
    // than passed as bare nulls, so the factory below says what the line has none of instead of
    // handing the constructor a row of unexplained absences a reader has to count off against the
    // components - which is a count that goes wrong the moment a part is added.
    private static final CellTooltipLabelFinding NO_LABEL_FINDING = null;
    private static final CellTooltipIndexPlace NO_PLACE = null;
    private static final String NO_NOTE = null;
    private static final String NO_QUALIFIER = null;
    private static final String NO_WORKING = null;

    // What an ordinary line is: one of the things the block lists rather than a note about them, and
    // carrying a number it earned. Both are the plain case and what every factory below builds.
    private static final boolean IS_LISTED_IN_ITS_OWN_RIGHT = false;
    private static final boolean IS_VALUE_EARNED = false;

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
            NO_LABEL_FINDING,
            NO_PLACE,
            NO_NOTE,
            NO_QUALIFIER,
            valueText,
            NO_WORKING,
            IS_LISTED_IN_ITS_OWN_RIGHT,
            IS_VALUE_EARNED);
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
     * Returns a copy of this line calling {@code qualifierText} out after its name - a status stated on
     * the line it is about rather than on a line of its own, such as why this line outranks a
     * higher-scoring one beneath it.
     *
     * @param qualifierText the status called out after the line's name, unspaced - the line parts it
     *                      from its label when it is laid out
     * @return an otherwise-identical line ending on that status
     */
    public CellTooltipEntryLine qualifiedWith(String qualifierText) {
        var parts = new LineParts(this);
        parts.qualifierText = qualifierText;
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
     * @param startIndex where the finding begins in the label, counted in characters from its start
     * @param endIndex   the character position just past the finding's last
     * @return an otherwise-identical line reading that stretch of its name as a finding
     */
    public CellTooltipEntryLine callsOutInLabel(int startIndex, int endIndex) {

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
     * <p>The components are enumerated here and in the record header, and nowhere else. Spelled out
     * once per refinement instead, a part added to a line has to be threaded through every one of
     * them, and the refinement that gets missed does not fail to compile - it silently drops the new
     * part from any line it is applied to, which surfaces as a mark or a qualifier that vanishes when
     * some unrelated status is layered on afterwards.
     *
     * <p>Mutable and private, which a value this package hands out could not be: it lives for the
     * three statements of one refinement and is never reachable from a built line.
     */
    private static final class LineParts {

        private CellTooltipMark mark;
        private String labelText;
        private CellTooltipLabelFinding labelFinding;
        private CellTooltipIndexPlace indexPlace;
        private String noteText;
        private String qualifierText;
        private String valueText;
        private String valueWorkingText;
        private boolean isAside;
        private boolean isValueUncounted;

        private LineParts(CellTooltipEntryLine line) {
            mark = line.mark();
            labelText = line.labelText();
            labelFinding = line.labelFinding();
            indexPlace = line.indexPlace();
            noteText = line.noteText();
            qualifierText = line.qualifierText();
            valueText = line.valueText();
            valueWorkingText = line.valueWorkingText();
            isAside = line.isAside();
            isValueUncounted = line.isValueUncounted();
        }

        private CellTooltipEntryLine buildLine() {
            return new CellTooltipEntryLine(
                mark,
                labelText,
                labelFinding,
                indexPlace,
                noteText,
                qualifierText,
                valueText,
                valueWorkingText,
                isAside,
                isValueUncounted);
        }
    }
}
