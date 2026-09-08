package kmu.console;

import kmlib.console.KmlibBaseConsoleCommand;
import kmlib.console.output.CommandOutput;
import kmlib.console.output.ConsoleCommandOutput;
import kmlib.console.output.GameLogCommandOutput;
import kmlib.console.parsing.Parameter;
import kmlib.console.parsing.ParameterSpec;
import kmlib.console.parsing.ParameterValues;
import kmlib.console.parsing.ParsedParameters;
import kmlib.console.parsing.ValueParseException;
import kmlib.console.parsing.ValueParser;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.Profiler;
import kmlib.profiling.report.ProfileReportRequest;
import kmlib.profiling.report.TimingReport;
import kmlib.starsector.SectorWalkCounters;

import kmu.maplayers.base.render.MapFrameSections;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Console command that prints KMU's accumulated performance timings, or clears
 * them when invoked with {@code reset}.
 *
 * <p>The on-demand reporting surface for whichever profiler is bound. Three
 * readings of one capture, because three different questions are asked of it:
 * the tree says what a total is made of, the listing says which row to open
 * next, and the walks listing says which pass went looking for the sector. A
 * namespace and a row count narrow any of them to what is being investigated,
 * and the per-frame division turns a session's totals into what one frame
 * spends.
 *
 * <p>Which counter "walks" means and which beat a frame is counted by are named
 * here rather than in the library: profiling knows that a row counted something,
 * and what the something is belongs to whoever counted it.
 *
 * <p>The profiler is resolved per invocation rather than held, because the level
 * knob rebinds it: a command holding the profiler it was built with would report
 * the capture the player switched away from. The lookup and the two output sinks
 * arrive as constructor arguments rather than being reached for, so a caller can
 * point the same formatting and reset rules at a different profiler or a
 * different sink.
 */
public final class KmuProfilingReportCommand extends KmlibBaseConsoleCommand {
    private static final KmuProfilingSpec SPEC = new KmuProfilingSpec();

    private static final String TIMINGS_RESET_NOTICE = "KMU timings reset.";
    private static final String TIMINGS_LOGGED_NOTICE =
        "KMU timings written to the game log (starsector.log).";

    private final Supplier<Profiler> resolveBoundProfiler;
    private final CommandOutput log;

    public KmuProfilingReportCommand() {
        this(
            ActiveProfiler::resolveProfiler,
            ConsoleCommandOutput.INSTANCE,
            GameLogCommandOutput.INSTANCE);
    }

    KmuProfilingReportCommand(
            Supplier<Profiler> resolveBoundProfiler,
            CommandOutput output,
            CommandOutput log) {

        super(output);
        this.resolveBoundProfiler =
            Objects.requireNonNull(resolveBoundProfiler, "resolveBoundProfiler");
        this.log = Objects.requireNonNull(log, "log");
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
            output.showMessage(TIMINGS_RESET_NOTICE);
            return CommandResult.SUCCESS;
        }
        var report = TimingReport.format(profiler.snapshot(), buildRequest(parsed));

        // To the log or to the overlay, never to both: two copies of one capture
        // is one of them read against a table nobody meant to compare it with.
        if (parsed.get(SPEC.log)) {
            log.showMessage(report);
            output.showMessage(TIMINGS_LOGGED_NOTICE);
        } else {
            output.showMessage(report);
        }
        return CommandResult.SUCCESS;
    }

    // The reading, narrowed by whatever else was asked for. Each narrowing states
    // its own "everything" default, so an invocation that named only a view asks
    // for the whole capture without the command spelling that out.
    private static ProfileReportRequest buildRequest(ParsedParameters parsed) {

        var request = parsed.get(SPEC.view)
            .limitToNamespace(parsed.get(SPEC.namespace))
            .limitToTopRows(parsed.get(SPEC.top));

        return parsed.get(SPEC.perFrame)
            ? request.divideByFramesOf(MapFrameSections.PREPARE)
            : request;
    }

    /**
     * What {@code kmu_profiling} accepts: which reading of the capture to write,
     * how to narrow it, whether to divide it by the frames it was measured over,
     * where to write it, and a {@code reset} that clears the timings instead of
     * reporting them.
     *
     * <p>The reading is a positional, being the one thing an invocation is
     * mostly about; the narrowings are named, since two numbers given
     * positionally could not be told apart; and the sink and the reset are bare
     * keywords, absence being the answer for both.
     */
    private static final class KmuProfilingSpec extends ParameterSpec {

        // What each reading is asked for by. Values rather than an enum: the
        // words are a console vocabulary, and each maps to the request that
        // answers it.
        private static final String TREE_VIEW = "tree";
        private static final String FLAT_VIEW = "flat";
        private static final String WALKS_VIEW = "walks";

        private static final String VIEW_HINT =
            "<" + TREE_VIEW + "|" + FLAT_VIEW + "|" + WALKS_VIEW + ">";

        private static final String EXPECTED_VIEW =
            "one of " + TREE_VIEW + ", " + FLAT_VIEW + ", " + WALKS_VIEW;

        private static final String EXPECTED_TOP = "a whole number of rows above zero";

        private final Parameter<ProfileReportRequest> view =
            acceptsPositional("view", VIEW_HINT, parseView())
                .defaultsTo(ProfileReportRequest.showTree());

        private final Parameter<String> namespace =
            acceptsNamed("namespace", "<prefix>", ParameterValues.text())
                .defaultsTo(ProfileReportRequest.EVERY_NAMESPACE);

        private final Parameter<Integer> top =
            acceptsNamed("top", "<count>", ParameterValues.positiveWholeNumber(EXPECTED_TOP))
                .defaultsTo(ProfileReportRequest.EVERY_ROW);

        private final Parameter<Boolean> perFrame = acceptsFlag("perframe");
        private final Parameter<Boolean> log = acceptsFlag("log");
        private final Parameter<Boolean> reset = acceptsFlag("reset");

        private KmuProfilingSpec() {
            super("Usage: kmu_profiling [" + TREE_VIEW + "|" + FLAT_VIEW + "|" + WALKS_VIEW
                + "] [namespace=<prefix>] [top=<count>] [perframe] [log] [reset].");
        }

        // The counter the walks reading sorts on is the sector's, which is why the
        // reading is named here and not in the library: what a walk is belongs to
        // whoever walked.
        private static ValueParser<ProfileReportRequest> parseView() {

            return raw -> switch (raw.toLowerCase(Locale.ROOT)) {
                case TREE_VIEW -> ProfileReportRequest.showTree();
                case FLAT_VIEW -> ProfileReportRequest.showRowsBySelfTime();
                case WALKS_VIEW ->
                    ProfileReportRequest.showRowsCounting(SectorWalkCounters.SECTOR_WALKS);
                default -> throw new ValueParseException(EXPECTED_VIEW);
            };
        }
    }
}
