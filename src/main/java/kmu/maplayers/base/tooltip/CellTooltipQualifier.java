package kmu.maplayers.base.tooltip;

import kmlib.text.KmlibStrings;

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
 * line, with nothing around it - draws as the one gold run it has always drawn as.
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
 * @param trailingWordText the word closing the status, saying what kind of thing the finding names,
 *                         or null where the finding says that itself. Quiet for the same reason the
 *                         leading word is: what kind of thing it is, is the box's word and not the
 *                         name it found
 */
public record CellTooltipQualifier(
    String leadingWordText,
    CellTooltipMark mark,
    String findingText,
    String trailingWordText) {

    // What a qualifier carries where no word stands either side of its finding. Named rather than
    // passed as bare nulls, so the factories below say what a case has none of instead of handing
    // the constructor unexplained absences.
    private static final String NO_LEADING_WORD = null;
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

        return new CellTooltipQualifier(leadingWordText, mark, findingText, trailingWordText);
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
