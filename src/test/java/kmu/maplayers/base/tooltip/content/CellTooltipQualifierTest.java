package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a status called out after a line's name is made of: a finding it always states, and the
 * word and mark introducing it and the colour of its own that only some carry.
 *
 * <p>What each of the three is drawn in is the label's ({@link CellTooltipLabelsTest}), that being a
 * fact about how a line is spoken rather than about what it says.
 */
final class CellTooltipQualifierTest {

    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored("graphics/hegemony_crest.png");

    // A colour standing for a relation level, the kind of finding whose colour is itself the fact.
    private static final Color HOSTILE_SHADE = new Color(210, 60, 50);

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
        void stateFindingLeavesTheFindingInTheBoxsHighlight() {
            // The shared look is the default: a status states a colour of its own only where it is
            // given one, so every layer that does not ask calls its findings out alike.
            assertThat(CellTooltipQualifier.stateFinding("undiscovered").hasOwnFindingColour())
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

    @Nested
    class DrawsFindingIn {

        @Test
        void drawsFindingInCarriesTheColourBesideTheFinding() {

            assertThat(CellTooltipQualifier
                    .stateFinding("hostile")
                    .drawsFindingIn(HOSTILE_SHADE)
                    .findingColour())
                .isEqualTo(HOSTILE_SHADE);
        }

        @Test
        void drawsFindingInKeepsEveryOtherPartOfTheStatus() {
            // One exception laid over any shape of status, so an introduced or enclosed finding
            // whose colour is the fact keeps the sentence around it exactly as it was composed.
            var qualifier = CellTooltipQualifier
                .encloseFinding("of the", CREST_MARK, "C.O.G.R.", "alliance")
                .drawsFindingIn(HOSTILE_SHADE);

            assertThat(qualifier)
                .isEqualTo(new CellTooltipQualifier(
                    "of the",
                    CREST_MARK,
                    "C.O.G.R.",
                    HOSTILE_SHADE,
                    "alliance"));
        }

        @Test
        void drawsFindingInRefusesANullColour() {
            // A finding reading in the box's highlight states that by never being given a colour;
            // a null handed in here would be that same default reached by a call claiming otherwise.
            var qualifier = CellTooltipQualifier.stateFinding("hostile");

            assertThatThrownBy(() -> qualifier.drawsFindingIn(null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
