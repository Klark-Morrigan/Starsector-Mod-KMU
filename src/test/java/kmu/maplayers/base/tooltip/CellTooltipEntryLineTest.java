package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a listed thing is composed of and what it leaves unstated: a plain line calls nothing out
 * and shows its number alone, a qualifier, a remark and a working are each layered onto one without
 * disturbing what it already carried, and a line with no name or no value is refused where the caller
 * that composed it is still on the stack rather than surfacing inside a draw with nothing to say which
 * line was meant.
 *
 * <p>What the line's mark is and how it is coloured is {@link CellTooltipMarkTest}'s: the line holds a
 * mark or holds none, and cannot state anything about one it does not have.
 */
final class CellTooltipEntryLineTest {

    private static final String CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored(CREST);

    // What a line showing nothing at its head carries where a mark would be.
    private static final CellTooltipMark NO_MARK = null;

    // What a line with no place in any ordering carries there - the plain case, and what every line
    // built through the factory has until one is stated on it.
    private static final CellTooltipIndexPlace NO_PLACE = null;

    // What a line remarking nothing about the thing on it carries in the note slot, which is again
    // every line until one is remarked on.
    private static final String NO_NOTE = null;

    // What every line built through the factory is: one of the things a block lists rather than a note
    // about them, carrying a number it earned rather than one an account recorded for it.
    private static final boolean IS_LISTED_IN_ITS_OWN_RIGHT = false;
    private static final boolean IS_VALUE_EARNED = false;

    @Nested
    class CreateLine {

        @Test
        void createLineCarriesItsMarkNameAndValueCallingNothingOut() {

            var line = CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        "The Hegemony",
                        NO_PLACE,
                        NO_NOTE,
                        null,
                        "1,200",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void createLineTakesAnAbsentMarkAsItStands() {
            // A caller resolving a mark that simply does not exist hands the absence straight over, so
            // a list of things carrying none is this same shape rather than a second one.
            var line = CellTooltipEntryLine.createLine(NO_MARK, "Independent", CellTooltipRows.NO_SCORE);

            assertThat(line.mark())
                .isNull();
        }

        @Test
        void createLineRefusesALineWithNoName() {
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST_MARK, null, "1,200"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void createLineRefusesALineWithNoValue() {
            // A line carrying no number states the value that means so, which the column collapses for -
            // an absence would reach the layout as a null instead.
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class HasMark {

        @Test
        void hasMarkReturnsTrueForALineLeadingWithOne() {

            assertThat(CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200").hasMark())
                .isTrue();
        }

        @Test
        void hasMarkReturnsFalseForALineCarryingNone() {
            // The judgement whatever reads a listing for its marks and whatever lays it out afterwards
            // both go through, so neither can reserve a column for a mark the other cannot show.
            assertThat(CellTooltipEntryLine
                    .createLine(NO_MARK, "None", CellTooltipRows.NO_SCORE)
                    .hasMark())
                .isFalse();
        }
    }

    @Nested
    class QualifiedWith {

        @Test
        void qualifiedWithCallsAStatusOutLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "The Hegemony", "1,200")
                .qualifiedWith("(core)");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        "The Hegemony",
                        NO_PLACE,
                        NO_NOTE,
                        "(core)",
                        "1,200",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void qualifiedWithLeavesTheLineItWasBuiltFromUnqualified() {
            // A refinement returns a new value, so a caller qualifying one line of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200");
            plainLine.qualifiedWith("(core)");

            assertThat(plainLine.qualifierText())
                .isNull();
        }
    }

    @Nested
    class NotedWith {

        @Test
        void notedWithRemarksOnTheLineLeavingTheRestOfItAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "Sentinel Gantries", "0")
                .notedWith("last seen 34 days ago, c206.05.12");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        "Sentinel Gantries",
                        NO_PLACE,
                        "last seen 34 days ago, c206.05.12",
                        null,
                        "0",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void notedWithKeepsAStatusTheLineAlreadyCallsOut() {
            // The two runs answer different questions - what the box has found about the thing on
            // the line, and how current the box's account of it is - so a line carrying both keeps
            // both rather than the later refinement dropping the first.
            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "Sentinel Gantries", "0")
                .qualifiedWith("(core)")
                .notedWith("last seen 34 days ago, c206.05.12");

            assertThat(line.qualifierText())
                .isEqualTo("(core)");
        }

        @Test
        void notedWithLeavesTheLineItWasBuiltFromRemarkingNothing() {
            // A refinement returns a new value, so a caller remarking on one line of a resolved
            // list cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(CREST_MARK, "Sentinel Gantries", "0");
            plainLine.notedWith("last seen 34 days ago, c206.05.12");

            assertThat(plainLine.noteText())
                .isNull();
        }
    }

    @Nested
    class IndexedAt {

        @Test
        void indexedAtStatesThePlaceLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "The Hegemony", "1,200")
                .indexedAt("[2]", CellTooltipIndexOutcome.WON);

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        "The Hegemony",
                        new CellTooltipIndexPlace("[2]", CellTooltipIndexOutcome.WON),
                        NO_NOTE,
                        null,
                        "1,200",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void indexedAtKeepsAStatusTheLineAlreadyCallsOut() {
            // The two runs answer different questions - which one this is, and what is true of it -
            // so a line stating both keeps both rather than the later refinement dropping the first.
            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "The Hegemony", "1,200")
                .qualifiedWith("(core)")
                .indexedAt("[2]", CellTooltipIndexOutcome.WON);

            assertThat(line.qualifierText())
                .isEqualTo("(core)");
            assertThat(line.indexPlace().text())
                .isEqualTo("[2]");
        }

