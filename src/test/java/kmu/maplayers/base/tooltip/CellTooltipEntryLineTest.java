package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a listed thing is composed of and what it leaves unstated: a plain line calls nothing out
 * and shows its number alone, a line whose name is withheld carries the shape of it and never the name,
 * a qualifier, a stretch of the name that is itself a finding, a remark and a working are each layered
 * onto one without disturbing what it already carried, and a line with no value, no account of what it
 * is called, two accounts of it, or a stretch running past its name is refused where the caller that
 * composed it is still on the stack rather than surfacing inside a draw with nothing to say which line
 * was meant.
 *
 * <p>What the line's mark is and how it is coloured is {@link CellTooltipMarkTest}'s: the line holds a
 * mark or holds none, and cannot state anything about one it does not have.
 */
final class CellTooltipEntryLineTest {

    private static final String CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored(CREST);

    // The shape of a withheld name: two words of seven and four characters, which is what reaches the
    // line in place of a name the box must not state.
    private static final List<Integer> WITHHELD_NAME = List.of(7, 4);

    // What a line showing nothing at its head carries where a mark would be.
    private static final CellTooltipMark NO_MARK = null;

    // What a line saying its name outright carries where the shape of a withheld one would be, and what
    // a line withholding its name carries where the name would be. Exactly one of the two stands on
    // every line.
    private static final List<Integer> NO_REDACTION = null;
    private static final String NO_NAME = null;

