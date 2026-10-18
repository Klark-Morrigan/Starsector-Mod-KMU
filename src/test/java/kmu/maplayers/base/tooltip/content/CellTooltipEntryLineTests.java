package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a listed thing is composed of and what it leaves unstated: a plain line calls nothing out
 * and shows its number alone, a line whose name is withheld carries the shape of it and never the name,
 * a qualifier, a stretch of the name that is itself a finding, a remark and a working are each layered
 * onto one without disturbing what it already carried, and a line with no value, no name, or a stretch
 * running past its name is refused where the caller that composed it is still on the stack rather than
 * surfacing inside a draw with nothing to say which line was meant.
 *
 * <p>What the line's mark is and how it is coloured is {@link CellTooltipMarkTests}'s: the line holds a
 * mark or holds none, and cannot state anything about one it does not have. What a name holds is
 * {@link CellTooltipLineNameTests}'.
 */
final class CellTooltipEntryLineTests {

    private static final String CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored(CREST);

    // The shape of a withheld name: two words of seven and four characters, which is what reaches the
    // line in place of a name the box must not state.
    private static final List<Integer> WITHHELD_NAME = List.of(7, 4);

    // What a line showing nothing at its head carries where a mark would be.
    private static final CellTooltipMark NO_MARK = null;

    // What a factory is handed where a caller has no name for the line at all.
    private static final String NO_NAME = null;

    // What a name saying none of the box's findings carries there, which is every name until a
    // resolver finds one of its words already on it.
    private static final CellTooltipLabelFinding NO_LABEL_FINDING = null;

    // What a line with no place in any ordering carries there - the plain case, and what every line
    // built through the factory has until one is stated on it.
    private static final CellTooltipIndexPlace NO_PLACE = null;

    // What a line remarking nothing about the thing on it carries in the note slot, which is again
    // every line until one is remarked on.
    private static final String NO_NOTE = null;

    // What a line calling no status out carries in the qualifier slot.
    private static final CellTooltipQualifier NO_QUALIFIER = null;

    // What a line whose value is words rather than a number the block adds up carries in the counted
    // slot - every line built through the plain factories, which are handed the value already worded.
    private static final Integer NO_COUNT = null;

    // What a line showing its number alone carries where the working would be.
    private static final String NO_WORKING = null;

    // What every line built through the factory is: one of the things a block lists rather than a note
    // about them, carrying a number it earned rather than one an account recorded for it.
    private static final boolean IS_LISTED_IN_ITS_OWN_RIGHT = false;
    private static final boolean IS_VALUE_EARNED = false;

    // What a line whose number an account recorded rather than earned carries.
    private static final boolean IS_VALUE_UNCOUNTED = true;

    @Nested
    class CreateLine {

