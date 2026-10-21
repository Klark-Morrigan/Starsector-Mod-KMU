package kmu.mods.console.commands;

import kmlib.mods.console.commands.BaseProfilingReportCommand;
import kmlib.mods.console.commands.output.CommandOutput;
import kmlib.profiling.Profiler;

import kmu.maplayers.base.render.MapFrameSections;

import java.util.function.Supplier;

/**
 * KMU's {@code kmu_profiling} console command: KMLib's timing report, named for KMU and divided per
 * frame by the beat KMU's map layers open once a frame.
 *
 * <p>Which beat a frame is counted by is named here rather than in the library: profiling knows a
 * row was opened some number of times, and which row a frame opens once belongs to whoever
 * opened it.
 */
public final class KmuProfilingReportCommand
        extends BaseProfilingReportCommand {

    private static final String COMMAND_NAME = "kmu_profiling";
    private static final String NOTICE_LABEL = "KMU";

    public KmuProfilingReportCommand() {
        super(COMMAND_NAME, NOTICE_LABEL, MapFrameSections.PREPARE);
    }

    KmuProfilingReportCommand(
            Supplier<Profiler> resolveBoundProfiler,
            CommandOutput output,
            CommandOutput log) {

        super(COMMAND_NAME, NOTICE_LABEL, MapFrameSections.PREPARE, resolveBoundProfiler, output, log);
    }
}
