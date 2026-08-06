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
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NESTED_MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins how a body divides into blocks and how a block lays out what it lists, since both are exactly
 * what a reader of the box sees: a heading stands clear of the crest gutter in gold above the entries it
 * names, an entry sits flush with whatever it is made up of inset beneath it, a line speaking for the
 * whole system stands as a block of its own, and a block with nothing to list contributes nothing rather
 * than leaving its heading standing over an absence a player would read as a failure to resolve one.
 *
 * <p>How far apart the blocks then stand is the widget's and pinned there; what is fixed here is that a
 * heading and the entries it names are one block, which is what that spacing follows from.
 */
final class CellTooltipSectionsTest {

    private static final String CREST = "graphics/hegemony_crest.png";

    // What a line is called, which is the run every case here reads it by; how a line calling something
    // out ends is the vocabulary's and pinned there.
    private static final int LABEL_RUN = 0;

    // Where the heading sits inside the block it opens, and where the first entry it names follows.
    private static final int HEADING_ROW = 0;
    private static final int FIRST_ENTRY_ROW = 1;

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
        void appendSectionPutsTheHeadingAboveTheBlocksOwnEntriesInOrder() {

            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(readLabelTexts(sections))
                .containsExactly("Contested by:", "The Hegemony", "Tri-Tachyon");
        }

        @Test
        void appendSectionHoldsTheHeadingAndItsEntriesAsOneBlock() {
            // The heading belongs with what it names: parted from its own entries it would read as a
            // line of the block above, which is the only thing that could tell a reader whose heading
            // it is.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).rows())
                .hasSize(3);
        }

