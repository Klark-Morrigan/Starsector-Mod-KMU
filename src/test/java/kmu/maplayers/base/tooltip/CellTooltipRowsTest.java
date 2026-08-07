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

import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NESTED_MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_SUBORDINATION;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.ONE_LEVEL_SUBORDINATED;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TWO_LEVELS_SUBORDINATED;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the shapes a cell-tooltip body is written in, since what separates them is exactly what a reader
 * of the box sees: a heading stands clear of the crest gutter in gold to name a block, a line the block
 * lists in its own right sits flush in the bright colour, a line found beneath one belongs to it by its
 * indent and plainer colour and steps in again per level below that, a banner leaves the table altogether
 * to be set across the box with its crest carried inside its own words, a line calling something out
 * ends on it in gold at whichever tier it sits, and a value stating the working behind it opens on that
 * working in the quiet shade whatever colour the line itself speaks in. Two layers writing content
 * through these cannot drift on any of it.
 */
final class CellTooltipRowsTest {

    private static final String CREST = "graphics/ion_storm_icon.png";

    // Where a line was found: one of the block's own, one of the things that line breaks down into, and
    // one level deeper again - which is the shape a breakdown three levels down takes. Plus the line
    // gathered under another as its peer, one step in yet speaking at the level of the line it sits
    // under, which is the one case telling the indent and the demotion apart.
    //
    // Stated as the two counts each level is rather than walked down to from the block's own, for the
    // reason the indents beside them are: a level built by calling the refinements under test would be
    // edited alongside them and could never fail, while these have to be changed deliberately when what
    // a tier means does.
    private static final CellTooltipEntryLevel LISTED_LEVEL = new CellTooltipEntryLevel(0, 0);
    private static final CellTooltipEntryLevel PEER_LEVEL = new CellTooltipEntryLevel(1, 0);
    private static final CellTooltipEntryLevel MEMBER_LEVEL = new CellTooltipEntryLevel(1, 1);
    private static final CellTooltipEntryLevel NESTED_MEMBER_LEVEL = new CellTooltipEntryLevel(2, 2);

    // What the block around a line answered about its crest gutter: reserved where something it lists
    // leads with a mark, dropped where nothing does.
    private static final boolean RESERVING_CREST_COLUMN = true;
    private static final boolean NOT_RESERVING_CREST_COLUMN = false;

    // The runs a line reads as, in order: what it names, then any place it holds in an ordering, then
    // any qualifier picked out beside it. A line stating no place closes the gap, so its qualifier
    // takes the run the place would have. A banner led by a crest opens on that image instead, so its
    // words sit one run later.
    private static final int LABEL_RUN = 0;
    private static final int QUALIFIER_RUN = 1;
    private static final int INDEX_RUN = 1;
    private static final int INDEXED_QUALIFIER_RUN = 2;
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
    class BuildListedRow {

