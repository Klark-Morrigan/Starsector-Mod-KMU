package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins a plan as the value the drawn band reads: what its segments add up to, when two plans are
 * one band, and that nothing outside it can change a plan once made.
 *
 * <p>Stated over plain colours and no rule for laying the segments, since a plan says nothing about who
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

        // A rule may plan segments that come out empty, and a ring walked for them would carry
        // nothing, so a plan of zero-length segments counts as having no length at all.
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

            // ARRANGE
            var expectedLengthUnits = 7;
            var plan = new RibbonPlan(List.of(
                new RibbonSegment(FIRST_RUN_COLOUR, 3),
                new RibbonSegment(PARTING_COLOUR, 1),
                new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            // ACT
            var actualLengthUnits = plan.sumLengthUnits();

            // ASSERT
            assertThat(actualLengthUnits)
                .isEqualTo(expectedLengthUnits);
        }

        @Test
        void addsUpToNothingForACellWithNoRibbon() {

            // ARRANGE
            var plan = RibbonPlan.NONE;
            var expectedLengthUnits = 0;

            // ACT
            var actualLengthUnits = plan.sumLengthUnits();

            // ASSERT
            assertThat(actualLengthUnits)
                .isEqualTo(expectedLengthUnits);
        }
    }

    @Nested
    class EqualsAndHashCode {

        // A plan is a value: two plans of the same segments are one band, which is what lets a
        // cell's standing band be compared with a re-baked one.
        @Test
        void equatesTwoPlansOfTheSameRuns() {

            // ARRANGE
            var expectedPlan = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));

            // ACT
            var actualPlan = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));

            // ASSERT
            assertThat(actualPlan)
                .isEqualTo(expectedPlan)
                .hasSameHashCodeAs(expectedPlan);
        }

        @Test
        void tellsApartTwoPlansOfDifferentRuns() {

            // ARRANGE
            var differentPlan = new RibbonPlan(List.of(new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            // ACT
            var actualPlan = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));

            // ASSERT
            assertThat(actualPlan)
                .isNotEqualTo(differentPlan);
        }
    }

    @Nested
    class Segments {

        // A plan is cached and read back over many frames, so it has to be a value rather
        // than a window onto whatever list built it - a caller reusing its builder must not
        // be able to lengthen a ribbon already planned.
        @Test
        void staysAsPlannedWhenTheListItWasBuiltFromIsAddedToAfterwards() {

            // ARRANGE
            var source = new ArrayList<RibbonSegment>();

            source.add(new RibbonSegment(FIRST_RUN_COLOUR, 3));

            var plan = new RibbonPlan(source);
            var expectedSegment = new RibbonSegment(FIRST_RUN_COLOUR, 3);

            // ACT
            source.add(new RibbonSegment(SECOND_RUN_COLOUR, 3));

            // ASSERT
            var actualSegments = plan.segments();

            assertThat(actualSegments)
                .containsExactly(expectedSegment);
        }

        @Test
        void refusesAnyChangeToAPlannedRibbon() {

            // ARRANGE
            var plan = new RibbonPlan(List.of(new RibbonSegment(FIRST_RUN_COLOUR, 3)));

            // ACT
            var actualThrown = catchThrowable(
                () -> plan
                    .segments()
                    .add(new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
