package kmu.console;

import kmlib.console.KmlibBaseConsoleCommand;
import kmlib.console.output.CommandOutput;
import kmlib.console.output.ConsoleCommandOutput;
import kmlib.console.parsing.Parameter;
import kmlib.console.parsing.ParameterSpec;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.Profiler;
import kmlib.profiling.TimingReport;

import java.util.Objects;

/**
 * Console command that prints KMU's accumulated performance timings, or clears
 * them when invoked with {@code reset}.
 *
 * <p>The on-demand reporting surface for whichever profiler is bound: it
 * formats the per-section stats (count, average / min / max / total ms) so the
 * cost of instrumented work - geometry build, ownership scan, per-frame draw -
 * can be read without flooding the log. The profiler and output sink arrive as
 * constructor arguments rather than being reached for, so a caller can point
 * the same formatting and reset rules at a different profiler or a different
 * sink.
 */
public final class KmuProfilingReportCommand extends KmlibBaseConsoleCommand {
    private static final KmuProfilingSpec SPEC = new KmuProfilingSpec();

    private final Profiler profiler;

    public KmuProfilingReportCommand() {
        this(ActiveProfiler.resolveProfiler(), ConsoleCommandOutput.INSTANCE);
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
