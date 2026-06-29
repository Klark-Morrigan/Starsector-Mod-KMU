package kmu.console;

import kmlib.profiling.Profiler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link KmuProfilingReportCommand}: a bare invocation prints the formatted
 * timings, {@code reset} clears the profiler and reports that it did, and a stray
 * argument is rejected as bad syntax without touching the profiler.
 */
final class KmuProfilingReportCommandTest {

    @Nested
    class RunCommand {

        @Test
        void printsTheFormattedTimingsForABareInvocation() {
            var profiler = new Profiler();
            profiler.record("politicalMap.render", 2_000_000);
            var output = new ArrayList<String>();
            var command = new KmuProfilingReportCommand(profiler, output::add);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(output).hasSize(1);
            assertThat(output.get(0)).contains("politicalMap.render");
        }

        @Test
        void resetClearsTheProfilerAndConfirms() {
            var profiler = new Profiler();
            profiler.record("politicalMap.render", 2_000_000);
            var output = new ArrayList<String>();
            var command = new KmuProfilingReportCommand(profiler, output::add);

            var result = command.runCommand("reset", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(output).containsExactly("KMU timings reset.");
            assertThat(profiler.snapshot()).isEmpty();
        }

        @Test
        void rejectsAStrayArgumentAsBadSyntaxWithoutTouchingTheProfiler() {
            var profiler = new Profiler();
            profiler.record("politicalMap.render", 2_000_000);
            var output = new ArrayList<String>();
            var command = new KmuProfilingReportCommand(profiler, output::add);

            var result = command.runCommand("bogus", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.BAD_SYNTAX);
            assertThat(output).anyMatch(message -> message.contains("Too many arguments"));
            // A malformed invocation must not clear the timings it failed to read.
            assertThat(profiler.snapshot()).isNotEmpty();
        }
    }
}
