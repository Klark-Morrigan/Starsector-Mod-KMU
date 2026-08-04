package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.TooltipRow;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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

    private static final Color BRIGHT = new Color(200, 230, 255);
    private static final Color TEXT = Color.LIGHT_GRAY;
    private static final Color GOLD = new Color(255, 200, 100);

    // The runs a line reads as; every line here is one run, none being qualified.
    private static final int LABEL_RUN = 0;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getBrightPlayerColor)
            .thenReturn(BRIGHT);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(GOLD);
    }

    @AfterEach
    void clearColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
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
        void appendSectionDrawsTheHeadingFlushAndCarryingNeitherCrestNorValue() {
            // A heading names a block rather than being one of its entries, which is what the shape
            // says: flush at the box's edge, in the bright colour, and with both slots left blank.
            var rows = new ArrayList<TooltipRow>();
            var headingRow = 0;

            CellTooltipSections.appendSection(
                rows,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            var heading = (TooltipRow.TableRow) rows.get(headingRow);

            assertThat(readLabelRun(heading, LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", BRIGHT));
            assertThat(heading.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(heading.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(heading.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(GOLD)));
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
