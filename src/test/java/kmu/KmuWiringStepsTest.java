package kmu;

import kmlib.starsector.compatibility.CompatibilityConsumer;
import kmlib.starsector.compatibility.CompatibilityFailures;
import kmlib.starsector.compatibility.ModIntegration;
import kmlib.testfixtures.logging.LogAppenderFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins that {@link KmuWiringSteps} hands each step to KMLib's guard as KMU: a failed step logs under
 * KMU's own logger, and only a step naming the mod it binds to is recorded for the player.
 *
 * <p>What the guard catches and what it lets through are pinned once, on KMLib's
 * {@code WiringSteps}; here each entry point is driven far enough to show it reached that guard and
 * which of its two halves.
 *
 * <p>The record is the process's own, which outlives a case, so it is drained on both sides of
 * each. Its latch cannot be emptied, which is why every case recording into it names a third party
 * of its own.
 */
final class KmuWiringStepsTest {

    private static final String FAILURE_MESSAGE = "Failed to install something";

    @BeforeEach
    void drainTheSessionRecordBefore() {

        drainTheSessionRecord();
    }

    @AfterEach
    void drainTheSessionRecordAfter() {

        drainTheSessionRecord();
    }

    @Nested
    class RunGuardedStep {

        @Test
        void runsTheStep() {

            var stepsRun = new ArrayList<String>();

            KmuWiringSteps.runGuardedStep(() -> stepsRun.add("ran"), FAILURE_MESSAGE);

            assertThat(stepsRun)
                .containsExactly("ran");
        }

        @Test
        void logsAStepThatThrewUnderKmusOwnLogger() {
            // KMU's logger rather than KMLib's, so the line sits inside the subtree KMU's log-level
            // setting reaches.
            var capturedLog = LogAppenderFake.captureLogOf(
                KmuWiringSteps.class,
                () -> assertThatCode(() -> KmuWiringSteps.runGuardedStep(buildThrowingStep(), FAILURE_MESSAGE))
                    .doesNotThrowAnyException());

            assertThat(capturedLog.getMessages())
                .containsExactly(FAILURE_MESSAGE);
        }

        @Test
        void recordsNothingForThePlayer() {

            KmuWiringSteps.runGuardedStep(buildThrowingStep(), FAILURE_MESSAGE);

            assertThat(CompatibilityFailures.SESSION_RECORD.takeNextUnreported())
                .isNull();
        }
    }

    @Nested
    class RunGuardedStepForAnIntegration {

        @Test
        void recordsTheFailureForThePlayerUnderTheIntegrationItNamed() {

            var integration = createIntegrationWith("kmu-test-recorded-mod");

            KmuWiringSteps.runGuardedStep(buildThrowingStep(), FAILURE_MESSAGE, () -> integration);

            var failure = CompatibilityFailures.SESSION_RECORD.takeNextUnreported();

            assertThat(failure.subject().name())
                .isEqualTo("Test Mod");
            assertThat(failure.consumer().consumerKey())
                .isEqualTo("kmu:wiring-steps-test");
            assertThat(CompatibilityFailures.SESSION_RECORD.takeNextUnreported())
                .isNull();
        }

        @Test
        void logsAStepThatThrewUnderKmusOwnLogger() {

            var integration = createIntegrationWith("kmu-test-logged-mod");

            var capturedLog = LogAppenderFake.captureLogOf(
                KmuWiringSteps.class,
                () -> KmuWiringSteps.runGuardedStep(buildThrowingStep(), FAILURE_MESSAGE, () -> integration));

            assertThat(capturedLog.getMessages())
                .anySatisfy(message -> assertThat(message).startsWith(FAILURE_MESSAGE));
        }

        @Test
        void recordsNothingWhereTheStepInstalled() {

            var integration = createIntegrationWith("kmu-test-installed-mod");

            KmuWiringSteps.runGuardedStep(() -> { }, FAILURE_MESSAGE, () -> integration);

            assertThat(CompatibilityFailures.SESSION_RECORD.takeNextUnreported())
                .isNull();
        }
    }

    private static Runnable buildThrowingStep() {
        return () -> {
            throw new IllegalStateException("the collaborator broke");
        };
    }

    // An integration under a third party named for the one case recording against it. The latch is
    // per third party and consumer for the whole run, so two cases sharing one would leave whichever
    // ran second recording nothing.
    private static ModIntegration createIntegrationWith(String subjectModId) {

        return new ModIntegration(
            subjectModId,
            "Test Mod",
            new CompatibilityConsumer(KmuMod.MOD_ID, "wiring-steps-test", "The test feature.", "Everything else."));
    }

    // Empties the process's own record. Left filled, the next case to read it finds a failure it
    // never filed.
    private static void drainTheSessionRecord() {

        while (CompatibilityFailures.SESSION_RECORD.takeNextUnreported() != null) {
            // drained for its side effect.
        }
    }
}