    // What a line whose name says none of the box's findings carries there, which is every line until
    // a resolver finds one of its words already on it.
    private static final CellTooltipLabelFinding NO_LABEL_FINDING = null;

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
                        NO_REDACTION,
                        NO_LABEL_FINDING,
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
            // Neither said nor withheld is not an absence the label can draw: a line is called
            // something, or its name is kept back and its shape stands where the name would.
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST_MARK, NO_NAME, "1,200"))
                .isInstanceOf(IllegalArgumentException.class);
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
    class CreateRedactedLine {

        @Test
        void createRedactedLineCarriesTheShapeOfItsNameInPlaceOfIt() {

            var line = CellTooltipEntryLine.createRedactedLine(CREST_MARK, WITHHELD_NAME, "820");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        NO_NAME,
                        List.of(7, 4),
                        NO_LABEL_FINDING,
                        NO_PLACE,
                        NO_NOTE,
                        null,
                        "820",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void createRedactedLineLeavesTheNameOffTheLineEntirely() {
            // The point of the shape rather than a consequence of it: the factory is never handed the
            // name, so no part of the line holds text a later change could be tempted to draw.
            assertThat(CellTooltipEntryLine
                    .createRedactedLine(NO_MARK, WITHHELD_NAME, "820")
                    .labelText())
                .isNull();
        }

        @Test
        void createRedactedLineHoldsTheShapeApartFromTheListItWasDerivedFrom() {
            // A caller deriving the lengths from a list it goes on using cannot reshape a name the box
            // has already stated.
            var derivedLengths = new ArrayList<>(List.of(7, 4));
            var line = CellTooltipEntryLine.createRedactedLine(NO_MARK, derivedLengths, "820");

            derivedLengths.add(11);

            assertThat(line.redactedWordLengths())
                .containsExactly(7, 4);
        }

        @Test
        void createRedactedLineRefusesALineNamedAndWithheldAtOnce() {
            // Two accounts of what the line is called, and the label draws one: held together, the same
            // line would come out named on one surface and blocked out on another.
            assertThatThrownBy(() -> new CellTooltipEntryLine(
                    NO_MARK,
                    "Tigra City",
                    WITHHELD_NAME,
                    NO_LABEL_FINDING,
                    NO_PLACE,
                    NO_NOTE,
                    null,
                    "820",
                    null,
                    IS_LISTED_IN_ITS_OWN_RIGHT,
                    IS_VALUE_EARNED))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void createRedactedLineRefusesALineWithNoValue() {
            // Withholding the name changes nothing about the value column: a line carrying no number
            // states the value that means so rather than reaching the layout as a null.
            assertThatThrownBy(() ->
                    CellTooltipEntryLine.createRedactedLine(CREST_MARK, WITHHELD_NAME, null))
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
    class HasRedactedName {

        @Test
        void hasRedactedNameReturnsTrueForALineKeepingItsNameBack() {

            assertThat(CellTooltipEntryLine
                    .createRedactedLine(CREST_MARK, WITHHELD_NAME, "820")
                    .hasRedactedName())
                .isTrue();
        }

        @Test
        void hasRedactedNameReturnsFalseForALineSayingWhatItIsCalled() {
            // The one judgement every surface goes through, so a line that says its name cannot be
            // drawn as though something had been kept back from it.
            assertThat(CellTooltipEntryLine
                    .createLine(CREST_MARK, "Tigra City", "820")
                    .hasRedactedName())
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
                        NO_REDACTION,
                        NO_LABEL_FINDING,
                        NO_PLACE,
                        NO_NOTE,
                        CellTooltipQualifier.stateFinding("(core)"),
                        "1,200",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void qualifiedWithKeepsANameTheLineWithholds() {
            // A refinement restates the one part it is about and carries the rest across, so a status
            // layered onto a redacted line cannot quietly restore the name it was keeping back.
            var line = CellTooltipEntryLine
                .createRedactedLine(CREST_MARK, WITHHELD_NAME, "820")
                .qualifiedWith("undiscovered");

            assertThat(line.hasRedactedName())
                .isTrue();
            assertThat(line.redactedWordLengths())
                .containsExactly(7, 4);
        }

        @Test
        void qualifiedWithLeavesTheLineItWasBuiltFromUnqualified() {
            // A refinement returns a new value, so a caller qualifying one line of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200");
            plainLine.qualifiedWith("(core)");

            assertThat(plainLine.qualifier())
                .isNull();
        }
    }

    @Nested
    class CallsOutInLabel {

        @Test
        void callsOutInLabelPicksTheStretchOutLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Abandoned Station", "0")
                .callsOutInLabel(0, 9);

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        NO_MARK,
                        "Abandoned Station",
                        NO_REDACTION,
                        new CellTooltipLabelFinding(0, 9),
                        NO_PLACE,
                        NO_NOTE,
                        null,
                        "0",
                        null,
                        IS_LISTED_IN_ITS_OWN_RIGHT,
                        IS_VALUE_EARNED));
        }

        @Test
        void callsOutInLabelKeepsAStatusTheLineAlreadyCallsOutAfterItsName() {
            // The two are the same finding drawn in two places rather than one displacing the other, so
            // a colony saying what it is in its name and undiscovered besides states both.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Abandoned Station", "0")
                .qualifiedWith("undiscovered")
                .callsOutInLabel(0, 9);

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("undiscovered"));
            assertThat(line.labelFinding())
                .isEqualTo(new CellTooltipLabelFinding(0, 9));
        }

        @Test
        void callsOutInLabelRefusesAStretchRunningPastTheName() {
            // Checked where the resolver that found the stretch is still on the stack: a range past the
            // end of the label otherwise surfaces inside the draw that splits it, well past the point
            // that could say which line was meant.
            assertThatThrownBy(() -> CellTooltipEntryLine
                    .createLine(NO_MARK, "Culann", "6")
                    .callsOutInLabel(0, 9))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void callsOutInLabelRefusesALineWhoseNameIsWithheld() {
            // There is no name to pick a stretch out of, and the blocks drawn in its place stand for
            // words rather than spelling them - so a range into one could only gild whatever happened
            // to be that far along.
            assertThatThrownBy(() -> CellTooltipEntryLine
                    .createRedactedLine(NO_MARK, WITHHELD_NAME, "820")
                    .callsOutInLabel(0, 7))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void callsOutInLabelLeavesTheLineItWasBuiltFromSayingNothingInItsName() {
            // A refinement returns a new value, so a caller gilding one line of a resolved list cannot
            // reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Abandoned Station", "0");
            plainLine.callsOutInLabel(0, 9);

            assertThat(plainLine.labelFinding())
                .isNull();
        }
    }

    @Nested
    class NotedWith {

        @Test
        void notedWithRemarksOnTheLineLeavingTheRestOfItAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "Sentinel Gantries", "0")
                .notedWith("last seen 34 days ago (c206.05.12)");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        "Sentinel Gantries",
                        NO_REDACTION,
                        NO_LABEL_FINDING,
                        NO_PLACE,
                        "last seen 34 days ago (c206.05.12)",
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
                .notedWith("last seen 34 days ago (c206.05.12)");

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("(core)"));
        }

        @Test
        void notedWithLeavesTheLineItWasBuiltFromRemarkingNothing() {
            // A refinement returns a new value, so a caller remarking on one line of a resolved
            // list cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(CREST_MARK, "Sentinel Gantries", "0");
            plainLine.notedWith("last seen 34 days ago (c206.05.12)");

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
                        NO_REDACTION,
                        NO_LABEL_FINDING,
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

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("(core)"));
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
                        NO_REDACTION,
                        NO_LABEL_FINDING,
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

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("hidden"));
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
