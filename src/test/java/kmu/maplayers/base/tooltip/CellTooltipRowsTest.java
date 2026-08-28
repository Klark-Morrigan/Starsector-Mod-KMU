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
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_QUALIFIER_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NESTED_MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_SUBORDINATION;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.ONE_LEVEL_SUBORDINATED;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TWO_LEVELS_SUBORDINATED;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readOpeningWords;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the shapes a cell-tooltip body is written in, since what separates them is exactly what a reader
 * of the box sees: a heading names a block in gold at the box's content edge, a line the block lists in
 * its own right sits flush in the bright colour, a line found beneath one belongs to it by its indent and
 * plainer colour and steps in again per level below that, a banner leaves the table altogether to be set
 * across the box, a line calling something out carries it in gold at whichever tier it sits, and a value
 * stating the working behind it opens on that working in the quiet shade whatever colour the line itself
 * speaks in. Two layers writing content through these cannot drift on any of it.
 *
 * <p>A mark is pinned as a run of the label on every shape, listed lines and banner alike, since that is
 * the one rule the box has for images: a marked line and a markless one at the same level have to start
 * their words at the same inset, or a breakdown reads as two staggered columns.
 *
 * <p>What a label is made of - the mark's own colouring, and the stretches a name saying one of the
 * box's findings is picked apart into - is {@link CellTooltipLabelsTest}'s. What is pinned here is
 * the tier each colour comes from, and that a label of several runs reaches one row with everything
 * after it still in place.
 */
final class CellTooltipRowsTest {

    private static final String CREST = "graphics/ion_storm_icon.png";
    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored(CREST);

