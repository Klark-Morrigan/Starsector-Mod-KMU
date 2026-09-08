package kmu.console;

import kmlib.profiling.ProfileOrigin;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.Profiler;
import kmlib.profiling.snapshot.BudgetBreach;
import kmlib.profiling.snapshot.DurationBuckets;
import kmlib.profiling.snapshot.ProfileIterations;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.profiling.snapshot.ProfileOriginTree;
import kmlib.profiling.snapshot.ProfileTiming;
import kmlib.profiling.snapshot.WorstCall;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link KmuProfilingReportCommand}: a bare invocation prints the formatted
 * timings, {@code reset} clears the profiler and reports that it did, a stray
 * argument is rejected as bad syntax without touching the profiler, and every
 * invocation acts on whichever profiler is bound at the moment it runs.
 */
final class KmuProfilingReportCommandTest {

    private static final String SECTION = "politicalMap.render";
    private static final String ORIGIN_LABEL = "MN-6220 - Marat";
    private static final long TWO_MILLIS_IN_NANOS = 2_000_000L;

    private final Profiler profilerMock = mock(Profiler.class);
    private final List<String> output = new ArrayList<>();
    // What the holder answers with, so a case can rebind between building the command and running
    // it - which is what the level knob does in play.
    private final AtomicReference<Profiler> boundProfiler = new AtomicReference<>(profilerMock);

    private final KmuProfilingReportCommand command =
        new KmuProfilingReportCommand(boundProfiler::get, output::add);

    @Nested
    class RunCommand {

        @Test
        void printsTheFormattedTimingsForABareInvocation() {

            when(profilerMock.snapshot()).thenReturn(List.of(new ProfileOriginTree(
                ProfileOrigin.registerOrigin(ORIGIN_LABEL),
                List.of(new ProfileNode(
                    ProfileSection.registerSection(SECTION),
                    new ProfileTiming(
                        1,
                        TWO_MILLIS_IN_NANOS,
                        TWO_MILLIS_IN_NANOS,
                        TWO_MILLIS_IN_NANOS,
                        DurationBuckets.NO_CALLS),
                    WorstCall.NO_CALL,
                    BudgetBreach.NO_BREACH,
                    ProfileIterations.NO_ITERATIONS,
                    List.of(),
                    List.of())))));

            var result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(output).hasSize(1);
            assertThat(output.get(0)).contains(SECTION);
        }

        @Test
        void resetClearsTheProfilerAndConfirms() {

            var result = command.runCommand("reset", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(output).containsExactly("KMU timings reset.");
            verify(profilerMock).reset();
        }

        @Test
        void rejectsAStrayArgumentAsBadSyntaxWithoutTouchingTheProfiler() {

            var result = command.runCommand("bogus", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.BAD_SYNTAX);
            assertThat(output).anyMatch(message -> message.contains("Too many arguments"));
            // A malformed invocation must not clear the timings it failed to read.
            verify(profilerMock, never()).reset();
        }

        @Test
        void readsWhicheverProfilerIsBoundWhenItRuns() {
            // The level knob rebinds the profiler mid-session, so a command that held the one it
            // was built with would report the capture the player has just switched away from.
            var reboundProfilerMock = mock(Profiler.class);

            boundProfiler.set(reboundProfilerMock);
            command.runCommand("reset", CommandContext.CAMPAIGN_MAP);

            verify(reboundProfilerMock).reset();
            verify(profilerMock, never()).reset();
        }
    }
}
