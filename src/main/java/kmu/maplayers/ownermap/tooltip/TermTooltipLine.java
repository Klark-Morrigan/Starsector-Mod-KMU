package kmu.maplayers.ownermap.tooltip;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;

/**
 * One term of the number a listed thing carries: named, uncrested, and stating what it contributed.
 *
 * <p>The shape every line beneath a listed colony or market takes, whichever account is being broken
 * down. Shared rather than spelled per account because it is the same line: two accounts each
 * composing their own would be free to drift in what a term looks like, and a reader comparing one
 * box against the next has no way to tell a difference that means something from one that does not.
 *
 * <p>Uncrested throughout - a term is arithmetic rather than a thing in the sector, so there is
 * nothing for a mark to picture. What is left open is the entry against the line, since an account
 * that goes on to say more - a rate, a working, a qualifier - refines the line rather than composing
 * a second spelling of it.
 */
public final class TermTooltipLine {

    private TermTooltipLine() {
    }

    /**
     * Builds a term that breaks down no further, which is most of them.
     *
     * @param labelText what the term is called
     * @param valueText what it contributed, already worded
     * @return the entry, ready to hang beneath the line it explains
     */
    public static CellTooltipEntry buildTermEntry(String labelText, String valueText) {

        return CellTooltipEntry.createEntry(buildTermLine(labelText, valueText));
    }

    /**
     * Builds the line alone, for a term that has something further to say about itself - the working
     * a rate came out of, or a tier's own count.
     *
     * @param labelText what the term is called
     * @param valueText what it contributed, already worded
     * @return the line, ready to be refined and then listed
     */
    public static CellTooltipEntryLine buildTermLine(String labelText, String valueText) {

        return CellTooltipEntryLine.createLine(CellTooltipMark.NO_MARK, labelText, valueText);
    }
}
