package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins that a plan laid run by run is the same value as a plan stated whole, and that taking the
 * builder's list over costs nothing a copy would have protected: once the plan is built, neither
 * the builder nor the plan's own list can lengthen it.
 */
final class RibbonPlanBuilderTests {

    private static final Color FIRST_RUN_COLOUR = new Color(200, 40, 40);
    private static final Color PARTING_COLOUR = new Color(90, 20, 20);
    private static final Color SECOND_RUN_COLOUR = new Color(40, 40, 200);

    @Nested
    class AppendSegment {

        // The guarantee the plan's copy used to give: a plan already handed out stays as planned,
        // so a band read back over many frames never grows under the reader.
        @Test
        void refusesARunOnceThePlanIsBuilt() {

            // ARRANGE
            var planBuilder = new RibbonPlanBuilder();

            planBuilder.appendSegment(new RibbonSegment(FIRST_RUN_COLOUR, 3));
            planBuilder.buildPlan();

            // ACT
            var actualThrown = catchThrowable(
                () -> planBuilder.appendSegment(new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void refusesANullRun() {

            // ARRANGE
            var planBuilder = new RibbonPlanBuilder();

            // ACT
            var actualThrown = catchThrowable(() -> planBuilder.appendSegment(null));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class BuildPlan {

        // Laid run by run or stated whole, two plans of the same runs are one band, which is what
        // lets a re-baked band be compared with the one standing.
        @Test
        void equalsThePlanOfTheSameRunsStatedWhole() {

            // ARRANGE
            var planBuilder = new RibbonPlanBuilder();

            planBuilder.appendSegment(new RibbonSegment(FIRST_RUN_COLOUR, 3));
            planBuilder.appendSegment(new RibbonSegment(PARTING_COLOUR, 1));
            planBuilder.appendSegment(new RibbonSegment(SECOND_RUN_COLOUR, 3));

            var expectedPlan = new RibbonPlan(List.of(
                new RibbonSegment(FIRST_RUN_COLOUR, 3),
                new RibbonSegment(PARTING_COLOUR, 1),
                new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            // ACT
            var actualPlan = planBuilder.buildPlan();

            // ASSERT
            assertThat(actualPlan)
                .isEqualTo(expectedPlan)
                .hasSameHashCodeAs(expectedPlan);
        }

        @Test
        void answersNoRibbonWhereNoRunWasLaid() {

            // ARRANGE
            var planBuilder = new RibbonPlanBuilder();

            // ACT
            var actualPlan = planBuilder.buildPlan();

            // ASSERT
            assertThat(actualPlan)
                .isEqualTo(RibbonPlan.NONE);
        }

        // The list the plan took over is the plan's own now, and read back as a value it must
        // refuse a write exactly as a copied list would.
        @Test
        void refusesAnyChangeToTheRunsItTookOver() {

            // ARRANGE
            var planBuilder = new RibbonPlanBuilder();

            planBuilder.appendSegment(new RibbonSegment(FIRST_RUN_COLOUR, 3));

            var segments = planBuilder
                .buildPlan()
                .segments();

            // ACT
            var actualThrown = catchThrowable(() -> segments.add(new RibbonSegment(SECOND_RUN_COLOUR, 3)));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        // Two plans over the one list would share every later write, so a builder builds once.
        @Test
        void refusesToBuildASecondPlan() {

            // ARRANGE
            var planBuilder = new RibbonPlanBuilder();

            planBuilder.buildPlan();

            // ACT
            var actualThrown = catchThrowable(planBuilder::buildPlan);

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
