package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.text.TextStyle;
import kmlib.starsector.ui.widgets.tooltip.CursorTooltip;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineGaps;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.starsector.ui.widgets.tooltip.TooltipStyle;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the order a box gives things up in: size first, content last.
 *
 * <p>A tooltip takes no input, so nothing it leaves out can be scrolled back to - which makes every
 * line worth keeping at a smaller size than it is worth dropping. So a box that fits is drawn exactly
 * as it was composed, one that can be compressed into the room it has keeps all of its content, and
 * only a box still overflowing at the smallest its typography will draw gives any of that content up.
 *
 * <p>The compression itself is the widget's and pinned there; what is fixed here is that it is reached
 * for first, that the cut is reached for only past it, and that a cut box comes back inside its room.
 */
final class CellTooltipContentFitTest {

    // The look the box is authored in, standing in for the real one: two faces at their atlases' own
    // sizes, a step per level to compress along, and the gap the lines stack at. This suite's own
    // numbers rather than the shipped ones, since what is under test is the order the two answers are
    // reached in and not any particular density.
    private static final float LEVEL_SHRINK = 2f;
    private static final float LINE_GAP = 6f;

    // Budgets stated as what they mean for the box below: room enough for the whole of it, room that
    // only compression can bring it inside, and less room than compression can reach. Each case
    // asserts what its own budget should have led to, so a budget that stopped landing in the regime
    // it names fails here rather than passing quietly.
    private static final float ROOM_FOR_THE_WHOLE_BOX = 100000f;
    private static final float ROOM_FOR_A_COMPRESSED_BOX = 320f;
    private static final float ROOM_FOR_ALMOST_NOTHING = 120f;

    // The box these cases are posed over: one block listing this many things, each broken down into
    // an account of its own - deep enough to have context to compress, and long enough to have a tail
    // worth taking off.
    private static final int LISTED_ENTRY_COUNT = 8;

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
    class FitToHeight {

        @Test
        void fitToHeightHandsBackABoxThatFitsExactlyAsItWasComposed() {
            // Nearly every box. The compression inverts how a listing reads and the cut takes content
            // away, so a box that was never the problem must pay for neither.
            var fittedBox = fitWithin(ROOM_FOR_THE_WHOLE_BOX);

            assertThat(fittedBox.typography())
                .isEqualTo(buildStyle());
            assertThat(fittedBox.sections())
                .isEqualTo(assembleListing(CellTooltipBlocks.NO_ENTRY_LIMIT));
        }

        @Test
        void fitToHeightGivesUpSizeBeforeItGivesUpContent() {
            // The order that matters: a box that can be brought inside its room by drawing smaller
            // keeps every line it was asked for, and the reader loses nothing but size.
            var fittedBox = fitWithin(ROOM_FOR_A_COMPRESSED_BOX);

            assertThat(fittedBox.typography())
                .isNotEqualTo(buildStyle());
            assertThat(fittedBox.sections())
                .isEqualTo(assembleListing(CellTooltipBlocks.NO_ENTRY_LIMIT));
        }

        @Test
        void fitToHeightBringsACompressibleBoxInsideTheRoomItHas() {
            // What the compression is for: the box the reader is handed actually fits the screen.
            var fittedBox = fitWithin(ROOM_FOR_A_COMPRESSED_BOX);

            assertThat(measureHeight(fittedBox))
                .isLessThanOrEqualTo(ROOM_FOR_A_COMPRESSED_BOX);
        }

        @Test
        void fitToHeightGivesUpContentWhereNoAmountOfSizeWillDo() {
            // Past the floor the compression stops at there is nothing left to give up but content -
            // and a box drawn overflowing would lose the same lines with nothing on screen saying so.
            var fittedBox = fitWithin(ROOM_FOR_ALMOST_NOTHING);

            assertThat(countRows(fittedBox.sections()))
                .isLessThan(countRows(assembleListing(CellTooltipBlocks.NO_ENTRY_LIMIT)));
        }

        @Test
        void fitToHeightBringsACutBoxInsideTheRoomItHas() {
            // The point of cutting at all: what the reader is handed fits, and what it left out is
            // stated on the rows standing for it rather than run off the edge of the screen.
            var fittedBox = fitWithin(ROOM_FOR_ALMOST_NOTHING);

            assertThat(measureHeight(fittedBox))
                .isLessThanOrEqualTo(ROOM_FOR_ALMOST_NOTHING);
        }

        @Test
        void fitToHeightKeepsMoreThanTheLeastItCouldWhereTheRoomAllows() {
            // Every entry dropped is something the player asked to see, so the search answers with the
            // largest listing that fits rather than with the first one that does - a box cut to one
            // entry per listing where two would have fitted is answering a question it was not asked.
            var fittedBox = fitWithin(ROOM_FOR_ALMOST_NOTHING);

            assertThat(countRows(fittedBox.sections()))
                .isGreaterThan(countRows(
                    assembleListing(CellTooltipBlocks.LEAST_ENTRY_ALLOWANCE)));
        }
    }

    // The box fitted into a given amount of room, through the one call every case here is about.
    private static CellTooltipContentFit.FittedCellBox fitWithin(float heightBudget) {

        return CellTooltipContentFit.fitToHeight(
            CellTooltipContentFitTest::assembleListing,
            buildStyle(),
            heightBudget,
            LISTED_ENTRY_COUNT);
    }

    // How tall a fitted box stands, measured the way the widget stacks it.
    private static float measureHeight(CellTooltipContentFit.FittedCellBox fittedBox) {
        return CursorTooltip.measureBoxHeight(fittedBox.sections(), fittedBox.typography());
    }

    private static int countRows(List<TooltipSection> sections) {
        return TooltipSection.readRowsInOrder(sections).size();
    }

    // The body under test at one entry allowance: a block of listed things, each over an account of
    // its own, so the box has a tier standing above its deepest line for the compression to act on.
    private static List<TooltipSection> assembleListing(int entryAllowance) {

        var body = CellTooltipBody.openBody(HoverTooltipDetailLevel.PATROL_DETAILS);
        var entries = new ArrayList<CellTooltipEntry>(LISTED_ENTRY_COUNT);

        for (var index = 0; index < LISTED_ENTRY_COUNT; index++) {
            entries.add(createEntry("Colony " + index)
                .nesting(List.of(createEntry("Size"))));
        }
        body.appendSection("Contested by:", entries);

        return body.readBlocks().readBodyWithin(entryAllowance).sections();
    }

    private static CellTooltipEntry createEntry(String labelText) {
        return CellTooltipEntry.createEntry(CellTooltipEntryLine.createCountedLine(
            CellTooltipMark.NO_MARK,
            labelText,
            1000));
    }

    // The look the box is authored in. Built per call rather than held, since the cases compare the
    // fitted look against it and a shared instance would compare equal to itself whatever came back.
    private static TooltipStyle buildStyle() {
        return TooltipStyle
            .createStyle(
                TextStyle.createStyle(StarsectorFont.VANILLA_ORBITRON_20AA),
                TextStyle.createStyle(StarsectorFont.VANILLA_INSIGNIA_15))
            .shrunkPerLevel(LEVEL_SHRINK)
            .stackedAt(TooltipLineGaps.createGaps(LINE_GAP));
    }
}
