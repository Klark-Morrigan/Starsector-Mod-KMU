package kmu.console;

import kmlib.profiling.Profiler;
import kmlib.profiling.SectionTiming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link KmuProfilingReportCommand}: a bare invocation prints the formatted
 * timings, {@code reset} clears the profiler and reports that it did, and a stray
 * argument is rejected as bad syntax without touching the profiler.
 */
final class KmuProfilingReportCommandTest {

    private static final String SECTION = "politicalMap.render";
    private static final long TWO_MILLIS_IN_NANOS = 2_000_000L;

    private final Profiler profilerMock = mock(Profiler.class);
    private final List<String> output = new ArrayList<>();
    private final KmuProfilingReportCommand command =
        new KmuProfilingReportCommand(profilerMock, output::add);

    @Nested
    class RunCommand {

        @Test
        void printsTheFormattedTimingsForABareInvocation() {

            when(profilerMock.snapshot()).thenReturn(List.of(
                new SectionTiming(
                    SECTION, 1, TWO_MILLIS_IN_NANOS, TWO_MILLIS_IN_NANOS, TWO_MILLIS_IN_NANOS)));

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
    }
}