        @Test
        void buildListedRowListsSomethingFlushWithItsMarkAndValue() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST, "Ion Storm", "42"),
                LISTED_LEVEL,
                RESERVING_CREST_COLUMN);

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
        void buildListedRowStaysInTheCrestColumnWithoutAMark() {
            // A markless entry still aligns with the crested lines around it, so a block mixing the
            // two does not read as two staggered columns.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Independent", CellTooltipRows.NO_SCORE),
                LISTED_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }

        @Test
        void buildListedRowOpensAtTheContentEdgeWhereTheBlockReservesNoCrestColumn() {
            // The gutter is the box's one column, so a block listing nothing marked would otherwise open
            // behind a gutter another block's crests widened - which reads as its lines being indented
            // under their own heading rather than as an empty column.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "None", CellTooltipRows.NO_SCORE),
                LISTED_LEVEL,
                NOT_RESERVING_CREST_COLUMN);

            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void buildListedRowOpensAMemberAtTheContentEdgeWhereTheBlockReservesNoCrestColumn() {
            // A member follows its block's answer as its entry does, so a breakdown under an unmarked
            // line steps in from the same column its parent opened at rather than from a gutter away.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Size", "8"),
                MEMBER_LEVEL,
                NOT_RESERVING_CREST_COLUMN);

            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowContinuesIntoWhatTheLineCallsOut() {
            // Continuing a line does not promote it: a qualified entry is still an entry, which is what
            // keeps the tier a matter of how deep the line sits rather than a side effect of a second
            // run.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(CREST, "Ion Storm", CellTooltipRows.NO_SCORE)
                    .qualifiedWith("worsening"),
                LISTED_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(PLAYER_BRIGHT);
            assertThat(readLabelTextRun(row, QUALIFIER_RUN).colour())
                .isEqualTo(HIGHLIGHT);
            assertThat(row.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowSaysNothingMoreForALineCallingNothingOut() {
            // The absence is a line of one run rather than one ending on a run that draws nothing, so a
            // plain line measures as the words it actually says.
            var row = CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST, "Ion Storm", "42"),
                LISTED_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.labelRuns())
                .hasSize(1);
        }

        @Test
        void buildListedRowIndentsWhatWasFoundUnderALineInThePlainColour() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST, "Ion Storm", "17"),
                MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("17", TEXT)));
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowStepsInAgainForEachLevelBelowTheFirst() {
            // What lets a breakdown go as deep as its subject matter: the indent is charged per level,
            // so a line under a line under an entry is legibly inside both rather than sharing one
            // indent with the level above it.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Light patrol", "3"),
                NESTED_MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(row.indent())
                .isCloseTo(NESTED_MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowOpensAValueOnItsWorkingInTheQuietShade() {
            // The split says which part of the value is the finding and which is the arithmetic
            // behind it; drawn in one shade the two read as a single number with a stray separator.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(CREST, "Ion Storm", "42")
                    .derivesValueFrom("0.25 /"),
                LISTED_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.TextRuns(List.of(
                    new TextSpan("0.25 /", GRAY),
                    new TextSpan("42", HIGHLIGHT))));
        }

        @Test
        void buildListedRowKeepsAWorkingQuietAgainstAMembersOwnColour() {
            // The working is quieter than whatever the line it opens speaks in, so the same split
            // reads the same way at a tier the box draws in the plain colour.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(null, "Small: 2", "500")
                    .derivesValueFrom("0.25 /"),
                MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.TextRuns(List.of(
                    new TextSpan("0.25 /", GRAY),
                    new TextSpan("500", TEXT))));
        }

        @Test
        void buildListedRowShowsANumberAloneAsOneRunWhereTheLineStatesNoWorking() {
            // Which is almost every line in the box: a value of one run is what a stack of rows is
            // aligned by, and a run drawing nothing in front of it would be a gap held open for it.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Size", "8"),
                MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("8", TEXT)));
        }

        @Test
        void buildListedRowChargesNoColumnForAnAbsentScoreBeneathALine() {
            // A line with nothing to count fills its value slot with a run that draws nothing, so the
            // value column collapses for it rather than the line claiming a width it cannot use.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Decivilised", CellTooltipRows.NO_SCORE),
                MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(TEXT)));
        }

        @Test
        void buildListedRowContinuesIntoWhatTheLineCallsOutAtItsOwnTier() {
            // A status stated on a member reads exactly as one stated on the entry it belongs to, and
            // calling it out does not lift the member out of its indent - which is the whole reason
            // every tier qualifies through one rule rather than each spelling it out.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(CREST, "Ion Storm", CellTooltipRows.NO_SCORE)
                    .qualifiedWith("worsening"),
                MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(readLabelTextRun(row, QUALIFIER_RUN))
                .isEqualTo(new TextSpan(" worsening", HIGHLIGHT));
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowRunsAPlaceOnAfterTheNameInTheQuietShade() {
            // A place identifies the line rather than saying something about it, so it is drawn in the
            // shade the working behind a value is - not the gold a finding reads in - and sits with the
            // name it belongs to rather than at the end of the line.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(null, "Chicomoztoc", "19")
                    .indexedAt("[2]"),
                MEMBER_LEVEL,
                NOT_RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, INDEX_RUN))
                .isEqualTo(new TextSpan(" [2]", GRAY));
        }

        @Test
        void buildListedRowRunsAPlaceAheadOfWhatTheLineCallsOut() {
            // The two runs answer different questions, and in that order: which one this is, then what
            // is true of it. Reversed, the gold status would break the name from the number naming it.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(null, "Chicomoztoc", "19")
                    .indexedAt("[2]")
                    .qualifiedWith("strongest"),
                MEMBER_LEVEL,
                NOT_RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, INDEX_RUN))
                .isEqualTo(new TextSpan(" [2]", GRAY));
            assertThat(readLabelTextRun(row, INDEXED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan(" strongest", HIGHLIGHT));
        }

        @Test
        void buildListedRowAddsNoRunForALineWithNoPlaceToState() {
            // The ordinary line, and every line of the box that is not part of an ordering: it keeps the
            // runs it had rather than opening one that draws nothing between its name and its status.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(null, "Ion Storm", CellTooltipRows.NO_SCORE)
                    .qualifiedWith("worsening"),
                MEMBER_LEVEL,
                NOT_RESERVING_CREST_COLUMN);

            assertThat(readLabelTextRun(row, QUALIFIER_RUN))
                .isEqualTo(new TextSpan(" worsening", HIGHLIGHT));
        }

        @Test
        void buildListedRowSpeaksInTheBoxsOwnVoiceForOneOfTheBlocksOwnLines() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST, "The Hegemony", "1,200"),
                LISTED_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.subordinationLevel())
                .isEqualTo(NO_SUBORDINATION);
        }

        @Test
        void buildListedRowSetsAPeerInWithoutQuietingIt() {
            // The distinction the level exists for, read off the drawn line: a faction inside the alliance
            // naming it is inset beneath it while still saying who holds the system, so it is stepped
            // in without being demoted.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST, "The Hegemony", "900"),
                PEER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(row.subordinationLevel())
                .isEqualTo(NO_SUBORDINATION);
        }

        @Test
        void buildListedRowQuietensEachLevelOfAnAccount() {
            // The other half: what a line breaks down into is the box explaining itself, and a factor's
            // own tiers explain that, so each step of the account reads one step quieter.
            var factor = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Patrols", "120"),
                MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);
            var tier = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Light patrol", "3"),
                NESTED_MEMBER_LEVEL,
                RESERVING_CREST_COLUMN);

            assertThat(factor.subordinationLevel())
                .isEqualTo(ONE_LEVEL_SUBORDINATED);
            assertThat(tier.subordinationLevel())
                .isEqualTo(TWO_LEVELS_SUBORDINATED);
        }
    }

    @Nested
    class BuildQualifierSpan {

        @Test
        void buildQualifierSpanReadsGold() {
            // The one decision the run exists for: a line that continues into it reads in two colours,
            // with what is being called out picked out from what is merely named.
            assertThat(CellTooltipRows.buildQualifierSpan("worsening").colour())
                .isEqualTo(HIGHLIGHT);
        }

        @Test
        void buildQualifierSpanPartsItselfFromTheWordsItFollows() {
            // Runs are laid down touching, so the run opens with the gap itself - otherwise a label and
            // the status stated on it read as one word.
            assertThat(CellTooltipRows.buildQualifierSpan("hidden").text())
                .isEqualTo(" hidden");
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
                .isEqualTo(new TextSpan(" worsening", HIGHLIGHT));
        }
    }
}