        @Test
        void indexedAtLeavesTheLineItWasBuiltFromUnplaced() {
            // A refinement returns a new value, so a caller numbering one line of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200");
            plainLine.indexedAt("[2]", CellTooltipIndexOutcome.WON);

            assertThat(plainLine.indexPlace())
                .isNull();
        }
    }

    @Nested
    class StatesUncountedValue {

        @Test
        void statesUncountedValueQuietensTheNumberWithoutQuietingTheLine() {
            // The narrower of the two quiet readings, and the difference they exist for: this line is
            // one of the things the block lists, so it stays named as loudly as its neighbours and
            // only the number an account recorded for it quietens.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Tigra City", "0")
                .statesUncountedValue();

            assertThat(line.isValueUncounted())
                .isTrue();
            assertThat(line.isAside())
                .isFalse();
        }

        @Test
        void statesUncountedValueLeavesTheLineItWasBuiltFromAFinding() {
            // A refinement returns a new value, so a caller quietening one number of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Culann", "6");
            plainLine.statesUncountedValue();

            assertThat(plainLine.isValueUncounted())
                .isFalse();
        }
    }

    @Nested
    class ReadsAsAside {

        @Test
        void readsAsAsideMarksTheLineAsTheArithmeticBehindANumberRatherThanAFinding() {

            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Same-faction market bonus", "+2")
                .readsAsAside();

            assertThat(line.isAside())
                .isTrue();
        }

        @Test
        void readsAsAsideLeavesEveryOtherPartOfTheLineAsItWas() {
            // The refinement says how the line reads, not what it states, so a line that already
            // carries a working and a place keeps both.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Same-faction market bonus", "+2")
                .derivesValueFrom("(3 markets) - 1 =")
                .indexedAt("[2]", CellTooltipIndexOutcome.WON)
                .readsAsAside();

            assertThat(line.valueWorkingText())
                .isEqualTo("(3 markets) - 1 =");
            assertThat(line.valueText())
                .isEqualTo("+2");
            assertThat(line.indexPlace().text())
                .isEqualTo("[2]");
        }

        @Test
        void readsAsAsideLeavesTheLineItWasBuiltFromAFinding() {
            // A refinement returns a new value, so a caller quietening one line of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Culann", "6");
            plainLine.readsAsAside();

            assertThat(plainLine.isAside())
                .isFalse();
        }
    }

    @Nested
    class DerivesValueFrom {

        @Test
        void derivesValueFromStatesTheWorkingLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Small: 2", "500")
                .derivesValueFrom("0.25 /");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        NO_MARK,
                        "Small: 2",
                        NO_PLACE,
                        NO_NOTE,
                        null,
                        "500",
                        "0.25 /",
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void derivesValueFromKeepsAStatusTheLineAlreadyCallsOut() {
            // The working and the qualifier are stated at opposite ends of the line, so a line can
            // carry both - and a refinement that dropped one would silently lose it.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Size", "5,000")
                .qualifiedWith("hidden")
                .derivesValueFrom("5 ::");

            assertThat(line.qualifierText())
                .isEqualTo("hidden");
        }

        @Test
        void derivesValueFromLeavesTheLineItWasBuiltFromShowingItsNumberAlone() {

            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Small: 2", "500");
            plainLine.derivesValueFrom("0.25 /");

            assertThat(plainLine.valueWorkingText())
                .isNull();
        }
    }
}
