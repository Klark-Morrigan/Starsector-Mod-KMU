package kmu.maplayers.base.chrome.arrange;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the arithmetic the box is laid out by, which is everything about the dialog that can be
 * checked without a running game.
 *
 * <p>Held as relationships rather than as a table of the numbers it was built from. What matters is
 * that the column butts against the head, that a row sits exactly one row-height below the one above
 * it, and that the box is as tall as everything standing in it - a case restating each constant
 * would pass whatever the constants were, and fail on every deliberate change.
 */
final class ArrangementBoxLayoutTest {

    // Where a cell placed at the given x actually draws what it holds. Every assertion about an inset
    // reads this rather than the placement, the placement being a number nobody sees: the game sets an
    // element's contents in from its own edge, so a cell and its contents have different left edges.
    private static float resolveDrawnLeft(float cellLeft) {

        return cellLeft + ArrangementBoxLayout.VANILLA_ELEMENT_CONTENT_INSET;
    }

    @Nested
    class ResolveBoxHeight {

        @Test
        void resolveBoxHeightGrowsByOneRowHeightPerRow() {

            var oneRowHeight = ArrangementBoxLayout.resolveBoxHeight(1);
            var twoRowHeight = ArrangementBoxLayout.resolveBoxHeight(2);

            assertThat(twoRowHeight - oneRowHeight)
                .isEqualTo(28f);
        }

        @Test
        void resolveBoxHeightHoldsTheHeadTheColumnAndTheFootWithAPadAtEitherEnd() {
            // An empty column, so what is left is the furniture: 12 of pad twice, an 84-tall head and
            // a 34-tall foot.
            assertThat(ArrangementBoxLayout.resolveBoxHeight(0))
                .isEqualTo(142f);
        }
    }

    @Nested
    class ResolveRowTop {

        @Test
        void resolveRowTopStartsTheColumnWhereTheHeadEnds() {
            // The head stands 12 down from the box's top and is 84 tall, so the leading row butts
            // against it rather than against a gap nothing states.
            assertThat(ArrangementBoxLayout.resolveRowTop(0))
                .isEqualTo(96f);
        }

        @Test
        void resolveRowTopSetsEachRowOneRowHeightBelowTheOneAboveIt() {

            assertThat(ArrangementBoxLayout.resolveRowTop(3) - ArrangementBoxLayout.resolveRowTop(2))
                .isEqualTo(28f);
        }
    }

    @Nested
    class ResolveHeaderWidth {

        @Test
        void resolveHeaderWidthLeavesAPadAtEitherSideOfTheBox() {

            assertThat(ArrangementBoxLayout.BOX_WIDTH - ArrangementBoxLayout.resolveHeaderWidth())
                .isEqualTo(24f);
        }
    }

    @Nested
    class ResolveLeadingCellLeft {

        @Test
        void resolveLeadingCellLeftDrawsACellsContentsAPadInFromTheBoxsLeftEdge() {
            // The cell goes 7 in and the element draws 5 further, so what the player reads starts at
            // the pad rather than past it.
            assertThat(resolveDrawnLeft(ArrangementBoxLayout.resolveLeadingCellLeft()))
                .isEqualTo(12f);
        }
    }

    @Nested
    class ResolveTrailingCellLeft {

        @Test
        void resolveTrailingCellLeftEndsACellsContentsAtTheSamePadALeadingCellsBeginAt() {
            // The one thing this step is for: both edges land on the one pad, for a cell of any width,
            // so a widening control moves the cell rather than the gap around the box's contents.
            var leadingLeftInset = resolveDrawnLeft(ArrangementBoxLayout.resolveLeadingCellLeft());
            var trailingRightInset = ArrangementBoxLayout.BOX_WIDTH
                - (resolveDrawnLeft(ArrangementBoxLayout.resolveTrailingCellLeft(60f)) + 60f);

            assertThat(leadingLeftInset).isEqualTo(12f);
            assertThat(trailingRightInset).isEqualTo(12f);
        }
    }

    @Nested
    class ResolveControlsCellLeft {

        @Test
        void resolveControlsCellLeftStandsTheControlsAChannelBeyondTheLabelColumn() {
            // The name is drawn at 12 across a 200-wide column, so an 8-unit channel puts the first
            // control at 220.
            assertThat(resolveDrawnLeft(ArrangementBoxLayout.resolveControlsCellLeft()))
                .isEqualTo(220f);
        }

        @Test
        void resolveControlsCellLeftLeavesTheDrawnControlsEndingAPadInFromTheBoxEdge() {

            var controlsRightEdge = resolveDrawnLeft(ArrangementBoxLayout.resolveControlsCellLeft())
                + ArrangementBoxLayout.CONTROLS_WIDTH;

            assertThat(ArrangementBoxLayout.BOX_WIDTH - controlsRightEdge)
                .isEqualTo(12f);
        }
    }

    @Nested
    class ResolveFooterLeft {

        @Test
        void resolveFooterLeftStandsTheWayOutAPadInFromTheBoxsRightEdge() {

            var footerRightEdge = resolveDrawnLeft(ArrangementBoxLayout.resolveFooterLeft())
                + ArrangementBoxLayout.APPLY_BUTTON_WIDTH;

            assertThat(ArrangementBoxLayout.BOX_WIDTH - footerRightEdge)
                .isEqualTo(12f);
        }
    }

    @Nested
    class ResolveFooterTop {

        @Test
        void resolveFooterTopStandsTheFootAtTheBottomOfWhateverBoxItIsIn() {
            // A 34-tall foot in a 300-tall box.
            assertThat(ArrangementBoxLayout.resolveFooterTop(300f))
                .isEqualTo(266f);
        }

        @Test
        void resolveFooterTopLeavesThePadBetweenTheLastRowAndTheFoot() {
            // The pad at the box's bottom is what parts the column from the way out, the foot itself
            // reaching the box's lower edge.
            var boxHeight = ArrangementBoxLayout.resolveBoxHeight(3);
            var lastRowBottom =
                ArrangementBoxLayout.resolveRowTop(2) + ArrangementBoxLayout.ROW_HEIGHT;

            assertThat(ArrangementBoxLayout.resolveFooterTop(boxHeight) - lastRowBottom)
                .isEqualTo(12f);
        }
    }
}
