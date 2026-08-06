package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a listed thing is composed of and what it leaves unstated: a plain line calls nothing out,
 * a qualifier is layered onto one without disturbing what it already carried, and a line with no name
 * or no value is refused where the caller that composed it is still on the stack rather than surfacing
 * inside a draw with nothing to say which line was meant.
 */
final class CellTooltipEntryLineTest {

    private static final String CREST = "graphics/hegemony_crest.png";

    @Nested
    class CreateLine {

        @Test
        void createLineCarriesItsMarkNameAndValueCallingNothingOut() {

            var line = CellTooltipEntryLine.createLine(CREST, "The Hegemony", "1,200");

            assertThat(line)
                .isEqualTo(new CellTooltipEntryLine(CREST, "The Hegemony", null, "1,200"));
        }

        @Test
        void createLineTakesAnAbsentMarkAsItStands() {
            // A caller resolving a mark that simply does not exist hands the absence straight over, so
            // a list of things carrying none is this same shape rather than a second one.
            var line = CellTooltipEntryLine.createLine(null, "Independent", CellTooltipRows.NO_SCORE);

            assertThat(line.iconSpritePath())
                .isNull();
        }

        @Test
        void createLineRefusesALineWithNoName() {
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST, null, "1,200"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void createLineRefusesALineWithNoValue() {
            // A line carrying no number states the value that means so, which the column collapses for -
            // an absence would reach the layout as a null instead.
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST, "The Hegemony", null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class HasMark {

        @Test
        void hasMarkReturnsTrueForALineLeadingWithOne() {

            assertThat(CellTooltipEntryLine.createLine(CREST, "The Hegemony", "1,200").hasMark())
                .isTrue();
        }

        @Test
        void hasMarkReturnsFalseForALineCarryingNone() {
            // The judgement whatever reads a listing for its marks and whatever lays it out afterwards
            // both go through, so neither can reserve a column for a mark the other cannot show.
            assertThat(CellTooltipEntryLine
                    .createLine(null, "None", CellTooltipRows.NO_SCORE)
                    .hasMark())
                .isFalse();
        }
    }

    @Nested
    class QualifiedWith {

        @Test
        void qualifiedWithCallsAStatusOutLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST, "The Hegemony", "1,200")
                .qualifiedWith("(core)");

            assertThat(line)
                .isEqualTo(new CellTooltipEntryLine(CREST, "The Hegemony", "(core)", "1,200"));
        }

        @Test
        void qualifiedWithLeavesTheLineItWasBuiltFromUnqualified() {
            // A refinement returns a new value, so a caller qualifying one line of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(CREST, "The Hegemony", "1,200");
            plainLine.qualifiedWith("(core)");

            assertThat(plainLine.qualifierText())
                .isNull();
        }
    }
}