        @Test
        void carriesItsMarkNameAndValueCallingNothingOut() {

            var line = CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        new CellTooltipLineName.Spoken("The Hegemony", NO_LABEL_FINDING),
                        NO_PLACE,
                        NO_NOTE,
                        NO_QUALIFIER,
                        new CellTooltipLineValue("1,200", NO_COUNT, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void takesAnAbsentMarkAsItStands() {
            // A caller resolving a mark that simply does not exist hands the absence straight over, so
            // a list of things carrying none is this same shape rather than a second one.
            var line = CellTooltipEntryLine.createLine(NO_MARK, "Independent", CellTooltipEntryLine.NO_SCORE);

            assertThat(line.mark())
                .isNull();
        }

        @Test
        void refusesALineWithNoName() {
            // Neither said nor withheld is not an absence the label can draw: a line is called
            // something, or its name is kept back and its shape stands where the name would.
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST_MARK, NO_NAME, "1,200"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void refusesALineWithNoValue() {
            // A line carrying no number states the value that means so, which the column collapses for -
            // an absence would reach the layout as a null instead.
            assertThatThrownBy(() -> CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class CreateCountedLine {

        @Test
        void wordsTheNumberItWasHandedAndHoldsOntoIt() {
            // The whole point of the shape: the line shows the figure and carries it, so a row standing
            // in for lines the box could not draw can add them up and word the total the same way.
            var line = CellTooltipEntryLine.createCountedLine(CREST_MARK, "The Hegemony", 23600);

            assertThat(line.value().valueText())
                .isEqualTo("23,600");
            assertThat(line.value().countedValue())
                .isEqualTo(23600);
        }

        @Test
        void callsNothingOutAndShowsItsNumberAlone() {
            // The plainest line there is, arrived at from the number rather than from words for it:
            // everything a line may go on to say is layered onto it afterwards, as on any other.
            assertThat(CellTooltipEntryLine.createCountedLine(CREST_MARK, "The Hegemony", 1200))
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        new CellTooltipLineName.Spoken("The Hegemony", NO_LABEL_FINDING),
                        NO_PLACE,
                        NO_NOTE,
                        NO_QUALIFIER,
                        new CellTooltipLineValue("1,200", 1200, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void keepsItsNumberThroughARefinementLaidOnIt() {
            // A line refined into an aside, a quiet value, or a qualified one is the same line and the
            // same figure - a refinement that dropped the count would leave the row standing in for it
            // silently short.
            assertThat(CellTooltipEntryLine
                    .createCountedLine(CREST_MARK, "The Hegemony", 1200)
                    .statesUncountedValue()
                    .value()
                    .countedValue())
                .isEqualTo(1200);
        }
    }

    @Nested
    class FormatCountedValue {

        @Test
        void wordsANumberTheWayACountedLineStatesItsOwn() {
            // What a stand-in row words its total through, so a total cannot come out spelled unlike
            // the figures it was summed from.
            assertThat(CellTooltipEntryLine.formatCountedValue(4320))
                .isEqualTo("4,320");
        }
    }

    @Nested
    class CreateRedactedLine {

        @Test
        void carriesTheShapeOfItsNameInPlaceOfIt() {
            // The name is never handed to the factory, so the line holds only its shape and there is
            // no text on it a later change could be tempted to draw.
            var line = CellTooltipEntryLine.createRedactedLine(CREST_MARK, WITHHELD_NAME, "820");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        new CellTooltipLineName.Withheld(List.of(7, 4)),
                        NO_PLACE,
                        NO_NOTE,
                        NO_QUALIFIER,
                        new CellTooltipLineValue("820", NO_COUNT, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void refusesALineWithNoValue() {
            // Withholding the name changes nothing about the value column: a line carrying no number
            // states the value that means so rather than reaching the layout as a null.
            assertThatThrownBy(() ->
                    CellTooltipEntryLine.createRedactedLine(CREST_MARK, WITHHELD_NAME, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class CreateRedactedCountedLine {

        @Test
        void keepsBackTheNameAndNotTheFigure() {
            // What a withheld line withholds is the name. Its number is one of the block's own, so it
            // is stated and counted like any other - a listing cut short still adds up.
            var line = CellTooltipEntryLine.createRedactedCountedLine(CREST_MARK, WITHHELD_NAME, 820);

            assertThat(line.name())
                .isEqualTo(new CellTooltipLineName.Withheld(List.of(7, 4)));
            assertThat(line.value())
                .isEqualTo(new CellTooltipLineValue("820", 820, NO_WORKING, IS_VALUE_EARNED));
        }
    }

    @Nested
    class HasMark {

        @Test
        void returnsTrueForALineLeadingWithOne() {

            assertThat(CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200").hasMark())
                .isTrue();
        }

        @Test
        void returnsFalseForALineCarryingNone() {
            // The judgement whatever reads a listing for its marks and whatever lays it out afterwards
            // both go through, so neither can reserve a column for a mark the other cannot show.
            assertThat(CellTooltipEntryLine
                    .createLine(NO_MARK, "None", CellTooltipEntryLine.NO_SCORE)
                    .hasMark())
                .isFalse();
        }
    }

    @Nested
    class QualifiedWith {

        @Test
        void callsAStatusOutLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "The Hegemony", "1,200")
                .qualifiedWith("(core)");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        new CellTooltipLineName.Spoken("The Hegemony", NO_LABEL_FINDING),
                        NO_PLACE,
                        NO_NOTE,
                        CellTooltipQualifier.stateFinding("(core)"),
                        new CellTooltipLineValue("1,200", NO_COUNT, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void keepsANameTheLineWithholds() {
            // A refinement restates the one part it is about and carries the rest across, so a status
            // layered onto a redacted line cannot quietly restore the name it was keeping back.
            var line = CellTooltipEntryLine
                .createRedactedLine(CREST_MARK, WITHHELD_NAME, "820")
                .qualifiedWith("undiscovered");

            assertThat(line.name())
                .isEqualTo(new CellTooltipLineName.Withheld(List.of(7, 4)));
        }

        @Test
        void leavesTheLineItWasBuiltFromUnqualified() {
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
        void picksTheStretchOutLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Abandoned Station", "0")
                .callsOutInLabel(0, 9);

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        NO_MARK,
                        new CellTooltipLineName.Spoken("Abandoned Station", new CellTooltipLabelFinding(0, 9)),
                        NO_PLACE,
                        NO_NOTE,
                        NO_QUALIFIER,
                        new CellTooltipLineValue("0", NO_COUNT, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void keepsAStatusTheLineAlreadyCallsOutAfterItsName() {
            // The two are the same finding drawn in two places rather than one displacing the other, so
            // a colony saying what it is in its name and undiscovered besides states both.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Abandoned Station", "0")
                .qualifiedWith("undiscovered")
                .callsOutInLabel(0, 9);

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("undiscovered"));
            assertThat(line.name())
                .isEqualTo(new CellTooltipLineName.Spoken("Abandoned Station", new CellTooltipLabelFinding(0, 9)));
        }

        @Test
        void refusesAStretchRunningPastTheName() {
            // Checked where the resolver that found the stretch is still on the stack: a range past the
            // end of the label otherwise surfaces inside the draw that splits it, well past the point
            // that could say which line was meant.
            assertThatThrownBy(() -> CellTooltipEntryLine
                    .createLine(NO_MARK, "Culann", "6")
                    .callsOutInLabel(0, 9))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesALineWhoseNameIsWithheld() {
            // There is no name to pick a stretch out of, and the blocks drawn in its place stand for
            // words rather than spelling them - so a range into one could only gild whatever happened
            // to be that far along.
            assertThatThrownBy(() -> CellTooltipEntryLine
                    .createRedactedLine(NO_MARK, WITHHELD_NAME, "820")
                    .callsOutInLabel(0, 7))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void leavesTheLineItWasBuiltFromSayingNothingInItsName() {
            // A refinement returns a new value, so a caller gilding one line of a resolved list cannot
            // reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Abandoned Station", "0");
            plainLine.callsOutInLabel(0, 9);

            assertThat(plainLine.name())
                .isEqualTo(new CellTooltipLineName.Spoken("Abandoned Station", NO_LABEL_FINDING));
        }
    }

    @Nested
    class NotedWith {

        @Test
        void remarksOnTheLineLeavingTheRestOfItAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "Sentinel Gantries", "0")
                .notedWith("last seen 34 days ago (c206.05.12)");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        new CellTooltipLineName.Spoken("Sentinel Gantries", NO_LABEL_FINDING),
                        NO_PLACE,
                        "last seen 34 days ago (c206.05.12)",
                        NO_QUALIFIER,
                        new CellTooltipLineValue("0", NO_COUNT, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void keepsAStatusTheLineAlreadyCallsOut() {
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
        void leavesTheLineItWasBuiltFromRemarkingNothing() {
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
        void statesThePlaceLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(CREST_MARK, "The Hegemony", "1,200")
                .indexedAt("[2]", CellTooltipIndexOutcome.WON);

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        CREST_MARK,
                        new CellTooltipLineName.Spoken("The Hegemony", NO_LABEL_FINDING),
                        new CellTooltipIndexPlace("[2]", CellTooltipIndexOutcome.WON),
                        NO_NOTE,
                        NO_QUALIFIER,
                        new CellTooltipLineValue("1,200", NO_COUNT, NO_WORKING, IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void keepsAStatusTheLineAlreadyCallsOut() {
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
        void leavesTheLineItWasBuiltFromUnplaced() {
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
        void quietensTheNumberWithoutQuietingTheLine() {
            // The narrower of the two quiet readings, and the difference they exist for: this line is
            // one of the things the block lists, so it stays named as loudly as its neighbours and
            // only the number an account recorded for it quietens.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Tigra City", "0")
                .statesUncountedValue();

            assertThat(line.value().isValueUncounted())
                .isTrue();
            assertThat(line.isAside())
                .isFalse();
        }

        @Test
        void keepsTheWorkingTheValueAlreadyShows() {
            // The value is one part of the line, so restating whether its number was earned must carry
            // the rest of that part across rather than drop the working laid on before it.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Small: 2", "500")
                .derivesValueFrom("0.25 /")
                .statesUncountedValue();

            assertThat(line.value())
                .isEqualTo(new CellTooltipLineValue("500", NO_COUNT, "0.25 /", IS_VALUE_UNCOUNTED));
        }

        @Test
        void leavesTheLineItWasBuiltFromAFinding() {
            // A refinement returns a new value, so a caller quietening one number of a resolved list
            // cannot reach into the line another caller is still holding.
            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Culann", "6");
            plainLine.statesUncountedValue();

            assertThat(plainLine.value().isValueUncounted())
                .isFalse();
        }
    }

    @Nested
    class ReadsAsAside {

        @Test
        void marksTheLineAsTheArithmeticBehindANumberRatherThanAFinding() {

            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Same-faction market bonus", "+2")
                .readsAsAside();

            assertThat(line.isAside())
                .isTrue();
        }

        @Test
        void leavesEveryOtherPartOfTheLineAsItWas() {
            // The refinement says how the line reads, not what it states, so a line that already
            // carries a working and a place keeps both.
            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Same-faction market bonus", "+2")
                .derivesValueFrom("(3 markets) - 1 =")
                .indexedAt("[2]", CellTooltipIndexOutcome.WON)
                .readsAsAside();

            assertThat(line.value().valueWorkingText())
                .isEqualTo("(3 markets) - 1 =");
            assertThat(line.value().valueText())
                .isEqualTo("+2");
            assertThat(line.indexPlace().text())
                .isEqualTo("[2]");
        }

        @Test
        void leavesTheLineItWasBuiltFromAFinding() {
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
        void statesTheWorkingLeavingTheRestOfTheLineAsItWas() {

            var line = CellTooltipEntryLine
                .createLine(NO_MARK, "Small: 2", "500")
                .derivesValueFrom("0.25 /");

            assertThat(line)
                .isEqualTo(
                    new CellTooltipEntryLine(
                        NO_MARK,
                        new CellTooltipLineName.Spoken("Small: 2", NO_LABEL_FINDING),
                        NO_PLACE,
                        NO_NOTE,
                        NO_QUALIFIER,
                        new CellTooltipLineValue("500", NO_COUNT, "0.25 /", IS_VALUE_EARNED),
                        IS_LISTED_IN_ITS_OWN_RIGHT));
        }

        @Test
        void keepsAStatusTheLineAlreadyCallsOut() {
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
        void keepsANumberNothingEarned() {
            // The value is one part of the line, so stating its working must carry the rest of that
            // part across rather than restore a quietened number to a finding.
            var line = CellTooltipEntryLine
                .createCountedLine(NO_MARK, "Tigra City", 0)
                .statesUncountedValue()
                .derivesValueFrom("0 x");

            assertThat(line.value())
                .isEqualTo(new CellTooltipLineValue("0", 0, "0 x", IS_VALUE_UNCOUNTED));
        }

        @Test
        void leavesTheLineItWasBuiltFromShowingItsNumberAlone() {

            var plainLine = CellTooltipEntryLine.createLine(NO_MARK, "Small: 2", "500");
            plainLine.derivesValueFrom("0.25 /");

            assertThat(plainLine.value().valueWorkingText())
                .isNull();
        }
    }
}
