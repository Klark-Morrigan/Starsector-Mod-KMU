package kmu.maplayers.base.tooltip.content;

import kmlib.text.KmlibStrings;

import java.awt.Color;
import java.util.Objects;

/**
 * The status a listed line calls out after its name: what the box found about the thing on the line,
 * optionally introduced by a word of the box's own, marked by a picture of what that finding names,
 * and closed by a word saying what kind of thing it is.
 *
 * <p>One value rather than parts layered onto a line, because they are one statement. A word, a mark
 * and a name added separately could each be applied without the others, which leaves a line free to
 * end on a connective introducing nothing, or on a picture of something it never names - fragments
 * of an attribution no reader could complete.
 *
 * <p>Only the finding reads in the highlight the box reserves for what it has worked out; the words
 * around it and the mark beside it are quiet. So the ordinary qualifier - a single word about the
 * line, with nothing around it - draws as a single gold run. The one departure is a finding whose
 * colour is itself what the box found - a relation level, a hazard band, a signal strength - which
 * carries that colour here ({@link #drawsFindingIn}), a fixed gold run stating the fact and
 * contradicting it in the same line. Held on the qualifier rather than on the line, so a line calling
 * nothing out has nowhere to state how a finding would have been coloured.
 *
 * <p>Unspaced throughout: what parts each part from the next is the run vocabulary's own word space,
 * so nothing here carries a separator.
 *
 * @param leadingWordText  the word introducing the finding, or null where the finding stands alone.
 *                         Quiet, being the box's own connective rather than anything it found
 * @param mark             the picture of what the finding names, drawn between that word and the
 *                         finding itself, or null where the finding names nothing there is a picture
 *                         of
 * @param findingText      what the box found about the thing on the line
 * @param findingColour    the colour the finding is drawn in where that colour is itself part of what
 *                         the box found, or null where it reads in the box's highlight like every
 *                         other finding
 * @param trailingWordText the word closing the status, saying what kind of thing the finding names,
 *                         or null where the finding says that itself. Quiet for the same reason the
 *                         leading word is: what kind of thing it is, is the box's word and not the
 *                         name it found
 */
public record CellTooltipQualifier(
    String leadingWordText,
    CellTooltipMark mark,
    String findingText,
    Color findingColour,
    String trailingWordText) {

    // What a qualifier carries where no word stands either side of its finding, and where its finding
    // reads in the box's own highlight. Named rather than passed as bare nulls, so the factories below
    // say what a case has none of instead of handing the constructor unexplained absences.
    private static final String NO_LEADING_WORD = null;
    private static final Color NO_OWN_COLOUR = null;
    private static final String NO_TRAILING_WORD = null;

    /**
     * Rejects a finding with no words in it, at construction, where the caller that composed it is
     * still on the stack. A qualifier exists to state one, so a blank one would leave a line ending
     * on a word and a picture introducing nothing - while a line calling nothing out states that by
     * carrying no qualifier at all.
     */
    public CellTooltipQualifier {

        if (!KmlibStrings.hasText(findingText)) {
            throw new IllegalArgumentException("a qualifier states a finding");
        }
    }

    /**
     * Builds the plainest status there is: one finding about the line, drawn in the box's highlight
     * with nothing around it.
     *
     * @param findingText what the box found about the thing on the line
     * @return the qualifier
     */
    public static CellTooltipQualifier stateFinding(String findingText) {

        return new CellTooltipQualifier(
            NO_LEADING_WORD,
            CellTooltipMark.NO_MARK,
            findingText,
            NO_OWN_COLOUR,
            NO_TRAILING_WORD);
    }

    /**
     * Builds a status introduced by a word of the box's own and marked by a picture of what it
     * names - what a line stating the thing it belongs to carries, where the finding is that thing's
     * own name and says for itself what kind of thing it is.
     *
     * @param leadingWordText the word introducing the finding
     * @param mark            the picture of what the finding names, or null where there is none
     * @param findingText     what the box found about the thing on the line
     * @return the qualifier
     */
    public static CellTooltipQualifier introduceFinding(
            String leadingWordText,
            CellTooltipMark mark,
            String findingText) {

        return new CellTooltipQualifier(
            leadingWordText,
            mark,
            findingText,
            NO_OWN_COLOUR,
            NO_TRAILING_WORD);
    }

    /**
     * Builds that same status closed by a word saying what kind of thing the finding names - for a
     * finding that cannot say so itself, a name stood in for by its initials being the case.
     *
     * @param leadingWordText  the word introducing the finding
     * @param mark             the picture of what the finding names, or null where there is none
     * @param findingText      what the box found about the thing on the line
     * @param trailingWordText the word saying what kind of thing that is
     * @return the qualifier
     */
    public static CellTooltipQualifier encloseFinding(
            String leadingWordText,
            CellTooltipMark mark,
            String findingText,
            String trailingWordText) {

        return new CellTooltipQualifier(
            leadingWordText,
            mark,
            findingText,
            NO_OWN_COLOUR,
            trailingWordText);
    }

    /**
     * Returns a copy of this status drawing its finding in {@code findingColour} rather than in the
     * box's highlight - for a finding whose colour is part of what the box found, such as a relation
     * level read in the shade the map paints that relation.
     *
     * <p>Layered on rather than taken by each factory, because it is one exception over any shape of
     * status: a plain finding, an introduced one or an enclosed one may each be a finding whose colour
     * is the fact.
     *
     * @param findingColour the colour the finding is drawn in, never null
     * @return an otherwise-identical status whose finding reads in that colour
     */
    public CellTooltipQualifier drawsFindingIn(Color findingColour) {
        Objects.requireNonNull(findingColour, "findingColour");

        return new CellTooltipQualifier(
            leadingWordText,
            mark,
            findingText,
            findingColour,
            trailingWordText);
    }

    /**
     * Whether the finding carries a colour of its own rather than reading in the box's highlight.
     * Judged here like the other absences, so the one place the default is chosen is the one place
     * that asks.
     *
     * @return true where the finding is drawn in a colour the status states
     */
    public boolean hasOwnFindingColour() {
        return findingColour != null;
    }

    /**
     * Whether the finding is introduced by a word of the box's own rather than standing alone. The
     * one place that absence is judged, so a qualifier with nothing to introduce it cannot end up
     * opening on a run that draws nothing.
     *
     * @return true where a word precedes the finding
     */
    public boolean hasLeadingWord() {
        return KmlibStrings.hasText(leadingWordText);
    }

    /**
     * Whether the finding is marked by a picture of what it names. Judged here for the same reason
     * the leading word is, so a qualifier the game gave no texture for draws no image run.
     *
     * @return true where the qualifier carries a mark
     */
    public boolean hasMark() {
        return mark != null;
    }

    /**
     * Whether a word closes the status, saying what kind of thing the finding names. Judged here for
     * the same reason the other two absences are.
     *
     * @return true where a word follows the finding
     */
    public boolean hasTrailingWord() {
        return KmlibStrings.hasText(trailingWordText);
    }
}
