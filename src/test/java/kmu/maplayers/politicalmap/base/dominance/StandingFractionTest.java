package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the one rule about when a fraction is worth drawing: both ends of its range state exactly what
 * the heading above the row already said, and everything between them says something the heading did
 * not.
 */
class StandingFractionTest {

    @Nested
    class IsStated {

        @Test
        void isFalseWhereTheHeadingTookNoneOfTheRow() {
            // The heading is false of all of it, which is a row that would not be under that heading -
            // so a nought states nothing a reader could act on.
            assertThat(new StandingFraction(0, 3).isStated())
                .isFalse();
        }

        @Test
        void isFalseWhereTheHeadingTookAllOfTheRow() {

            assertThat(new StandingFraction(3, 3).isStated())
                .isFalse();
        }

        @Test
        void isTrueWhereTheHeadingTookPartOfTheRow() {

            assertThat(new StandingFraction(1, 3).isStated())
                .isTrue();
            assertThat(new StandingFraction(2, 3).isStated())
                .isTrue();
        }

        @Test
        void isFalseForARowWithNothingToState() {
            // What every block placed by membership carries. Nothing counted out of nothing falls at
            // both ends at once, so the absence needs no reading of its own.
            assertThat(StandingFraction.NOTHING_TO_STATE.isStated())
                .isFalse();
        }

        @Test
        void isFalseForALoneFactionAgainstALoneHolder() {
            // Why the whole device belongs to the alliances view: a faction measured against a holder
            // of one can only ever count nought or the whole, so nothing draws without an alliance
            // somewhere in the reading.
            assertThat(new StandingFraction(1, 1).isStated())
                .isFalse();
            assertThat(new StandingFraction(0, 1).isStated())
                .isFalse();
        }
    }

    @Nested
    class StandingFractionConstruction {

        @Test
        void rejectsACountThatIsNotPartOfItsWhole() {
            // Caught where the caller that worked the count out is still on the stack, a fraction
            // outside its own range saying nothing a reader could act on.
            assertThatThrownBy(() -> new StandingFraction(4, 3))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANegativeCountOrTotal() {

            assertThatThrownBy(() -> new StandingFraction(-1, 3))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StandingFraction(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
