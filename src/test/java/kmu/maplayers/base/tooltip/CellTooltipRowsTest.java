package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the shapes a cell-tooltip body is written in, since what separates them is exactly what a reader
 * of the box sees: a heading stands clear of the crest gutter in gold to name a block, an entry line
 * lists something in it flush in the bright colour, a member line belongs to the entry above it by its
 * indent and plainer colour, a banner leaves the table altogether to be set across the box with its
 * crest carried inside its own words, and a line calling something out ends on it in gold at whichever
 * tier it sits. Two layers writing content through these cannot drift on any of it.
 */
final class CellTooltipRowsTest {

    private static final String CREST = "graphics/ion_storm_icon.png";

    // The runs a line reads as, in order: what it names, then any qualifier picked out beside it. A
    // banner led by a crest opens on that image instead, so its words sit one run later.
    private static final int LABEL_RUN = 0;
    private static final int QUALIFIER_RUN = 1;
    private static final int BANNER_CREST_RUN = 0;
    private static final int BANNER_LABEL_RUN = 1;
    private static final int BANNER_QUALIFIER_RUN = 2;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildSectionHeadingRow {

        @Test
        void buildSectionHeadingRowNamesItsBlockInGoldClearOfTheCrestGutter() {
            // The two things that tell a heading from its own entries. Inside the gutter it starts
            // where their labels start and so reads as indented under nothing; in their own bright it
            // is told apart only by lacking a crest.
            var row = CellTooltipRows.buildSectionHeadingRow("Contested by:");

            assertThat(readLabelTextRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", HIGHLIGHT));
            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void buildSectionHeadingRowCarriesNeitherCrestNorValue() {
            // A heading names a block rather than being one of the things in it, so it fills neither
            // column - and charging the value column for a number it will never carry would widen the
            // box around an empty slot.
            var row = CellTooltipRows.buildSectionHeadingRow("Contested by:");

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }
    }

    @Nested
    class BuildEntryRow {

        @Test
        void buildEntryRowListsSomethingFlushWithItsMarkAndValue() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildEntryRow(
                CellTooltipEntryLine.createLine(CREST, "Ion Storm", "42"));

            assertThat(readLabelTextRun(row, LABEL_RUN).text())
                .isEqualTo("Ion Storm");
            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(PLAYER_BRIGHT);

            assertThat(row.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(CREST));
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("42", HIGHLIGHT)));
        }

        @Test
        void buildEntryRowStaysInTheCrestColumnWithoutAMark() {
            // A markless entry still aligns with the crested lines around it, so a block mixing the
            // two does not read as two staggered columns.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildEntryRow(
                CellTooltipEntryLine.createLine(null, "Independent", CellTooltipRows.NO_SCORE));

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }

        @Test
        void buildEntryRowContinuesIntoWhatTheLineCallsOut() {
            // Continuing a line does not promote it: a qualified entry is still an entry, which is what
            // keeps the tier a choice of builder rather than a side effect of a second run.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildEntryRow(CellTooltipEntryLine
                .createLine(CREST, "Ion Storm", CellTooltipRows.NO_SCORE)
                .qualifiedWith("worsening"));

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(PLAYER_BRIGHT);
            assertThat(readLabelTextRun(row, QUALIFIER_RUN).colour())
                .isEqualTo(HIGHLIGHT);
            assertThat(row.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void buildEntryRowSaysNothingMoreForALineCallingNothingOut() {
            // The absence is a line of one run rather than one ending on a run that draws nothing, so a
            // plain line measures as the words it actually says.
            var row = CellTooltipRows.buildEntryRow(
                CellTooltipEntryLine.createLine(CREST, "Ion Storm", "42"));

            assertThat(row.labelRuns())
                .hasSize(1);
        }
    }

    @Nested
    class BuildMemberRow {

        @Test
        void buildMemberRowIndentsUnderTheLineAboveItInThePlainColour() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildMemberRow(
                CellTooltipEntryLine.createLine(CREST, "Ion Storm", "17"));

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("17", TEXT)));
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildMemberRowChargesNoColumnForAnAbsentScore() {
            // A line with nothing to count fills its value slot with a run that draws nothing, so the
            // value column collapses for it rather than the line claiming a width it cannot use.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildMemberRow(
                CellTooltipEntryLine.createLine(null, "Decivilised", CellTooltipRows.NO_SCORE));

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(TEXT)));
        }

        @Test
        void buildMemberRowContinuesIntoWhatTheLineCallsOutAtItsOwnTier() {
            // A status stated on a member reads exactly as one stated on the entry it belongs to, and
            // calling it out does not lift the member out of its indent - which is the whole reason
            // both tiers qualify through one rule rather than each spelling it out.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildMemberRow(CellTooltipEntryLine
                .createLine(CREST, "Ion Storm", CellTooltipRows.NO_SCORE)
                .qualifiedWith("worsening"));

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(readLabelTextRun(row, QUALIFIER_RUN))
                .isEqualTo(new TextSpan("worsening", HIGHLIGHT));
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }
    }

    @Nested
    class BuildQualifierSpan {

        @Test
        void buildQualifierSpanReadsGold() {
            // The one decision the run exists for: a line that continues into it reads in two colours,
            // with what is being called out picked out from what is merely named.
            assertThat(CellTooltipRows.buildQualifierSpan("worsening"))
                .isEqualTo(new TextSpan("worsening", HIGHLIGHT));
        }
    }

    @Nested
    class BuildBannerRow {

        @Test
        void buildBannerRowReadsInThePlainTextColour() {
            // A banner states a fact rather than calling one out, so it is spoken plainly - what it may
            // call out is the qualifier it ends on, which reads gold.
            assertThat(readLabelTextRun(CellTooltipRows.buildBannerRow(null, "Unpopulated"), LABEL_RUN))
                .isEqualTo(new TextSpan("Unpopulated", TEXT));
        }

        @Test
        void buildBannerRowCarriesItsCrestAsARunOfTheLine() {
            // The whole point of the shape: the crest rides inside the label rather than in the gutter
            // the entries below align to, so the line centres crest and words together instead of
            // anchoring the image to a column a centred line has left.
            var row = CellTooltipRows.buildBannerRow(CREST, "Ion Storm");

            assertThat(readLabelRun(row, BANNER_CREST_RUN))
                .isEqualTo(new ImageSpan(CREST));
            assertThat(readLabelRun(row, BANNER_LABEL_RUN))
                .isEqualTo(new TextSpan("Ion Storm", TEXT));
        }

        @Test
        void buildBannerRowOpensAtItsWordsWithoutACrest() {
            // A caller resolving a crest that simply does not exist hands the absence straight over, so
            // the line is built from its words alone rather than from an image run with nothing to load.
            var row = CellTooltipRows.buildBannerRow(null, "Ion Storm");

            assertThat(row.labelRuns())
                .containsExactly(new TextSpan("Ion Storm", TEXT));
        }

        @Test
        void buildBannerRowTakesTheSameQualifierEveryOtherLineDoes() {
            // A banner calls something out in the shade every line calls things out in, so leaving the
            // table costs it none of the vocabulary the lines below it are written in.
            var row = CellTooltipRows
                .buildBannerRow(CREST, "Ion Storm")
                .continuesWith(CellTooltipRows.buildQualifierSpan("worsening"));

            assertThat(readLabelRun(row, BANNER_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("worsening", HIGHLIGHT));
        }
    }
}
