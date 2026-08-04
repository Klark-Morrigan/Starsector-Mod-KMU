package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.TooltipRow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins how a body divides into blocks, since the division is what a reader of the box actually sees: a
 * heading opens flush over the lines it names and takes the break parting it from whatever sits above,
 * and a block with no lines contributes nothing rather than leaving its heading standing over an
 * absence a player would read as a failure to resolve one.
 */
final class CellTooltipSectionsTest {

    // The runs a line reads as; every line here is one run, none being qualified.
    private static final int LABEL_RUN = 0;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class AppendSection {

        @Test
        void appendSectionPutsTheHeadingAboveTheBlocksOwnLinesInOrder() {

            var rows = new ArrayList<TooltipRow>();

            CellTooltipSections.appendSection(
                rows,
                "Contested by:",
                List.of(createEntryRow("The Hegemony"), createEntryRow("Tri-Tachyon")));

            assertThat(readLabelTexts(rows))
                .containsExactly("Contested by:", "The Hegemony", "Tri-Tachyon");
        }

        @Test
        void appendSectionLeavesTheBodyUntouchedForABlockWithNoLines() {
            // The rule the whole class exists for: a heading over nothing tells the player a block
            // failed to fill, when in truth there was nothing to put in it.
            var rows = new ArrayList<TooltipRow>(List.of(createEntryRow("Unpopulated")));

            CellTooltipSections.appendSection(rows, "Contested by:", List.of());

            assertThat(readLabelTexts(rows))
                .containsExactly("Unpopulated");
        }

        @Test
        void appendSectionAddsItsBlockBeneathWhateverTheBodyAlreadyHolds() {
            // Blocks read in the order they are appended, which is what leaves a body's running order
            // stated by its own calls rather than by a rule inside this one.
            var rows = new ArrayList<TooltipRow>();

            CellTooltipSections.appendSection(rows, "Claim:", List.of(createEntryRow("Pirates")));
            CellTooltipSections.appendSection(
                rows,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            assertThat(readLabelTexts(rows))
                .containsExactly("Claim:", "Pirates", "Contested by:", "The Hegemony");
        }

        @Test
        void appendSectionPartsItsBlockFromTheOneAboveOnTheHeadingAlone() {
            // Only the heading takes the break, so the box reads as blocks: an entry taking one would
            // part it from the heading it belongs to.
            var rows = new ArrayList<TooltipRow>();
            var headingRow = 0;
            var entryRow = 1;

            CellTooltipSections.appendSection(
                rows,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            assertThat(rows.get(headingRow).hasSectionBreak())
                .isTrue();
            assertThat(rows.get(entryRow).hasSectionBreak())
                .isFalse();
        }

        @Test
        void appendSectionDrawsTheHeadingAtNoIndentCarryingNeitherCrestNorValue() {
            // A heading names a block rather than being one of its entries: at no indent of its own,
            // in the bright colour, and with both slots left blank.
            var rows = new ArrayList<TooltipRow>();
            var headingRow = 0;

            CellTooltipSections.appendSection(
                rows,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            var heading = (TooltipRow.TableRow) rows.get(headingRow);

            assertThat(readLabelRun(heading, LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", PLAYER_BRIGHT));
            assertThat(heading.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(heading.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(heading.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(HIGHLIGHT)));
        }

        @Test
        void appendSectionStartsTheHeadingWhereTheCrestedEntriesStart() {
            // Not flush at the box's content edge, which zero indent alone would suggest: a heading
            // carrying no crest still reserves the gutter its entries lead with, so it begins where
            // their labels do rather than where the box's content does.
            var rows = new ArrayList<TooltipRow>();
            var headingRow = 0;

            CellTooltipSections.appendSection(
                rows,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            var heading = (TooltipRow.TableRow) rows.get(headingRow);

            assertThat(heading.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }
    }

    // One line of a block, told apart from its siblings by its label alone - what the block holds is
    // the caller's business, so any line the vocabulary can build serves.
    private static TooltipRow createEntryRow(String text) {
        return CellTooltipRows.buildNestedRow(null, text, CellTooltipRows.NO_SCORE);
    }

    private static List<String> readLabelTexts(List<TooltipRow> rows) {
        return rows
            .stream()
            .map(row -> readLabelTextRun(row, LABEL_RUN).text())
            .toList();
    }
}
