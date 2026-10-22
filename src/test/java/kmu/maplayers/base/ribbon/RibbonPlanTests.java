package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins a plan as the value the drawn band reads: what its runs add up to, when two plans are one
 * band, and that nothing outside it can change a plan once made.
 *
 * <p>Stated over plain colours and no rule for laying the runs, since a plan says nothing about who
 * decided it.
 */
final class RibbonPlanTests {

    private static final Color FIRST_RUN_COLOUR = new Color(200, 40, 40);
    private static final Color PARTING_COLOUR = new Color(90, 20, 20);
    private static final Color SECOND_RUN_COLOUR = new Color(40, 40, 200);

    @Nested
    class HasLength {

        @Test
        void answersYesForAPlanWithARunOfAnyLength() {

            // ARRANGE
            var plan = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 1)));

            // ACT
            var actualHasLength = plan.hasLength();

            // ASSERT
            assertThat(actualHasLength)
                .isTrue();
        }

        @Test
        void answersNoForACellWithNoRibbon() {

            // ARRANGE
            var plan = RibbonPlan.NONE;

            // ACT
            var actualHasLength = plan.hasLength();

            // ASSERT
            assertThat(actualHasLength)
                .isFalse();
        }

        // A rule may plan runs that come out empty, and a ring walked for them would carry
        // nothing, so a plan of zero-length runs counts as having no length at all.
        @Test
        void answersNoForAPlanWhoseRunsAreAllEmpty() {

            // ARRANGE
            var plan = new RibbonPlan(List.of(
                new RibbonSegment(FIRST_RUN_COLOUR, 0),
                new RibbonSegment(PARTING_COLOUR, 0)));

            // ACT
            var actualHasLength = plan.hasLength();

            // ASSERT
            assertThat(actualHasLength)
                .isFalse();
        }
    }

    @Nested
    class SumLengthUnits {

        @Test
        void addsUpEveryRunItWasBuiltFrom() {

            var plan = new RibbonPlan(List.of(
                new RibbonSegment(FIRST_RUN_COLOUR, 3),
                new RibbonSegment(PARTING_COLOUR, 1),
                new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            assertThat(plan.sumLengthUnits())
                .isEqualTo(7);
        }

        @Test
        void addsUpToNothingForACellWithNoRibbon() {

            assertThat(RibbonPlan.NONE.sumLengthUnits())
                .isEqualTo(0);
        }
    }

    @Nested
    class EqualsAndHashCode {

        @Test
        void equatesTwoPlansOfTheSameRuns() {
            // A plan is a value: two plans of the same runs are one band, which is what lets a
            // cell's standing band be compared with a re-baked one.
            var first = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));
            var second = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));

            assertThat(first)
                .isEqualTo(second)
                .hasSameHashCodeAs(second);
        }

        @Test
        void tellsApartTwoPlansOfDifferentRuns() {

            assertThat(new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3))))
                .isNotEqualTo(new RibbonPlan(List.of(new RibbonSegment(SECOND_RUN_COLOUR, 3))));
        }
    }

    @Nested
    class Segments {

        @Test
        void staysAsPlannedWhenTheListItWasBuiltFromIsAddedToAfterwards() {
            // A plan is cached and read back over many frames, so it has to be a value rather
            // than a window onto whatever list built it - a caller reusing its builder must not
            // be able to lengthen a ribbon already planned.
            var source = new ArrayList<RibbonSegment>();

            source.add(new RibbonSegment(FIRST_RUN_COLOUR, 3));

            var plan = new RibbonPlan(source);

            source.add(new RibbonSegment(SECOND_RUN_COLOUR, 3));

            assertThat(plan.segments())
                .containsExactly(new RibbonSegment(FIRST_RUN_COLOUR, 3));
        }

        @Test
        void refusesAnyChangeToAPlannedRibbon() {

            var plan = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));

            assertThatThrownBy(() -> plan.segments().add(new RibbonSegment(SECOND_RUN_COLOUR, 3)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
