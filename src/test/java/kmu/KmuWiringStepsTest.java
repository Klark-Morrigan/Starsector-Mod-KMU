package kmu;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the failure boundary the whole of start-up rests on: a step that throws costs its own
 * registration and nothing else.
 *
 * <p>Nothing else pins it. Every installer is a list of these, and the lists are called unguarded on
 * exactly the promise made here - so a boundary that let a throw escape would take the rest of a
 * load with it, and the first anyone would know is a half-wired sector with no indication of which
 * piece went missing.
 */
class KmuWiringStepsTest {

    @Nested
    class RunGuardedStep {

        @Test
        void runsTheStep() {

            var stepsRun = new ArrayList<String>();

            KmuWiringSteps.runGuardedStep(() -> stepsRun.add("ran"), "unused");

            assertThat(stepsRun)
                .containsExactly("ran");
        }

        @Test
        void swallowsWhateverTheStepThrows() {
            // The whole point: the caller carries on. A collaborator that throws is a collaborator
            // that did not install, which is survivable; a throw reaching the engine's load is not.
            var throwingStep = (Runnable) () -> {
                throw new IllegalStateException("the collaborator broke");
            };

            var guardedThrow = (Runnable) () ->
                KmuWiringSteps.runGuardedStep(throwingStep, "Failed to install something");

            assertThatCode(guardedThrow::run)
                .doesNotThrowAnyException();
        }

        @Test
        void leavesEveryLaterStepStillRunning() {
            // What a list of guarded steps is for, and the reason an installer's steps are listed
            // rather than called in sequence: the one that broke is the only one that is missing.
            var stepsRun = new ArrayList<String>();

            KmuWiringSteps.runGuardedStep(() -> stepsRun.add("first"), "unused");
            KmuWiringSteps.runGuardedStep(
                () -> {
                    throw new IllegalStateException("the middle collaborator broke");
                },
                "Failed to install the middle one");
            KmuWiringSteps.runGuardedStep(() -> stepsRun.add("third"), "unused");

            assertThat(stepsRun)
                .containsExactly("first", "third");
        }
    }
}