        @Test
        void appendSectionLeavesTheBodyUntouchedForABlockWithNothingToList() {
            // The rule the whole class exists for: a heading over nothing tells the player a block
            // failed to fill, when in truth there was nothing to put in it.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntry("Pirates")));
            CellTooltipSections.appendSection(sections, "Contested by:", List.of());

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "Pirates");
        }

        @Test
        void appendSectionAddsItsBlockBeneathWhateverTheBodyAlreadyHolds() {
            // Blocks read in the order they are appended, which is what leaves a body's running order
            // stated by its own calls rather than by a rule inside this one.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntry("Pirates")));
            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntry("The Hegemony")));

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "Pirates", "Contested by:", "The Hegemony");
        }

        @Test
        void appendSectionLaysTheHeadingAtTheBoxsContentEdgeInGold() {
            // Where the review found the fault: a crestless heading at no indent still reserves the
            // gutter its entries lead with, so it begins where their labels do and reads as indented
            // under nothing. Placement, not indent, is what moves it - and gold is what stops it
            // reading as one of the entries it names.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(createEntry("The Hegemony")));

            var heading = (TooltipRow.TableRow) readRow(sections, HEADING_ROW);

            assertThat(readLabelRun(heading, LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", HIGHLIGHT));
            assertThat(heading.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(heading.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(heading.labelledRow().trailingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void appendSectionLaysAnEntryFlushWithItsMarkAndItsValueCalledOut() {
            // An entry is one of the things being listed, so it opens flush rather than inset under the
            // heading that names it, and its number reads in the called-out shade like every value in
            // the box.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Claim:",
                List.of(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(CREST, "The Hegemony", "1,200"))));

            var entry = (TooltipRow.TableRow) readRow(sections, FIRST_ENTRY_ROW);

            assertThat(readLabelRun(entry, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(entry.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(entry.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(CREST));
            assertThat(entry.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void appendSectionInsetsWhatAnEntryIsMadeUpOfBeneathIt() {
            // The tiers the box has, read off the indent and the plainer colour rather than off any
            // label saying which is which - and the entries themselves stay flush, so a block of them
            // does not read as a list nested under its own heading.
            var sections = new ArrayList<TooltipSection>();
            var memberRow = 2;

            CellTooltipSections.appendSection(
                sections,
                "Dominated by:",
                List.of(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(null, "Rebel Pact", "1,200"))
                    .nesting(List.of(CellTooltipEntry.createEntry(
                        CellTooltipEntryLine.createLine(CREST, "The Hegemony", "900"))))));

            assertThat(readLabelTexts(sections))
                .containsExactly("Dominated by:", "Rebel Pact", "The Hegemony");

            var member = (TooltipRow.TableRow) readRow(sections, memberRow);

            assertThat(readLabelRun(member, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
            assertThat(member.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(((TooltipRow.TableRow) readRow(sections, FIRST_ENTRY_ROW)).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void appendSectionReadsEachBreakdownUnderTheThingItBreaksDown() {
            // Depth-first is what makes a listing readable: a market's own factors follow that market
            // rather than being gathered after every market in the block, so the reader never has to
            // carry which line a run of factors belongs to.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Dominated by:",
                List.of(
                    createEntry("Chicomoztoc")
                        .nesting(List.of(createEntry("Size"), createEntry("Patrols"))),
                    createEntry("Kazeron")
                        .nesting(List.of(createEntry("Station")))));

            // The first market's factors read in the order it lists them and are done before the next
            // market opens, so the run under a line never spills past the thing it belongs to.
            assertThat(readLabelTexts(sections))
                .containsExactly(
                    "Dominated by:",
                    "Chicomoztoc",
                    "Size",
                    "Patrols",
                    "Kazeron",
                    "Station");
        }

        @Test
        void appendSectionStepsInAgainForEachLevelOfABreakdown() {
            // The whole point of the entry being a tree: a listing goes as deep as its subject matter,
            // and each level is legibly inside the one above rather than sharing its indent.
            var sections = new ArrayList<TooltipSection>();
            var childRow = 2;
            var grandchildRow = 3;

            CellTooltipSections.appendSection(
                sections,
                "Dominated by:",
                List.of(createEntry("Chicomoztoc")
                    .nesting(List.of(createEntry("Patrols")
                        .nesting(List.of(createEntry("Light patrol")))))));

            assertThat(readLabelTexts(sections))
                .containsExactly("Dominated by:", "Chicomoztoc", "Patrols", "Light patrol");

            assertThat(((TooltipRow.TableRow) readRow(sections, FIRST_ENTRY_ROW)).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(((TooltipRow.TableRow) readRow(sections, childRow)).indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(((TooltipRow.TableRow) readRow(sections, grandchildRow)).indent())
                .isCloseTo(NESTED_MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void appendSectionOpensNoGutterForAnEntryCarryingNoMark() {
            // A list of things that carry no mark - industries, conditions, hazards - lays through the
            // same construct: the leading slot is left unfilled, and the box reserves a crest column
            // only for the boxes that have one.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntry("None")));

            assertThat(((TooltipRow.TableRow) readRow(sections, FIRST_ENTRY_ROW))
                    .labelledRow()
                    .leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void appendSectionOpensABlockListingNothingMarkedAtTheContentEdge() {
            // Where the fault showed: the crest gutter is the box's one column, widened by whichever
            // block does carry crests, so a block listing nothing marked opened its lines behind a
            // gutter none of them could fill - a claim of "None" reading as indented under the very
            // heading naming it.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(
                sections,
                "Claim:",
                List.of(createEntry("None").nesting(List.of(createEntry("Uncontested")))));

            assertThat(readLabelPlacements(sections))
                .containsOnly(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void appendSectionHoldsAMarklessLineInTheGutterABlockDoesReserve() {
            // The other half of the same rule: the gutter is answered for the block rather than per
            // line, so a faction the game gives no crest stays aligned with the crested lines beside it
            // instead of stepping out of the column they share.
            var sections = new ArrayList<TooltipSection>();
            var marklessEntryRow = 2;

            CellTooltipSections.appendSection(
                sections,
                "Contested by:",
                List.of(
                    CellTooltipEntry.createEntry(
                        CellTooltipEntryLine.createLine(CREST, "The Hegemony", "1,200")),
                    createEntry("Independent")));

            assertThat(((TooltipRow.TableRow) readRow(sections, marklessEntryRow)).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }

        @Test
        void appendSectionChargesNoValueColumnForAnEntryCountedInNothing() {
            // A line with nothing to count fills its value slot with a run that draws nothing, so the
            // column collapses for it rather than the line claiming a width it cannot use.
            var sections = new ArrayList<TooltipSection>();

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntry("None")));

            assertThat(((TooltipRow.TableRow) readRow(sections, FIRST_ENTRY_ROW))
                    .labelledRow()
                    .trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(HIGHLIGHT)));
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

            CellTooltipSections.appendSection(sections, "Claim:", List.of(createEntry("Pirates")));
            CellTooltipSections.appendBannerSection(
                sections,
                Optional.of(CellTooltipRows.buildBannerRow(null, "Decivilised")));

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "Pirates", "Decivilised");
        }
    }

    // One thing a block lists, told apart from its siblings by its name alone - what it carries beyond
    // that is stated by the cases that are about it.
    private static CellTooltipEntry createEntry(String labelText) {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(null, labelText, CellTooltipRows.NO_SCORE));
    }

    // One line of the body, by its place in the flat run the box draws - the cases below are about which
    // lines a block contributes, so its own grouping is read back out rather than walked.
    private static TooltipRow readRow(List<TooltipSection> sections, int rowIndex) {
        return TooltipSection
            .readRowsInOrder(sections)
            .get(rowIndex);
    }

    // Where every line of a body starts across the box. Read over the whole body rather than row by row,
    // since the rule under test is about the block agreeing with itself - one line asserted alone would
    // pass while the lines around it opened from another column.
    private static List<TooltipLabelPlacement> readLabelPlacements(List<TooltipSection> sections) {
        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(row -> ((TooltipRow.TableRow) row).labelPlacement())
            .toList();
    }

    private static List<String> readLabelTexts(List<TooltipSection> sections) {
        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(row -> readLabelTextRun(row, LABEL_RUN).text())
            .toList();
    }
}
