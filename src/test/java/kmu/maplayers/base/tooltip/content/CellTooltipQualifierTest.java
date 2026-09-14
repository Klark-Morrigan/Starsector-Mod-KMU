package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a status called out after a line's name is made of: a finding it always states, and the
 * word and mark introducing it that only some carry.
 *
 * <p>What each of the three is drawn in is the label's ({@link CellTooltipLabelsTest}), that being a
 * fact about how a line is spoken rather than about what it says.
 */
final class CellTooltipQualifierTest {

    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored("graphics/hegemony_crest.png");

    @Nested
    class StateFinding {

        @Test
        void stateFindingIntroducesTheFindingWithNothing() {
            // The plain status the great majority of lines carry: one finding and no sentence around
            // it, which is what keeps it the single run it has always drawn as.
            var qualifier = CellTooltipQualifier.stateFinding("undiscovered");

            assertThat(qualifier.hasLeadingWord())
                .isFalse();
            assertThat(qualifier.hasMark())
                .isFalse();
            assertThat(qualifier.hasTrailingWord())
                .isFalse();
        }

        @Test
        void stateFindingRefusesAStatusWithNoWordsInIt() {
            // Checked where the caller that composed it is still on the stack. A blank finding would
            // otherwise surface as a line ending on a word and a picture introducing nothing, well
            // past the point that could say which line was meant - and a line calling nothing out
            // says so by carrying no qualifier at all.
            assertThatThrownBy(() -> CellTooltipQualifier.stateFinding(" "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class IntroduceFinding {

        @Test
        void introduceFindingCarriesTheWordAndMarkBesideTheFinding() {

            var qualifier = CellTooltipQualifier.introduceFinding("of", CREST_MARK, "Allied Powers");

            assertThat(qualifier.leadingWordText())
                .isEqualTo("of");
            assertThat(qualifier.mark())
                .isEqualTo(CREST_MARK);
            assertThat(qualifier.findingText())
                .isEqualTo("Allied Powers");
        }

        @Test
        void introduceFindingClosesTheStatusOnTheFinding() {
            // A name says for itself what kind of thing it is, so nothing follows it - which is what
            // keeps the closing word to the one case that needs it.
            assertThat(CellTooltipQualifier
                    .introduceFinding("of", CREST_MARK, "Allied Powers")
                    .hasTrailingWord())
                .isFalse();
        }

        @Test
        void introduceFindingStatesNoMarkWhereTheGameSuppliedNoTexture() {
            // A subject the game gives no crest for still states which one it is, on the same absence
            // rule every marked line holds to: the picture is dropped rather than drawn as an image
            // run with nothing to load.
            assertThat(CellTooltipQualifier
                    .introduceFinding("of", CellTooltipMark.NO_MARK, "Allied Powers")
                    .hasMark())
                .isFalse();
        }
    }

    @Nested
    class EncloseFinding {

        @Test
        void encloseFindingCarriesAWordEitherSideOfTheFinding() {
            // What a finding that cannot say for itself what it names needs: initials say nothing
            // about what kind of thing they stand for, so the box says it after them.
            var qualifier = CellTooltipQualifier.encloseFinding(
                "of the",
                CREST_MARK,
                "C.O.G.R.",
                "alliance");

            assertThat(qualifier.leadingWordText())
                .isEqualTo("of the");
            assertThat(qualifier.findingText())
                .isEqualTo("C.O.G.R.");
            assertThat(qualifier.trailingWordText())
                .isEqualTo("alliance");
        }
    }
}
