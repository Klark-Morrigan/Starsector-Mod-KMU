package kmu.maplayers.base.tooltip.content;

import java.util.List;

/**
 * How a test reads a listing of {@linkplain CellTooltipEntry entries} - what it states, line by line,
 * before any of it becomes rows.
 *
 * <p>Shared for the same reason {@link CellTooltipRowReads} is: every resolver that answers a listing
 * is asserted on the same way, and each suite reaching through the entry to its line and out to its
 * text is a walk restated per class rather than a read named once.
 */
public final class CellTooltipEntryReads {

    /**
     * What a line whose name is withheld reads as out of {@link #readLabelText}. Named rather than
     * asserted as a bare null, so a case pinning the order of a listing says the line is there and
     * unnamed rather than appearing to have lost one - and named here, beside the read that produces
     * the absence, so every suite meeting one spells it the same way.
     */
    public static final String NO_NAME_STATED = null;

    /**
     * What a line whose name calls none of the box's findings out reads as out of
     * {@link #readLabelFinding}, a withheld name included.
     */
    public static final CellTooltipLabelFinding NO_LABEL_FINDING = null;

    private CellTooltipEntryReads() {
    }

    /**
     * Reads the stretch of one line's name that is itself a finding, through the name's own fold.
     *
     * @param line the line
     * @return the stretch, or {@link #NO_LABEL_FINDING} where the name says none or is withheld
     */
    public static CellTooltipLabelFinding readLabelFinding(CellTooltipEntryLine line) {

        return line.name().selectByCase(
            CellTooltipLineName.Spoken::labelFinding,
            withheldName -> NO_LABEL_FINDING);
    }

    /**
     * Reads what one line is called, through the name's own fold.
     *
     * @param line the line
     * @return the line's label text, or {@link #NO_NAME_STATED} where its name is withheld
     */
    public static String readLabelText(CellTooltipEntryLine line) {

        return line.name().selectByCase(
            CellTooltipLineName.Spoken::labelText,
            withheldName -> NO_NAME_STATED);
    }

    /**
     * Reads what a listing states, in the order it is read. What a line counts in is left out: a case
     * about which things were listed and in what order says so without also pinning their numbers,
     * which belong to whatever read produced them.
     *
     * @param entries the listing, in the order it is read
     * @return each entry's label text, in that order
     */
    public static List<String> readLabelTexts(List<CellTooltipEntry> entries) {

        return entries
            .stream()
            .map(entry -> readLabelText(entry.line()))
            .toList();
    }
}
