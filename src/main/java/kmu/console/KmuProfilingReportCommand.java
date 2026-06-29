package kmu.console;

import kmlib.console.KmlibBaseConsoleCommand;
import kmlib.console.output.CommandOutput;
import kmlib.console.output.ConsoleCommandOutput;
import kmlib.console.parsing.Parameter;
import kmlib.console.parsing.ParameterSpec;
import kmlib.profiling.Profiler;
import kmlib.profiling.TimingReport;

import kmu.diagnostics.KmuProfiling;

import java.util.Objects;

/**
 * Console command that prints KMU's accumulated performance timings, or clears
 * them when invoked with {@code reset}.
 *
 * <p>The on-demand reporting surface for {@link KmuProfiling}'s profiler: it
 * formats the per-section stats (count, average / min / max / total ms) so the
 * cost of instrumented work - geometry build, ownership scan, per-frame draw -
 * can be read without flooding the log. The profiler and output sink are
 * injected so the formatting and reset behaviour test without the console.
 */
public final class KmuProfilingReportCommand extends KmlibBaseConsoleCommand {
    private static final KmuProfilingSpec SPEC = new KmuProfilingSpec();

    private final Profiler profiler;

    public KmuProfilingReportCommand() {
        this(KmuProfiling.getProfiler(), ConsoleCommandOutput.INSTANCE);
    }

    KmuProfilingReportCommand(Profiler profiler, CommandOutput output) {
        super(output);
        this.profiler = Objects.requireNonNull(profiler, "profiler");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        var parsed = SPEC.parse(args, output);
        if (!parsed.isValid()) {
            return parsed.getResult();
        }
        if (parsed.get(SPEC.reset)) {
            profiler.reset();
            output.showMessage("KMU timings reset.");
            return CommandResult.SUCCESS;
        }

        output.showMessage(TimingReport.format(profiler.snapshot()));
        return CommandResult.SUCCESS;
    }

    /**
     * What {@code kmu_profiling} accepts: a lone {@code reset} flag that clears
     * the accumulated timings instead of printing them. A flag rather than a
     * value, so it is given as the bare keyword and absent means print.
     */
    private static final class KmuProfilingSpec extends ParameterSpec {
        private final Parameter<Boolean> reset = acceptsFlag("reset");

        private KmuProfilingSpec() {
            super("Usage: kmu_profiling [reset].");
        }
    }
}
