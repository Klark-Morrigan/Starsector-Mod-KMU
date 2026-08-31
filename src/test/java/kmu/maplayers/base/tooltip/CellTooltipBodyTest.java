package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NESTED_MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_SUBORDINATION;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.ONE_LEVEL_SUBORDINATED;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.MARKET_STATS;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.SYSTEM_COMPOSITION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins how a body divides into blocks and how a block lays out what it lists, since both are exactly
 * what a reader of the box sees: a heading stands in gold above the entries it names, an entry sits flush
 * with whatever it is made up of inset beneath it, a line speaking for the whole system stands as a block
 * of its own, and a block with nothing to list contributes nothing rather than leaving its heading
 * standing over an absence a player would read as a failure to resolve one.
 *
 * <p>How far apart the blocks then stand is the widget's and pinned there; what is fixed here is that a
 * heading and the entries it names are one block, which is what that spacing follows from.
 *
 * <p>And how far into a block the detail level the body was opened at reads, which is the same layout
 * over one tree read to four depths: each level admits one more tier, a line gathered as a peer
 * survives the shallowest, and a block never loses the entries it lists however little is asked for.
 */
final class CellTooltipBodyTest {

    private static final String CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored(CREST);

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

            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Contested by:",
                List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(readLabelTexts(body))
                .containsExactly("Contested by:", "The Hegemony", "Tri-Tachyon");
        }

        @Test
        void appendSectionHoldsTheHeadingAndItsEntriesAsOneBlock() {
            // The heading belongs with what it names: parted from its own entries it would read as a
            // line of the block above, which is the only thing that could tell a reader whose heading
            // it is.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Contested by:",
                List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(body.readSections())
                .hasSize(1);
            assertThat(body.readSections().get(0).readRowsInOrder())
                .hasSize(3);
        }

        @Test
        void appendSectionLeavesTheBodyUntouchedForABlockWithNothingToList() {
            // The rule the whole class exists for: a heading over nothing tells the player a block
            // failed to fill, when in truth there was nothing to put in it.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Claim:", List.of(createEntry("Pirates")));
            body.appendSection("Contested by:", List.of());

            assertThat(readLabelTexts(body))
                .containsExactly("Claim:", "Pirates");
        }

        @Test
        void appendSectionAddsItsBlockBeneathWhateverTheBodyAlreadyHolds() {
            // Blocks read in the order they are appended, which is what leaves a body's running order
            // stated by its own calls rather than by a rule inside this one.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Claim:", List.of(createEntry("Pirates")));
            body.appendSection("Contested by:", List.of(createEntry("The Hegemony")));

            assertThat(readLabelTexts(body))
                .containsExactly("Claim:", "Pirates", "Contested by:", "The Hegemony");
        }

        @Test
        void appendSectionLaysTheHeadingAtTheBoxsContentEdgeInGold() {
            // A heading opens where every other line of the box does, so what stops it reading as one of
            // the entries it names is the gold it speaks in and the two columns it leaves empty.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Contested by:", List.of(createEntry("The Hegemony")));

            var heading = (TooltipRow.TableRow) readRow(body, HEADING_ROW);

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
            // heading that names it, its mark rides at the head of its own label, and its number reads in
            // the called-out shade like every value in the box.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Claim:",
                List.of(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200"))));

            var entry = (TooltipRow.TableRow) readRow(body, FIRST_ENTRY_ROW);

            assertThat(readLabelRun(entry, MARK_RUN))
                .isEqualTo(new ImageSpan(CREST));
            assertThat(readLabelRun(entry, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(entry.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(entry.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(entry.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void appendSectionInsetsWhatAnEntryIsMadeUpOfBeneathIt() {
            // The tiers the box has, read off the indent and the plainer colour rather than off any
            // label saying which is which - and the entries themselves stay flush, so a block of them
            // does not read as a list nested under its own heading.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var memberRow = 2;

            body.appendSection(
                "Dominated by:",
                List.of(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(null, "Rebel Pact", "1,200"))
                    .nesting(List.of(CellTooltipEntry.createEntry(
                        CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "900"))))));

            assertThat(readLabelTexts(body))
                .containsExactly("Dominated by:", "Rebel Pact", "The Hegemony");

            var member = (TooltipRow.TableRow) readRow(body, memberRow);

            assertThat(readLabelRun(member, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
            assertThat(member.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(((TooltipRow.TableRow) readRow(body, FIRST_ENTRY_ROW)).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void appendSectionReadsEachBreakdownUnderTheThingItBreaksDown() {
            // Depth-first is what makes a listing readable: a market's own factors follow that market
            // rather than being gathered after every market in the block, so the reader never has to
            // carry which line a run of factors belongs to.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Dominated by:",
                List.of(
                    createEntry("Chicomoztoc")
                        .nesting(List.of(createEntry("Size"), createEntry("Patrols"))),
                    createEntry("Kazeron")
                        .nesting(List.of(createEntry("Station")))));

            // The first market's factors read in the order it lists them and are done before the next
            // market opens, so the run under a line never spills past the thing it belongs to.
            assertThat(readLabelTexts(body))
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
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var childRow = 2;
            var grandchildRow = 3;

            body.appendSection(
                "Dominated by:",
                List.of(createEntry("Chicomoztoc")
                    .nesting(List.of(createEntry("Patrols")
                        .nesting(List.of(createEntry("Light patrol")))))));

            assertThat(readLabelTexts(body))
                .containsExactly("Dominated by:", "Chicomoztoc", "Patrols", "Light patrol");

            assertThat(((TooltipRow.TableRow) readRow(body, FIRST_ENTRY_ROW)).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(((TooltipRow.TableRow) readRow(body, childRow)).indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(((TooltipRow.TableRow) readRow(body, grandchildRow)).indent())
                .isCloseTo(NESTED_MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void appendSectionSetsAGatheredPeerInWithoutQuietingIt() {
            // An alliance and the factions inside it are one answer at two granularities, so the members
            // read inset beneath it while still speaking as loudly - nothing has been broken down yet.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var memberRow = 2;

            body.appendSection(
                "Dominated by:",
                List.of(createEntry("Rebel Pact")
                    .grouping(List.of(createEntry("The Hegemony")))));

            var member = (TooltipRow.TableRow) readRow(body, memberRow);

            assertThat(member.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(member.subordinationLevel())
                .isEqualTo(NO_SUBORDINATION);
        }

        @Test
        void appendSectionQuietensWhatAnEntryBreaksDownInto() {
            // The other relation, through the same walk: a market beneath the faction holding it is the
            // box accounting for that faction's line rather than restating it.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var marketRow = 2;

            body.appendSection(
                "Dominated by:",
                List.of(createEntry("The Hegemony")
                    .nesting(List.of(createEntry("Chicomoztoc")))));

            assertThat(((TooltipRow.TableRow) readRow(body, marketRow)).subordinationLevel())
                .isEqualTo(ONE_LEVEL_SUBORDINATED);
        }

        @Test
        void appendSectionPutsAGatheredLinesAccountWhereAnUngatheredOnesLands() {
            // The consistency the whole split exists for: a market under a faction inside an alliance
            // and a market under a lone faction are the same kind of statement, so they read at the
            // same volume however many levels of grouping stand above them.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var alliedMarketRow = 3;
            var loneMarketRow = 5;

            body.appendSection(
                "Dominated by:",
                List.of(
                    createEntry("Rebel Pact")
                        .grouping(List.of(createEntry("The Hegemony")
                            .nesting(List.of(createEntry("Chicomoztoc"))))),
                    createEntry("Tri-Tachyon")
                        .nesting(List.of(createEntry("Culann")))));

            assertThat(readLabelTexts(body))
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "The Hegemony",
                    "Chicomoztoc",
                    "Tri-Tachyon",
                    "Culann");

            var alliedMarket = (TooltipRow.TableRow) readRow(body, alliedMarketRow);
            var loneMarket = (TooltipRow.TableRow) readRow(body, loneMarketRow);

            assertThat(alliedMarket.subordinationLevel())
                .isEqualTo(ONE_LEVEL_SUBORDINATED);
            assertThat(loneMarket.subordinationLevel())
                .isEqualTo(ONE_LEVEL_SUBORDINATED);

            // The indent still follows where each landed, which is what makes the two numbers worth
            // carrying apart: the allied market sits a level further in for the alliance above it.
            assertThat(alliedMarket.indent())
                .isCloseTo(NESTED_MEMBER_INDENT, within(TOLERANCE));
            assertThat(loneMarket.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void appendSectionLeavesTheLeadingSlotUnfilledForAnEntryCarryingNoMark() {
            // A list of things that carry no mark - industries, conditions, hazards - lays through the
            // same construct: no line of the box fills its leading slot, so the box reserves no column
            // for one whatever a block happens to list.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Claim:", List.of(createEntry("None")));

            assertThat(((TooltipRow.TableRow) readRow(body, FIRST_ENTRY_ROW))
                    .labelledRow()
                    .leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void appendSectionOpensABlockListingNothingMarkedAtTheContentEdge() {
            // A block listing nothing marked opens flush under its own heading rather than behind a
            // gutter none of its lines could fill - a claim of "None" would otherwise read as indented
            // under the very heading naming it.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Claim:",
                List.of(createEntry("None").nesting(List.of(createEntry("Uncontested")))));

            assertThat(readLabelPlacements(body))
                .containsOnly(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void appendSectionKeepsEveryLineAtTheContentEdgeWhenABreakdownCarriesAMark() {
            // A mark found deep in a listing moves no line: it rides in the label of the line carrying
            // it, so the markless lines above it are not pushed past a gutter they could not fill.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Dominated by:",
                List.of(createEntry("Rebel Pact")
                    .nesting(List.of(CellTooltipEntry.createEntry(
                        CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "900"))))));

            assertThat(readLabelPlacements(body))
                .containsOnly(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void appendSectionStartsAMarkedAndAMarklessEntryAtTheSameInset() {
            // The other half of the same rule: two entries of one block begin their labels at the same
            // place whether either carries a mark, so a block mixing the two reads as one column rather
            // than as two staggered ones.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var marklessEntryRow = 2;

            body.appendSection(
                "Contested by:",
                List.of(
                    CellTooltipEntry.createEntry(
                        CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200")),
                    createEntry("Independent")));

            var markedEntry = (TooltipRow.TableRow) readRow(body, FIRST_ENTRY_ROW);
            var marklessEntry = (TooltipRow.TableRow) readRow(body, marklessEntryRow);

            assertThat(marklessEntry.labelPlacement())
                .isEqualTo(markedEntry.labelPlacement());
            assertThat(marklessEntry.indent())
                .isCloseTo(markedEntry.indent(), within(TOLERANCE));
        }

        @Test
        void appendSectionChargesNoValueColumnForAnEntryCountedInNothing() {
            // A line with nothing to count fills its value slot with a run that draws nothing, so the
            // column collapses for it rather than the line claiming a width it cannot use.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Claim:", List.of(createEntry("None")));

            assertThat(((TooltipRow.TableRow) readRow(body, FIRST_ENTRY_ROW))
                    .labelledRow()
                    .trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(HIGHLIGHT)));
        }

        @Test
        void appendSectionNestsEachEntryAsABlockUnderTheHeading() {
            // What the box spaces by. Laid as one flat run, an entry that broke down into an account of
            // its own could not be set apart from the next entry at its tier - the widget would have no
            // way to tell the last line of one from the first line of another.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Contested by:",
                List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(body.readSections().get(0).openingRows())
                .hasSize(1);
            assertThat(body.readSections().get(0).members())
                .hasSize(2);
        }

        @Test
        void appendSectionNestsWhatAnEntryBreaksDownIntoBeneathThatEntry() {
            // The nesting goes as deep as the listing does, so a colony's terms are part of the colony
            // and not of the faction above it - which is what stops a parting landing inside a
            // breakdown.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection(
                "Dominated by:",
                List.of(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(
                        null,
                        "The Hegemony",
                        CellTooltipRows.NO_SCORE))
                    .nesting(List.of(CellTooltipEntry
                        .createEntry(CellTooltipEntryLine.createLine(
                            null,
                            "Jangala",
                            CellTooltipRows.NO_SCORE))
                        .nesting(List.of(createEntry("Size")))))));

            var factionSection = body.readSections().get(0).members().get(0);
            var marketSection = factionSection.members().get(0);

            assertThat(factionSection.countLines())
                .isEqualTo(3);
            assertThat(marketSection.countLines())
                .isEqualTo(2);
        }

        @Test
        void appendSectionKeepsAGatheredPeerAtTheShallowestLevel() {
            // The cut the whole level reads for: an alliance and the factions inside it are one answer
            // at two granularities, so the shallowest level - the one that exists to state who holds the
            // system - shows both. Dropping them would leave the alliances view stating nothing.
            var body = CellTooltipBody.openBody(FACTIONS);

            body.appendSection(
                "Dominated by:",
                List.of(createEntry("Rebel Pact")
                    .grouping(List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")))));

            assertThat(readLabelTexts(body))
                .containsExactly("Dominated by:", "Rebel Pact", "The Hegemony", "Tri-Tachyon");
        }

        @Test
        void appendSectionDropsWhatAnEntryBreaksDownIntoAtTheShallowestLevel() {
            // The other relation at the same indent, cut the other way: the markets a faction holds the
            // system with are the account of its line, which is what the next level up buys.
            var body = CellTooltipBody.openBody(FACTIONS);

            body.appendSection(
                "Dominated by:",
                List.of(createEntry("The Hegemony")
                    .nesting(List.of(createEntry("Chicomoztoc")))));

            assertThat(readLabelTexts(body))
                .containsExactly("Dominated by:", "The Hegemony");
        }

        @Test
        void appendSectionAdmitsOneMoreTierPerLevel() {
            // What the levels are: one tree read to four depths, each level adding the tier beneath the
            // one before it. Asserted over the one listing, so a level that admitted the wrong tier
            // fails here rather than agreeing with a listing shaped to suit it.
            assertThat(readLabelTexts(drawAccountedFaction(FACTIONS)))
                .containsExactly("Dominated by:", "The Hegemony");
            assertThat(readLabelTexts(drawAccountedFaction(SYSTEM_COMPOSITION)))
                .containsExactly("Dominated by:", "The Hegemony", "Chicomoztoc");
            assertThat(readLabelTexts(drawAccountedFaction(MARKET_STATS)))
                .containsExactly("Dominated by:", "The Hegemony", "Chicomoztoc", "Patrols");
            assertThat(readLabelTexts(drawAccountedFaction(PATROL_DETAILS)))
                .containsExactly(
                    "Dominated by:",
                    "The Hegemony",
                    "Chicomoztoc",
                    "Patrols",
                    "Small");
        }

        @Test
        void appendSectionKeepsABlockWhoseEntriesAreAllTheLevelAdmits() {
            // A cut reaches only what an entry carries, never the entries themselves - so a block still
            // opens with its heading at every level rather than being emptied into nothing by a shallow
            // one, which is what would leave the box silent about a system it does hold findings on.
            var body = CellTooltipBody.openBody(FACTIONS);

            body.appendSection(
                "Contested by:",
                List.of(createEntry("The Hegemony")
                    .nesting(List.of(createEntry("Chicomoztoc")))));

            assertThat(body.readSections())
                .hasSize(1);
        }
    }

    @Nested
    class AppendBannerSection {

        @Test
        void appendBannerSectionGivesTheLineABlockOfItsOwn() {
            // A banner speaks for the system rather than opening a list, so it is parted from whatever
            // follows instead of being read as that block's first entry.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);
            var bannerRow = CellTooltipRows.buildBannerRow(null, "Unpopulated");

            body.appendBannerSection(Optional.of(bannerRow));

            assertThat(body.readSections())
                .hasSize(1);
            assertThat(body.readSections().get(0).readRowsInOrder())
                .containsExactly(bannerRow);
        }

        @Test
        void appendBannerSectionLeavesTheBodyUntouchedWhenThereIsNothingToState() {
            // The absence rule the helper exists to hold: a system with nothing to state gets no empty
            // block, which would part the body around a gap holding no line.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendBannerSection(Optional.empty());

            assertThat(body.readSections())
                .isEmpty();
        }

        @Test
        void appendBannerSectionAddsItsBlockBeneathWhateverTheBodyAlreadyHolds() {

            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Claim:", List.of(createEntry("Pirates")));
            body.appendBannerSection(
                Optional.of(CellTooltipRows.buildBannerRow(null, "Decivilised")));

            assertThat(readLabelTexts(body))
                .containsExactly("Claim:", "Pirates", "Decivilised");
        }
    }

    // One block of a faction accounted for as deep as the boxes go - the markets it holds the system
    // with, a term of one of those, and a tier of that term - laid out at the level asked for. One
    // listing behind every level, since what the levels are is four readings of a single tree.
    private static CellTooltipBody drawAccountedFaction(HoverTooltipDetailLevel detailLevel) {

        var body = CellTooltipBody.openBody(detailLevel);

        body.appendSection(
            "Dominated by:",
            List.of(createEntry("The Hegemony")
                .nesting(List.of(createEntry("Chicomoztoc")
                    .nesting(List.of(createEntry("Patrols")
                        .nesting(List.of(createEntry("Small")))))))));

        return body;
    }

    // One thing a block lists, told apart from its siblings by its name alone - what it carries beyond
    // that is stated by the cases that are about it.
    private static CellTooltipEntry createEntry(String labelText) {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(null, labelText, CellTooltipRows.NO_SCORE));
    }

    // One line of the body, by its place in the flat run the box draws - the cases below are about which
    // lines a block contributes, so its own grouping is read back out rather than walked.
    private static TooltipRow readRow(CellTooltipBody body, int rowIndex) {
        return TooltipSection
            .readRowsInOrder(body.readSections())
            .get(rowIndex);
    }

    // Where every line of a body starts across the box. Read over the whole body rather than row by row,
    // since the rule under test is about the block agreeing with itself - one line asserted alone would
    // pass while the lines around it opened from another column.
    private static List<TooltipLabelPlacement> readLabelPlacements(CellTooltipBody body) {
        return TooltipSection
            .readRowsInOrder(body.readSections())
            .stream()
            .map(row -> ((TooltipRow.TableRow) row).labelPlacement())
            .toList();
    }

    // What each line of a body says, in draw order. Read as the line's opening words rather than as its
    // first run, since a line led by a mark opens on an image - so one expected list covers a block
    // mixing marked lines with markless ones.
    private static List<String> readLabelTexts(CellTooltipBody body) {
        return TooltipSection
            .readRowsInOrder(body.readSections())
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }
}
