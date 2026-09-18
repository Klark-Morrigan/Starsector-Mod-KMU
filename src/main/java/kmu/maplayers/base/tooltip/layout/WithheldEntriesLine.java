package kmu.maplayers.base.tooltip.layout;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.Optional;

/**
 * The line that stands in for the end of a listing the box had no room to draw: how many of its
 * entries were left out, and what they came to between them.
 *
 * <p>The last thing a box gives up. It has already been compressed as far as its typography goes
 * ({@code TooltipHeightFit}) and is still taller than the screen, so something has to go unsaid - and
 * what must not happen is that it goes unsaid <em>silently</em>. A listing quietly cut short reads as
 * a complete listing, and the reader adds up rows that no longer account for the number at the top of
 * them; the map would then be painting a system by a figure its own box appears to contradict.
 *
 * <p>So the row says both things a reader needs to reconstruct the list: how many entries stand behind
 * it, and their total. The total is summed off the very lines that were dropped
 * ({@link CellTooltipEntryLine#countedValue}) and worded through the call those lines worded their own
 * numbers with, so the stand-in reads as one of the list's own members rather than as a figure arrived
 * at some other way.
 *
 * <p>Only what the block actually counted is summed. A number an account recorded rather than earned -
 * the nought of a colony nothing was worked out for - is no part of any total, so a run of those is
 * stood for by its count alone rather than by a nought that would read as a sum somebody made.
 *
 * <p>Read as an aside, because that is what it is: a statement about the listing rather than one of the
 * things listed in it. Its name is drawn in the quiet shade the box states its own arithmetic in, and
 * only the figure it arrives at stays a finding.
 */
final class WithheldEntriesLine {

    // What a listing has withheld nothing of, and what the sum of nothing counted comes to. Named so
    // the walk below reads as counting rather than as comparing against bare zeroes.
    private static final int NOTHING_COUNTED = 0;

    private WithheldEntriesLine() {
    }

    /**
     * Builds the line standing for {@code withheldEntries} - the tail of a listing the box could not
     * draw - saying how many they are and what they counted for between them.
     *
     * <p>Only the entries' own lines are summed, never what hangs beneath them: a listed thing's number
     * is already the sum of its account, so adding both would state the same weight twice.
     *
     * @param withheldEntries the entries left undrawn, in the order they would have read; never empty
     * @return the line standing in for them
     */
    static CellTooltipEntryLine buildLine(List<CellTooltipEntry> withheldEntries) {

        var labelText = KmuStringKeys.format(
            KmuStringKeys.MAP_LAYER_TOOLTIP_WITHHELD_ENTRIES,
            withheldEntries.size());

        return sumCountedValues(withheldEntries)
            .map(countedValue -> CellTooltipEntryLine.createCountedLine(
                CellTooltipMark.NO_MARK,
                labelText,
                countedValue))
            .orElseGet(() -> CellTooltipEntryLine.createLine(
                CellTooltipMark.NO_MARK,
                labelText,
                CellTooltipEntryLine.NO_SCORE))
            .readsAsAside();
    }

    // What the withheld entries counted for between them, and nothing at all where none of them
    // carried a number the block counts - a run of statuses, of rates, or of noughts an account
    // recorded rather than anything earned. Empty rather than nought, since a total nobody summed and
    // a total that came to nought read alike on screen and mean opposite things.
    private static Optional<Integer> sumCountedValues(List<CellTooltipEntry> withheldEntries) {

        var countedTotal = NOTHING_COUNTED;
        var hasCountedEntry = false;

        for (var entry : withheldEntries) {
            var line = entry.line();

            if (line.countedValue() == null || line.isValueUncounted()) {
                continue;
            }
            countedTotal += line.countedValue();
            hasCountedEntry = true;
        }
        return hasCountedEntry
            ? Optional.of(countedTotal)
            : Optional.empty();
    }
}
