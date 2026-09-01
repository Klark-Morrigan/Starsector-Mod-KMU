package kmu.maplayers.base.tooltip.layout;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the row standing for a cut-short listing says: how many entries it stands for, and what
 * those came to between them.
 *
 * <p>Both halves are what stop a cut listing reading as a complete one. The count says a reader is
 * looking at part of a list; the total says how much of the number above it the visible rows fail to
 * account for. A row stating one without the other leaves the reader adding up rows that cannot reach
 * the figure the map painted the system by.
 */
final class WithheldEntriesLineTest {

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildLine {

        @Test
        void buildLineNamesHowManyEntriesItStandsFor() {

            var line = WithheldEntriesLine.buildLine(List.of(
                createCountedEntry("Chicomoztoc", 4000),
                createCountedEntry("Kazeron", 320)));

            assertThat(line.labelText())
                .isEqualTo("+ 2 more");
        }

        @Test
        void buildLineStatesWhatTheEntriesItStandsForCameTo() {
            // The figure the visible rows would otherwise leave unaccounted for. Summed off the very
            // lines that were dropped, so the row closes the arithmetic the list opened.
            var line = WithheldEntriesLine.buildLine(List.of(
                createCountedEntry("Chicomoztoc", 4000),
                createCountedEntry("Kazeron", 320)));

            assertThat(line.countedValue())
                .isEqualTo(4320);
            assertThat(line.valueText())
                .isEqualTo("4,320");
        }

        @Test
        void buildLineCountsOnlyTheEntriesLineAndNotWhatHangsBeneathIt() {
            // A listed thing's number is already the sum of its own account, so counting both would
            // state the same weight twice - and the row would report more withheld than the list holds.
            var line = WithheldEntriesLine.buildLine(List.of(
                createCountedEntry("Chicomoztoc", 4000)
                    .nesting(List.of(createCountedEntry("Size", 3000)))));

            assertThat(line.countedValue())
                .isEqualTo(4000);
        }

        @Test
        void buildLineStatesNoNumberWhereNothingItStandsForCarriedOne() {
            // A run of statuses or rates adds up to nothing anybody worked out, so the row says how
            // many were left out and stops there rather than showing a total nobody summed.
            var line = WithheldEntriesLine.buildLine(List.of(
                createEntry("Stability"),
                createEntry("Size")));

            assertThat(line.countedValue())
                .isNull();
            assertThat(line.valueText())
                .isEqualTo(CellTooltipEntryLine.NO_SCORE);
        }

        @Test
        void buildLinePassesOverANumberNothingEarned() {
            // A nought an account recorded for a colony it never weighed is that account's statement
            // rather than a figure the colony competed with, so it is no part of any sum. A run of
            // those is stood for by its count alone.
            var line = WithheldEntriesLine.buildLine(List.of(
                createCountedEntry("Kazeron", 320),
                createUncountedEntry("Sentinel Gantries")));

            assertThat(line.countedValue())
                .isEqualTo(320);
        }

        @Test
        void buildLineReadsAsANoteAboutTheListRatherThanOneOfTheThingsInIt() {
            // It is a statement about the listing, so its name takes the quiet shade the box states
            // its own arithmetic in - loud, it would read as one more of the things being listed.
            var line = WithheldEntriesLine.buildLine(List.of(createCountedEntry("Kazeron", 320)));

            assertThat(line.isAside())
                .isTrue();
        }

        @Test
        void buildLineCarriesNoMarkOfItsOwn() {
            // There is no one thing for a mark to be a picture of: the row stands for several.
            var line = WithheldEntriesLine.buildLine(List.of(createCountedEntry("Kazeron", 320)));

            assertThat(line.hasMark())
                .isFalse();
        }
    }

    // One withheld thing carrying the number its block counted it in.
    private static CellTooltipEntry createCountedEntry(String labelText, int countedValue) {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createCountedLine(CellTooltipMark.NO_MARK, labelText, countedValue));
    }

    // One withheld thing whose number an account recorded rather than anything it earned.
    private static CellTooltipEntry createUncountedEntry(String labelText) {
        return CellTooltipEntry.createEntry(CellTooltipEntryLine
            .createCountedLine(CellTooltipMark.NO_MARK, labelText, 0)
            .statesUncountedValue());
    }

    // One withheld thing counted in nothing at all - a term worded rather than numbered.
    private static CellTooltipEntry createEntry(String labelText) {
        return CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
            CellTooltipMark.NO_MARK,
            labelText,
            "3.5"));
    }
}
