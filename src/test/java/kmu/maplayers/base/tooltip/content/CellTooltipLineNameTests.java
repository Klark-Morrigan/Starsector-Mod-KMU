package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the two accounts of what a line is called: the fold hands each to its own case, a spoken name
 * holds a finding only inside its own letters, and a withheld name holds a shape the caller cannot
 * reshape afterwards.
 *
 * <p>A line naming itself and withholding its name at once has no spelling here, so nothing pins its
 * refusal: there is nothing left to refuse.
 */
final class CellTooltipLineNameTests {

    // What a name saying none of the box's findings carries there.
    private static final CellTooltipLabelFinding NO_LABEL_FINDING = null;

    // What the fold answers for each case, so a case can say which one it was handed.
    private static final String SPOKEN_CASE = "spoken";
    private static final String WITHHELD_CASE = "withheld";

    @Nested
    class CreateSpokenName {

        @Test
        void holdsTheNameCallingNothingOutInsideIt() {

            assertThat(CellTooltipLineName.createSpokenName("Tigra City"))
                .isEqualTo(new CellTooltipLineName.Spoken("Tigra City", NO_LABEL_FINDING));
        }
    }

    @Nested
    class SelectByCase {

        @Test
        void handsASpokenNameToTheSpokenCase() {

            var name = new CellTooltipLineName.Spoken("Tigra City", NO_LABEL_FINDING);
            var answeredCase = name.selectByCase(spokenName -> SPOKEN_CASE, withheldName -> WITHHELD_CASE);

            assertThat(answeredCase)
                .isEqualTo("spoken");
        }

        @Test
        void handsAWithheldNameToTheWithheldCase() {

            var name = new CellTooltipLineName.Withheld(List.of(5, 4));
            var answeredCase = name.selectByCase(spokenName -> SPOKEN_CASE, withheldName -> WITHHELD_CASE);

            assertThat(answeredCase)
                .isEqualTo("withheld");
        }

        @Test
        void handsTheSpokenCaseTheNameItself() {
            // A reader of the name gets the words from the case it was handed rather than asking the
            // line a second question, which is what keeps the two cases from being read crosswise.
            var name = new CellTooltipLineName.Spoken("Tigra City", NO_LABEL_FINDING);
            var labelText = name.selectByCase(CellTooltipLineName.Spoken::labelText, withheldName -> WITHHELD_CASE);

            assertThat(labelText)
                .isEqualTo("Tigra City");
        }

        @Test
        void handsTheWithheldCaseTheShapeItself() {

            var name = new CellTooltipLineName.Withheld(List.of(5, 4));

            var wordLengths = name.selectByCase(
                spokenName -> List.<Integer>of(),
                CellTooltipLineName.Withheld::redactedWordLengths);

            assertThat(wordLengths)
                .containsExactly(5, 4);
        }
    }

    @Nested
    class SpokenConstructor {

        @Test
        void holdsAFindingInsideTheName() {

            var spokenName = new CellTooltipLineName.Spoken("Abandoned Station", new CellTooltipLabelFinding(0, 9));

            assertThat(spokenName.labelFinding())
                .isEqualTo(new CellTooltipLabelFinding(0, 9));
        }

        @Test
        void refusesAFindingRunningPastTheName() {
            // A cross-field rule the type cannot state, so it is checked where the caller that composed
            // the name is still on the stack rather than inside the draw that splits the label.
            assertThatThrownBy(() -> new CellTooltipLineName.Spoken("Culann", new CellTooltipLabelFinding(0, 9)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAMissingName() {

            assertThatThrownBy(() -> new CellTooltipLineName.Spoken(null, NO_LABEL_FINDING))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class WithheldConstructor {

        @Test
        void holdsTheShapeApartFromTheListItWasDerivedFrom() {
            // A caller deriving the lengths from a list it goes on using cannot reshape a name the box
            // has already stated.
            var derivedLengths = new ArrayList<>(List.of(7, 4));
            var withheldName = new CellTooltipLineName.Withheld(derivedLengths);

            derivedLengths.add(11);

            assertThat(withheldName.redactedWordLengths())
                .containsExactly(7, 4);
        }
    }
}
