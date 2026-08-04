package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * heading opens the block it names, its lines follow it inside that same block, a line speaking for the
 * whole system stands as a block of its own, and a block with no lines contributes nothing rather than
 * leaving its heading standing over an absence a player would read as a failure to resolve one.
 *
 * <p>How far apart the blocks then stand is the widget's and pinned there; what is fixed here is that a
 * heading and the lines it names are one block, which is what that spacing follows from.
 */
final class CellTooltipSectionsTest {

    // The runs a line reads as; every line here is one run, none being qualified.
    private static final int LABEL_RUN = 0;

    // Where the heading sits inside the block it opens, and where the first line it names follows.
    private static final int HEADING_ROW = 0;

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

            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntryRow("The Hegemony"), createEntryRow("Tri-Tachyon")));

            assertThat(readLabelTexts(sections))
                .containsExactly("Contested by:", "The Hegemony", "Tri-Tachyon");
        }

        @Test
        void appendSectionHoldsTheHeadingAndItsLinesAsOneBlock() {
            // The heading belongs with what it names: parted from its own entries it would read as a
            // line of the block above, which is the only thing that could tell a reader whose heading
            // it is.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntryRow("The Hegemony"), createEntryRow("Tri-Tachyon")));

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).rows())
                .hasSize(3);
        }

        @Test
        void appendSectionLeavesTheBodyUntouchedForABlockWithNoLines() {
            // The rule the whole class exists for: a heading over nothing tells the player a block
            // failed to fill, when in truth there was nothing to put in it.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntryRow("Pirates")));
            CellTooltipSections.appendSection(sections, "Contested by:", List.of());

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "Pirates");
        }

        @Test
        void appendSectionAddsItsBlockBeneathWhateverTheBodyAlreadyHolds() {
            // Blocks read in the order they are appended, which is what leaves a body's running order
            // stated by its own calls rather than by a rule inside this one.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntryRow("Pirates")));
            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "Pirates", "Contested by:", "The Hegemony");
        }

        @Test
        void appendSectionDrawsTheHeadingAtNoIndentCarryingNeitherCrestNorValue() {
            // A heading names a block rather than being one of its entries: at no indent of its own,
            // in the bright colour, and with both slots left blank.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            var heading = (TooltipRow.TableRow) readRow(sections, HEADING_ROW);

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
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntryRow("The Hegemony")));

            var heading = (TooltipRow.TableRow) readRow(sections, HEADING_ROW);

            assertThat(heading.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }
    }

    @Nested
    class AppendBannerSection {

        @Test
        void appendBannerSectionGivesTheLineABlockOfItsOwn() {
            // A banner speaks for the system rather than opening a list, so it is parted from whatever
            // follows instead of being read as that block's first entry.
            var sections = new ArrayList<TooltipSection>();
            var bannerRow = CellTooltipRows.buildBannerRow(null, "Unpopulated");

            CellTooltipSections.appendBannerSection(sections, Optional.of(bannerRow));

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).rows())
                .containsExactly(bannerRow);
        }

        @Test
        void appendBannerSectionLeavesTheBodyUntouchedWhenThereIsNothingToState() {
            // The absence rule the helper exists to hold: a system with nothing to state gets no empty
            // block, which would part the body around a gap holding no line.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendBannerSection(sections, Optional.empty());

            assertThat(sections)
                .isEmpty();
        }

        @Test
        void appendBannerSectionAddsItsBlockBeneathWhateverTheBodyAlreadyHolds() {
            
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntryRow("Pirates")));
            CellTooltipSections.appendBannerSection(
                sections,
                Optional.of(CellTooltipRows.buildBannerRow(null, "Decivilised")));

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "Pirates", "Decivilised");
        }
    }

    // One line of a block, told apart from its siblings by its label alone - what the block holds is
    // the caller's business, so any line the vocabulary can build serves.
    private static TooltipRow createEntryRow(String text) {
        return CellTooltipRows.buildNestedRow(null, text, CellTooltipRows.NO_SCORE);
    }

    // One line of the body, by its place in the flat run the box draws - the cases below are about which
    // lines a block contributes, so its own grouping is read back out rather than walked.
    private static TooltipRow readRow(List<TooltipSection> sections, int rowIndex) {
        return TooltipSection
            .readRowsInOrder(sections)
            .get(rowIndex);
    }

    private static List<String> readLabelTexts(List<TooltipSection> sections) {
        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(row -> readLabelTextRun(row, LABEL_RUN).text())
            .toList();
    }
}