    // A mark of the other kind: a glyph standing in for the name beside it rather than a picture of
    // anything, which is the case that reads in the line's own colour.
    private static final String COLONY_ICON = "graphics/warroom/icon_planet.png";
    private static final CellTooltipMark COLONY_MARK =
        CellTooltipMark.resolveMarkInLineColour(COLONY_ICON);

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
        void buildSectionHeadingRowNamesItsBlockInGoldAtTheContentEdge() {
            // Every line of the box opens at that edge, so the gold is what tells a heading from the
            // entries it names: in their own bright it would be told apart by lacking a mark alone.
            var row = CellTooltipRows.buildSectionHeadingRow("Contested by:");

            assertThat(readLabelTextRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", HIGHLIGHT));
            assertThat(row.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void buildSectionHeadingRowCarriesNeitherMarkNorValue() {
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
        void buildListedRowOpensAMarkedLineOnThatMarkAndLeavesItsLeadingSlotUnfilled() {
            // The mark rides inside the label, so it lands where the line's own indent put it rather
            // than in a gutter shared with whatever the box lists at another level.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST_MARK, "Ion Storm", "42"),
                LISTED_LEVEL);

            assertThat(readLabelRun(row, MARK_RUN))
                .isEqualTo(new ImageSpan(CREST));
            assertThat(readLabelTextRun(row, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("Ion Storm", PLAYER_BRIGHT));

            assertThat(row.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("42", HIGHLIGHT)));
        }

        @Test
        void buildListedRowDrawsAMarkStandingInForTheNameInTheColourOfItsOwnTier() {
            // The colour is the tier's rather than one number written down somewhere, so a mark on a
            // line found beneath another follows that line down to its plainer shade instead of
            // staying at the brightness the level above speaks in.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(COLONY_MARK, "Jangala", "4,000"),
                MEMBER_LEVEL);

            assertThat(readLabelRun(row, MARK_RUN))
                .isEqualTo(new ImageSpan(COLONY_ICON, TEXT));
        }

        @Test
        void buildListedRowOpensAMarklessLineOnItsWords() {
            // A caller resolving a mark the game simply does not have hands the absence straight over,
            // so the line is built from its words alone rather than from an image run with nothing to
            // load.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Independent", CellTooltipRows.NO_SCORE),
                LISTED_LEVEL);

            assertThat(readLabelTextRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("Independent", PLAYER_BRIGHT));
            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void buildListedRowStartsAMarkedAndAMarklessLineAtTheSameInset() {
            // The whole reason the mark left the leading column: two lines the block lists side by side
            // begin their labels at the same place whether either carries a mark, so a listing mixing
            // the two does not read as two staggered columns.
            var markedRow = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200"),
                LISTED_LEVEL);
            var marklessRow = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Independent", "900"),
                LISTED_LEVEL);

            assertThat(markedRow.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(marklessRow.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(markedRow.indent())
                .isCloseTo(marklessRow.indent(), within(TOLERANCE));
        }

        @Test
        void buildListedRowOpensAMemberAtTheContentEdgeInsetByItsLevel() {
            // A member opens at the same edge its entry does and is told from it by the indent alone,
            // so a breakdown steps in from where its parent started rather than from a gutter away.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Size", "8"),
                MEMBER_LEVEL);

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
                    .createLine(CREST_MARK, "Ion Storm", CellTooltipRows.NO_SCORE)
                    .qualifiedWith("worsening"),
                LISTED_LEVEL);

            assertThat(readLabelTextRun(row, MARKED_LABEL_RUN).colour())
                .isEqualTo(PLAYER_BRIGHT);
            assertThat(readLabelTextRun(row, MARKED_QUALIFIER_RUN).colour())
                .isEqualTo(HIGHLIGHT);
            assertThat(row.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowLeavesAGildedLineReadingAsItsWholeName() {
            // How every box's own suite identifies a line, held against the one shape that splits a
            // name into runs: a fixture named for what it is answers the name it is plainly shown by,
            // and the status behind it is not swept in with it.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(CREST_MARK, "Abandoned Station", "0")
                    .callsOutInLabel(0, 9)
                    .qualifiedWith("undiscovered"),
                LISTED_LEVEL);

            assertThat(readOpeningWords(row))
                .isEqualTo("Abandoned Station");
        }

        @Test
        void buildListedRowIndentsWhatWasFoundUnderALineInThePlainColour() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST_MARK, "Ion Storm", "17"),
                MEMBER_LEVEL);

            assertThat(readLabelTextRun(row, MARKED_LABEL_RUN).colour())
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
                NESTED_MEMBER_LEVEL);

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
                    .createLine(CREST_MARK, "Ion Storm", "42")
                    .derivesValueFrom("0.25 /"),
                LISTED_LEVEL);

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
                MEMBER_LEVEL);

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
                MEMBER_LEVEL);

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("8", TEXT)));
        }

        @Test
        void buildListedRowChargesNoColumnForAnAbsentScoreBeneathALine() {
            // A line with nothing to count fills its value slot with a run that draws nothing, so the
            // value column collapses for it rather than the line claiming a width it cannot use.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Decivilised", CellTooltipRows.NO_SCORE),
                MEMBER_LEVEL);

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
                    .createLine(CREST_MARK, "Ion Storm", CellTooltipRows.NO_SCORE)
                    .qualifiedWith("worsening"),
                MEMBER_LEVEL);

            assertThat(readLabelTextRun(row, MARKED_LABEL_RUN).colour())
                .isEqualTo(TEXT);
            assertThat(readLabelTextRun(row, MARKED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("worsening", HIGHLIGHT));
            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildListedRowQuietensTheNameOfAnAside() {
            // An aside stating how a number above it was arrived at is not one of the things the block
            // lists, so it reads in the shade a value's working does - name and all - and only the
            // number it arrives at stays a finding.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(null, "Same-faction market bonus", "+2")
                    .derivesValueFrom("(3 markets) - 1 =")
                    .readsAsAside(),
                MEMBER_LEVEL);

            assertThat(readLabelTextRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("Same-faction market bonus", GRAY));
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.TextRuns(List.of(
                    new TextSpan("(3 markets) - 1 =", GRAY),
                    new TextSpan("+2", TEXT))));
        }

        @Test
        void buildListedRowQuietensANumberAnAccountRecordedRatherThanOneTheLineAchieved() {
            // Only the number moves. The line is one of the things the block lists, so its name reads
            // as loudly as its neighbours' - while the nought beside it, drawn in their colour, would
            // invite a comparison with the very scores it took no part in.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine
                    .createLine(null, "Tigra City", "0")
                    .statesUncountedValue(),
                MEMBER_LEVEL);

            assertThat(readLabelTextRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("Tigra City", TEXT));
            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
        }

        @Test
        void buildListedRowSpeaksInTheBoxsOwnVoiceForOneOfTheBlocksOwnLines() {

            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200"),
                LISTED_LEVEL);

            assertThat(row.subordinationLevel())
                .isEqualTo(NO_SUBORDINATION);
        }

        @Test
        void buildListedRowSetsAPeerInWithoutQuietingIt() {
            // The distinction the level exists for, read off the drawn line: a faction inside the alliance
            // naming it is inset beneath it while still saying who holds the system, so it is stepped
            // in without being demoted.
            var row = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "900"),
                PEER_LEVEL);

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
                MEMBER_LEVEL);
            var tier = (TooltipRow.TableRow) CellTooltipRows.buildListedRow(
                CellTooltipEntryLine.createLine(null, "Light patrol", "3"),
                NESTED_MEMBER_LEVEL);

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
        void buildQualifierSpanCarriesTheWordsAlone() {
            // What parts the run from the line it continues is the run vocabulary's own space, spent
            // whichever way the label is laid, so a separator written in here would be the second one -
            // which is what drew a qualifier two spaces clear of the words it qualifies.
            assertThat(CellTooltipRows.buildQualifierSpan("hidden").text())
                .isEqualTo("hidden");
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
            // The crest rides inside the label, so the line centres crest and words together instead of
            // anchoring the image to a column a centred line has left - and it reads the same way the
            // listed lines below it do, which is the one rule the box has for images.
            var row = CellTooltipRows.buildBannerRow(CREST, "Ion Storm");

            assertThat(readLabelRun(row, MARK_RUN))
                .isEqualTo(new ImageSpan(CREST));
            assertThat(readLabelRun(row, MARKED_LABEL_RUN))
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

            assertThat(readLabelRun(row, MARKED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("worsening", HIGHLIGHT));
        }
    }
}
