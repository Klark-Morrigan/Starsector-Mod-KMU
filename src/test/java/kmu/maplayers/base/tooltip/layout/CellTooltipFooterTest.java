package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.BUTTON_SHORTCUT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevelInput.CYCLE_KEY_NAME;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.MARKET_STATS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.SYSTEM_COMPOSITION;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the line a box ends on and the rule behind it: what the next press would do, stated in the
 * player's words and only where pressing would show them something the box does not already draw, and
 * what the box had no room to show.
 *
 * <p>The offer is asserted as the destination rather than as a yes or no, since that is what the key
 * handler sets the level from - and asserted over both sides of a box's own bound, the depth a box is
 * cut at being what parts a press worth offering from one that redraws what is on screen.
 *
 * <p>Where the line lands in the box, and what it is set in, is the shape's
 * ({@link SystemCellTooltipTest}); what it says is here.
 */
final class CellTooltipFooterTest {

    // What the hint says at each step, as the phrases the player reads rather than as the keys behind
    // them: the line has to name the step the next press takes, and a case reading the key instead
    // would pass over a level wired to another level's wording.
    private static final String EXPAND_SYSTEM_COMPOSITION = "expand system composition";
    private static final String COLLAPSE_TO_FACTIONS = "collapse to factions";

    // A box that drew everything it was asked for, and one that could not fit two of its entries.
    private static final int NOTHING_WITHHELD = 0;
    private static final int TWO_WITHHELD = 2;

    // The hint stands alone in its block, so it is both that block's only line and its first.
    private static final int FOOTER_ROW = 0;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ResolveOfferedLevel {

        @Test
        void resolveOfferedLevelStepsOneLevelDeeperInsideTheBound() {
            // The ordinary press: the box holds more than is being drawn, so the next level opens a
            // tier of it.
            assertThat(CellTooltipFooter.resolveOfferedLevel(FACTIONS, PATROL_DETAILS))
                .contains(SYSTEM_COMPOSITION);
        }

        @Test
        void resolveOfferedLevelCollapsesAtTheBoxsOwnBound() {
            // The wrap is the box's rather than the cycle's last constant. A box whose account ends at
            // the market stats - a claim, which no patrol enters - would otherwise be offered a patrol
            // tier that redraws exactly what is on screen.
            assertThat(CellTooltipFooter.resolveOfferedLevel(MARKET_STATS, MARKET_STATS))
                .contains(FACTIONS);
        }

        @Test
        void resolveOfferedLevelCollapsesABoxReadPastItsBound() {
            // The level is one shared fact carried across hovers, so a box is met deeper than it goes.
            // This one is still drawn cut - it holds the composition tier the collapse takes away - so
            // the press shows the player something.
            assertThat(CellTooltipFooter.resolveOfferedLevel(PATROL_DETAILS, MARKET_STATS))
                .contains(FACTIONS);
        }

        @Test
        void resolveOfferedLevelOffersNothingWhereTheBoxHoldsNothingDeeper() {
            // A box that lists nobody, read at the level it opens on: no tier to open and nothing to
            // collapse.
            assertThat(CellTooltipFooter.resolveOfferedLevel(FACTIONS, FACTIONS))
                .isEmpty();
        }

        @Test
        void resolveOfferedLevelOffersNothingWhereTheLevelHasOutrunTheBox() {
            // The same box met at a depth reached over some other system. Every level draws it the same,
            // so a collapse offered here would name a press that redraws what is on screen - and the key
            // it advertised would reset a shared level over a box that showed no sign of it.
            assertThat(CellTooltipFooter.resolveOfferedLevel(PATROL_DETAILS, FACTIONS))
                .isEmpty();
        }
    }

    @Nested
    class BuildSection {

        @Test
        void buildSectionNamesTheStepTheNextPressTakes() {
            // What the hint has to say to be worth a line: which key, and what the player would gain -
            // the key picked out and the words about it quiet, which is how the game states its own.
            assertThat(readFooterRuns(FACTIONS, PATROL_DETAILS, NOTHING_WITHHELD))
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(EXPAND_SYSTEM_COMPOSITION, GRAY));
        }

        @Test
        void buildSectionNamesTheCollapseOnceTheBoxsTreeRunsOut() {
            // The one press that takes detail away rather than adding it, worded by the level being
            // arrived at like every other step.
            assertThat(readFooterRuns(MARKET_STATS, MARKET_STATS, NOTHING_WITHHELD))
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(COLLAPSE_TO_FACTIONS, GRAY));
        }

        @Test
        void buildSectionStatesWhatTheBoxHadNoRoomToShowAfterTheOffer() {
            // Two things about the box on one line, in the order they are read: what a press would do,
            // then what this box is keeping back. The figure runs last because it speaks about the box's
            // own account rather than about the press.
            assertThat(readFooterRuns(FACTIONS, PATROL_DETAILS, TWO_WITHHELD))
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(EXPAND_SYSTEM_COMPOSITION, GRAY),
                    new TextSpan("2 not shown", GRAY));
        }

        @Test
        void buildSectionStatesWithheldContentWithoutAnOffer() {
            // A box cut for room that has nothing deeper to offer - which every level reaches, the
            // offer being withheld at all of them. The line is the same line short its first two runs
            // rather than a shape of its own, and the key is not named, nothing being claimed for it.
            assertThat(readFooterRuns(PATROL_DETAILS, FACTIONS, TWO_WITHHELD))
                .containsExactly(new TextSpan("2 not shown", GRAY));
        }

        @Test
        void buildSectionDrawsNoLineWithNothingToOfferAndNothingWithheld() {
            // Neither half applies, so there is no line - the hint is about the box rather than about
            // the system, and a box says nothing about itself when there is nothing to say.
            assertThat(CellTooltipFooter.buildSection(PATROL_DETAILS, FACTIONS, NOTHING_WITHHELD))
                .isEmpty();
        }
    }

    // The runs of the one line the footer block holds, for a box read at detailLevel whose own tree ends
    // at deepestHeldLevel and which had to withhold withheldEntryCount entries.
    private static List<TextSpan> readFooterRuns(
            HoverTooltipDetailLevel detailLevel,
            HoverTooltipDetailLevel deepestHeldLevel,
            int withheldEntryCount) {

        return CellTooltipFooter
            .buildSection(detailLevel, deepestHeldLevel, withheldEntryCount)
            .orElseThrow()
            .readRowsInOrder()
            .get(FOOTER_ROW)
            .labelRuns();
    }
}
