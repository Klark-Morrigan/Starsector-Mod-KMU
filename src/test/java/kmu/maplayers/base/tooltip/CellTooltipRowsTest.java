package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.TooltipLabelPlacement;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the shapes a cell-tooltip body is written in, since what separates them is exactly what a reader
 * of the box sees: a top-tier line opens a block flush in the bright colour, a nested one belongs to the
 * line above it by its indent and plainer colour, a banner leaves the table altogether to be set across
 * the box with its crest carried inside its own words, and a qualifier run picks a fact out in gold on
 * the line it qualifies. Two layers writing content through these cannot drift on any of it.
 */
final class CellTooltipRowsTest {

    private static final Color BRIGHT = new Color(200, 230, 255);
    private static final Color TEXT = Color.LIGHT_GRAY;
    private static final Color GOLD = new Color(255, 200, 100);
    private static final String CREST = "graphics/ion_storm_icon.png";

    // The runs a line reads as, in order: what it names, then any qualifier picked out beside it. A
    // banner led by a crest opens on that image instead, so its words sit one run later.
    private static final int LABEL_RUN = 0;
    private static final int QUALIFIER_RUN = 1;
    private static final int BANNER_CREST_RUN = 0;
    private static final int BANNER_LABEL_RUN = 1;
    private static final int BANNER_QUALIFIER_RUN = 2;

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
    class BuildTopTierRow {

        @Test
        void buildTopTierRowOpensABlockFlushWithItsCrestAndValue() {

            var row = CellTooltipRows.buildTopTierRow(CREST, "Ion Storm", "42");

            assertThat(readLabelTextRun(row, LABEL_RUN).text())
                .isEqualTo("Ion Storm");
            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(BRIGHT);

            assertThat(row.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(CREST));
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("42", GOLD)));
        }

        @Test
        void buildTopTierRowStaysInTheCrestColumnWithoutACrest() {
            // A crestless header still aligns with the crested lines around it, so a body mixing the
            // two does not read as two staggered columns.
            var row = CellTooltipRows.buildTopTierRow(null, "Independent", CellTooltipRows.NO_SCORE);

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }
    }

    @Nested
    class BuildNestedRow {

        @Test
        void buildNestedRowIndentsUnderTheLineAboveItInThePlainColour() {

            var row = CellTooltipRows.buildNestedRow(CREST, "Ion Storm", "17");

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("17", TEXT)));
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildNestedRowChargesNoColumnForAnAbsentScore() {
            // A line with nothing to count fills its value slot with a run that draws nothing, so the
            // value column collapses for it rather than the line claiming a width it cannot use.
            var row = CellTooltipRows.buildNestedRow(null, "Decivilised", CellTooltipRows.NO_SCORE);

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(TEXT)));
        }
    }

    @Nested
    class BuildQualifierSpan {

        @Test
        void buildQualifierSpanReadsGold() {
            // The one decision the run exists for: a line that continues into it reads in two colours,
            // with what is being called out picked out from what is merely named.
            assertThat(CellTooltipRows.buildQualifierSpan("worsening"))
                .isEqualTo(new TextSpan("worsening", GOLD));
        }

        @Test
        void buildQualifierSpanLeavesTheLineItContinuesAtItsOwnTier() {
            // Continuing a line does not promote it: a qualified nested line is still nested, which is
            // what keeps the tier a choice of row builder rather than a side effect of a second run.
            var row = CellTooltipRows
                .buildNestedRow(CREST, "Ion Storm", CellTooltipRows.NO_SCORE)
                .continuesWith(CellTooltipRows.buildQualifierSpan("worsening"));

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(readLabelTextRun(row, QUALIFIER_RUN).colour())
                .isEqualTo(GOLD);
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
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
                .isEqualTo(new TextSpan("worsening", GOLD));
        }
    }
}
