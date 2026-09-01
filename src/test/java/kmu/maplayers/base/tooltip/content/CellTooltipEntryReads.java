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
     * What a line whose name is withheld reads as out of {@link #readLabelTexts}. Named rather than
     * asserted as a bare null, so a case pinning the order of a listing says the line is there and
     * unnamed rather than appearing to have lost one - and named here, beside the read that produces
     * the absence, so every suite meeting one spells it the same way.
     */
    public static final String NO_NAME_STATED = null;

    private CellTooltipEntryReads() {
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
            .map(entry -> entry.line().labelText())
            .toList();
    }
}
