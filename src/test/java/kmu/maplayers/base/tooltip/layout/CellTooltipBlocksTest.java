package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.ONE_LEVEL_SUBORDINATED;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readTableRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins what a body does when the box has less room than its content needs: it draws the head of every
 * listing and closes each one it cut short with a row saying what stands behind it.
 *
 * <p>The rule that matters is which end goes. Every listing in the box is ranked, so the tail is the
 * low-scoring end and the entries a reader looks for first are the ones that survive - and the first
 * of them always does, since a heading over nothing reads as a block that failed to fill rather than
 * as a box short of room.
 *
 * <p>And that the allowance is spent at every depth. A box runs long by depth as much as by breadth,
 * so an allowance reaching only the blocks' own entries would drop whole factions while leaving every
 * term of the one that survived.
 *
 * <p>How a line inside a block reads is pinned where the vocabulary is ({@link CellTooltipRowsTest});
 * how a body divides into blocks is pinned where it is filled ({@link CellTooltipBodyTest}).
 */
final class CellTooltipBlocksTest {

    // A box with room for everything, and one with room for a single entry per listing. Named rather
    // than passed as bare numbers, since what an allowance means is exactly what these cases are
    // about.
    private static final int ROOM_FOR_TWO = 2;
    private static final int ROOM_FOR_ONE = 1;
    private static final int ROOM_FOR_NONE = 0;

    // Where the row standing for a cut tail lands in the flat run of lines: after a block's heading and
    // the one entry it had room for, and a line further down where the cut fell inside an account.
    private static final int WITHHELD_ROW_IN_A_BLOCK = 2;
    private static final int WITHHELD_ROW_IN_AN_ACCOUNT = 3;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ReadSections {

        @Test
        void readSectionsDrawsEveryEntryTheLayerListed() {
            // The ordinary read, and what nearly every box gets: nothing is stood for, so no row about
            // the box's own account appears among the things it lists.
            var blocks = buildBlocks(PATROL_DETAILS, "Chicomoztoc", "Kazeron", "Sindria");

            assertThat(readSectionOpeningWords(blocks.readSections()))
                .containsExactly("Contested by:", "Chicomoztoc", "Kazeron", "Sindria");
        }
    }

    @Nested
    class ReadBodyWithin {

        @Test
        void readBodyWithinDropsTheTailOfAListingItCannotDrawWhole() {
            // The tail is the low-scoring end, every listing in the box being ranked - so what a reader
            // came for survives and what goes is what they would have read last.
            var body = buildBlocks(PATROL_DETAILS, "Chicomoztoc", "Kazeron", "Sindria")
                .readBodyWithin(ROOM_FOR_TWO);

            assertThat(readSectionOpeningWords(body.sections()))
                .containsExactly("Contested by:", "Chicomoztoc", "Kazeron", "+ 1 more");
        }

        @Test
        void readBodyWithinKeepsTheFirstEntryOfAListingHoweverLittleRoomThereIs() {
            // Below one entry a heading stands over nothing, which reads as a block whose contents
            // failed to resolve - a different and untrue statement from "there was no room".
            var body = buildBlocks(PATROL_DETAILS, "Chicomoztoc", "Kazeron", "Sindria")
                .readBodyWithin(ROOM_FOR_NONE);

            assertThat(readSectionOpeningWords(body.sections()))
                .containsExactly("Contested by:", "Chicomoztoc", "+ 2 more");
        }

        @Test
        void readBodyWithinStatesWhatTheEntriesItDroppedCameTo() {
            // The row closes the arithmetic the listing opened: the figure is the sum of exactly the
            // entries standing behind it, so the visible rows and this one still account for the
            // number the box states above them.
            var body = buildBlocks(PATROL_DETAILS, "Chicomoztoc", "Kazeron", "Sindria")
                .readBodyWithin(ROOM_FOR_ONE);

            assertThat(readValueText(body.sections(), WITHHELD_ROW_IN_A_BLOCK))
                .isEqualTo("1,300");
        }

        @Test
        void readBodyWithinCountsEveryEntryItLeftOutAcrossTheBody() {
            // One figure for the whole box, which is what the line at its foot states - so a reader
            // can tell a short list from a cut one wherever the cut happened to land.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Dominated by:", List.of(
                createEntry("The Hegemony", 900),
                createEntry("Tri-Tachyon", 800)));
            body.appendSection("Contested by:", List.of(
                createEntry("Sindria", 700),
                createEntry("Kazeron", 600),
                createEntry("Chicomoztoc", 500)));

