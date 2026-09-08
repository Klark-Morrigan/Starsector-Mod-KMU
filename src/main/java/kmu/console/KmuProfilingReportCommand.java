package kmu.console;

import kmlib.console.KmlibBaseConsoleCommand;
import kmlib.console.output.CommandOutput;
import kmlib.console.output.ConsoleCommandOutput;
import kmlib.console.parsing.Parameter;
import kmlib.console.parsing.ParameterSpec;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.Profiler;
import kmlib.profiling.report.TimingReport;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Console command that prints KMU's accumulated performance timings, or clears
 * them when invoked with {@code reset}.
 *
 * <p>The on-demand reporting surface for whichever profiler is bound: it
 * formats the section tree (count, average / min / max / self / total ms, each
 * row indented under the section it ran inside, each group of roots headed by
 * the sector it was measured in) so the cost of instrumented work - geometry
 * build, ownership scan, per-frame draw - can be read without flooding the log,
 * and a slow row can be told from a slow thing beneath it.
 *
 * <p>The profiler is resolved per invocation rather than held, because the level
 * knob rebinds it: a command holding the profiler it was built with would report
 * the capture the player switched away from. The lookup and the output sink
 * arrive as constructor arguments rather than being reached for, so a caller can
 * point the same formatting and reset rules at a different profiler or a
 * different sink.
 */
public final class KmuProfilingReportCommand extends KmlibBaseConsoleCommand {
    private static final KmuProfilingSpec SPEC = new KmuProfilingSpec();

    private final Supplier<Profiler> resolveBoundProfiler;

    public KmuProfilingReportCommand() {
        this(ActiveProfiler::resolveProfiler, ConsoleCommandOutput.INSTANCE);
    }

    KmuProfilingReportCommand(Supplier<Profiler> resolveBoundProfiler, CommandOutput output) {
        super(output);
        this.resolveBoundProfiler =
            Objects.requireNonNull(resolveBoundProfiler, "resolveBoundProfiler");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        var parsed = SPEC.parse(args, output);
        if (!parsed.isValid()) {
            return parsed.getResult();
        }
        var profiler = resolveBoundProfiler.get();

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
