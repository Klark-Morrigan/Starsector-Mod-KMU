package kmu.mods.console.commands;

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
import kmlib.testfixtures.mods.console.commands.output.CommandOutputFake;

import kmu.maplayers.base.render.MapFrameSections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what {@link KmuProfilingReportCommand} decides over KMLib's report command: the console word
 * its usage line names, the label its notices carry, and the beat its per-frame figures divide by.
 * How the report reads, narrows and resets is KMLib's to pin.
 */
final class KmuProfilingReportCommandTests {

    private static final String ORIGIN_LABEL = "MN-6220 - Marat";
    private static final long TWO_MILLIS_IN_NANOS = 2_000_000L;
    private static final long ONE_MILLI_IN_NANOS = 1_000_000L;

    // Two calls of the beat the framework opens once a frame, so a per-frame report divides by a
    // number the rows themselves never state.
    private static final long FRAMES = 2L;

    private final Profiler profilerMock = mock(Profiler.class);
    private final CommandOutputFake outputFake = new CommandOutputFake();
    private final CommandOutputFake logFake = new CommandOutputFake();

    private final KmuProfilingReportCommand command =
        new KmuProfilingReportCommand(() -> profilerMock, outputFake, logFake);

    @Nested
    class RunCommand {

        @Test
        void namesTheTimingsKmuWhenTellingTheOverlayWhereTheyLanded() {

            when(profilerMock.snapshot()).thenReturn(captureOf(frameBeatNode()));

            command.runCommand("", CommandContext.CAMPAIGN_MAP);

            assertThat(outputFake.getMessages()).containsExactly(
                "KMU timings written to the game log (starsector-core/starsector.log).");
        }

        @Test
        void namesTheTimingsKmuWhenConfirmingAReset() {

            command.runCommand("reset", CommandContext.CAMPAIGN_MAP);

            assertThat(outputFake.getMessages()).containsExactly("KMU timings reset.");
        }

        @Test
        void dividesTheTotalsByTheMapFrameBeatWhenAskedForPerFrameFigures() {
            // Which beat a frame is counted by is this command's to say, and it is the one the map
            // layers open once per frame.
            when(profilerMock.snapshot()).thenReturn(captureOf(frameBeatNode()));

            command.runCommand("perframe", CommandContext.CAMPAIGN_MAP);

            assertThat(logFake.getMessages().get(0))
                .contains("per frame, over 2 of mapLayer.prepare");
        }

        @Test
        void printsTheUsageTheShippedTablePublishesWhenGivenTooManyViews() {
            // The same grammar is typed by hand into data/console/commands.csv, so the literal here
            // is that row's syntax column.
            command.runCommand("tree flat", CommandContext.CAMPAIGN_MAP);

            assertThat(outputFake.getMessages()).anyMatch(message -> message.contains(
                "Usage: kmu_profiling [tree|flat|walks] [namespace=<prefix>] [top=<count>]"
                    + " [perframe], or kmu_profiling reset."));
        }
    }

    private static List<ProfileOriginTree> captureOf(ProfileNode... roots) {
        return List.of(new ProfileOriginTree(
            ProfileOrigin.registerOrigin(ORIGIN_LABEL), List.of(roots)));
    }

    private static ProfileNode frameBeatNode() {
        return nodeOf(MapFrameSections.PREPARE, FRAMES);
    }

    private static ProfileNode nodeOf(ProfileSection section, long calls) {

        return new ProfileNode(
            section,
            new ProfileTiming(
                calls,
                TWO_MILLIS_IN_NANOS,
                ONE_MILLI_IN_NANOS,
                TWO_MILLIS_IN_NANOS,
                DurationBuckets.NO_CALLS),
            WorstCall.NO_CALL,
            BudgetBreach.NO_BREACH,
            ProfileIterations.NO_ITERATIONS,
            List.of(),
            List.of());
    }
}