            assertThat(body.readBlocks().readBodyWithin(ROOM_FOR_ONE).withheldEntryCount())
                .isEqualTo(3);
        }

        @Test
        void readBodyWithinWithholdsNothingWhereTheBodyFitsAsItIs() {
            // A box that was never the problem must not grow a row about its own account.
            var body = buildBlocks(PATROL_DETAILS, "Chicomoztoc", "Kazeron")
                .readBodyWithin(ROOM_FOR_TWO);

            assertThat(body.withheldEntryCount())
                .isZero();
            assertThat(readSectionOpeningWords(body.sections()))
                .containsExactly("Contested by:", "Chicomoztoc", "Kazeron");
        }

        @Test
        void readBodyWithinSpendsTheAllowanceAtEveryDepth() {
            // A box runs long by depth as much as by breadth - a colony under each faction, a term
            // under each colony - so the same number takes the tail off whichever listings are long,
            // wherever they sit.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Dominated by:", List.of(createEntry("The Hegemony", 900)
                .nesting(List.of(
                    createEntry("Chicomoztoc", 500),
                    createEntry("Kazeron", 400)))));

            assertThat(readSectionOpeningWords(
                    body.readBlocks().readBodyWithin(ROOM_FOR_ONE).sections()))
                .containsExactly("Dominated by:", "The Hegemony", "Chicomoztoc", "+ 1 more");
        }

        @Test
        void readBodyWithinLaysTheStandingInRowWhereTheEntriesItStandsForWouldHaveBeen() {
            // It closes their listing, so it reads as the last of them rather than as a line the box
            // states in its own voice - which at the wrong depth is exactly how it would read.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Dominated by:", List.of(createEntry("The Hegemony", 900)
                .nesting(List.of(
                    createEntry("Chicomoztoc", 500),
                    createEntry("Kazeron", 400)))));

            var withheldRow = readTableRow(
                TooltipSection.readRowsInOrder(
                    body.readBlocks().readBodyWithin(ROOM_FOR_ONE).sections()),
                WITHHELD_ROW_IN_AN_ACCOUNT);

            assertThat(withheldRow.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(withheldRow.subordinationLevel())
                .isEqualTo(ONE_LEVEL_SUBORDINATED);
        }

        @Test
        void readBodyWithinLeavesATierTheLevelDeclinesUnmentioned() {
            // The two cuts answer different questions. What the level leaves out is the player's own
            // standing choice and the hint at the foot already offers it back; what the room leaves out
            // is the box's doing and is stated. A tier the level declined must not be reported as
            // withheld.
            var body = CellTooltipBody.openBody(FACTIONS);

            body.appendSection("Dominated by:", List.of(createEntry("The Hegemony", 900)
                .nesting(List.of(
                    createEntry("Chicomoztoc", 500),
                    createEntry("Kazeron", 400)))));

            var drawnBody = body.readBlocks().readBodyWithin(CellTooltipBlocks.NO_ENTRY_LIMIT);

            assertThat(readSectionOpeningWords(drawnBody.sections()))
                .containsExactly("Dominated by:", "The Hegemony");
            assertThat(drawnBody.withheldEntryCount())
                .isZero();
        }

        @Test
        void readBodyWithinDrawsTheBlocksTheBodyHeldWhenItWasReadRatherThanWhatItHoldsNow() {
            // A layer composes into a body it goes on using - the same instance serves every hover -
            // so a value that kept the body's own list would reshape a box already measured and drawn.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Contested by:", List.of(createEntry("Chicomoztoc", 500)));

            var blocks = body.readBlocks();

            body.appendSection("Dominated by:", List.of(createEntry("The Hegemony", 900)));

            assertThat(blocks.readSections())
                .hasSize(1);
        }
    }

    @Nested
    class CountLongestListing {

        @Test
        void countLongestListingAnswersTheLongestListingAtAnyDepth() {
            // What bounds the search for an allowance: above this, no listing has a tail to take off,
            // so a larger allowance draws the very body an unbounded one does.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendSection("Dominated by:", List.of(createEntry("The Hegemony", 900)
                .nesting(List.of(
                    createEntry("Chicomoztoc", 500),
                    createEntry("Kazeron", 400),
                    createEntry("Sindria", 300)))));

            assertThat(body.readBlocks().countLongestListing())
                .isEqualTo(3);
        }

        @Test
        void countLongestListingPassesOverATierTheLevelDeclines() {
            // A tier that is never drawn cannot make the box tall, so its length has no say in how far
            // the allowance has to come down.
            var body = CellTooltipBody.openBody(FACTIONS);

            body.appendSection("Dominated by:", List.of(createEntry("The Hegemony", 900)
                .nesting(List.of(
                    createEntry("Chicomoztoc", 500),
                    createEntry("Kazeron", 400),
                    createEntry("Sindria", 300)))));

            assertThat(body.readBlocks().countLongestListing())
                .isEqualTo(1);
        }

        @Test
        void countLongestListingAnswersNothingForABodyThatListsNothing() {
            // A body of banners alone has no tail to take off however little room the box has.
            var body = CellTooltipBody.openBody(PATROL_DETAILS);

            body.appendBannerSection(
                Optional.of(CellTooltipRows.buildBannerRow(null, "Unpopulated")));

            assertThat(body.readBlocks().countLongestListing())
                .isZero();
        }
    }

    // One block of ranked entries, strongest first, at descending weights - so a case reading what the
    // stand-in row states is reading a sum of the entries actually dropped.
    private static CellTooltipBlocks buildBlocks(
            HoverTooltipDetailLevel detailLevel,
            String... labelTexts) {

        var body = CellTooltipBody.openBody(detailLevel);
        var entries = new ArrayList<CellTooltipEntry>();
        var countedValue = 800;

        for (var labelText : labelTexts) {
            entries.add(createEntry(labelText, countedValue));
            countedValue -= 100;
        }
        body.appendSection("Contested by:", entries);

        return body.readBlocks();
    }

    // One thing a block lists, carrying the number its block counts it in.
    private static CellTooltipEntry createEntry(String labelText, int countedValue) {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createCountedLine(CellTooltipMark.NO_MARK, labelText, countedValue));
    }

    // What one line of a drawn body states in its value column.
    private static String readValueText(List<TooltipSection> sections, int rowIndex) {

        var rowSlot = (RowSlot.Text) readTableRow(TooltipSection.readRowsInOrder(sections), rowIndex)
            .labelledRow()
            .trailingRowSlot();

        return rowSlot.textSpan().text();
    }
}
