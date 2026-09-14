package kmu.mods.console.commands;

import kmlib.profiling.ProfileOrigin;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.Profiler;
import kmlib.profiling.snapshot.BudgetBreach;
import kmlib.profiling.snapshot.CountTotals;
import kmlib.profiling.snapshot.DurationBuckets;
import kmlib.profiling.snapshot.ProfileCount;
import kmlib.profiling.snapshot.ProfileIterations;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.profiling.snapshot.ProfileOriginTree;
import kmlib.profiling.snapshot.ProfileTiming;
import kmlib.profiling.snapshot.WorstCall;
import kmlib.starsector.SectorWalkCounters;
import kmlib.testfixtures.mods.console.commands.output.CommandOutputFake;

import kmu.maplayers.base.render.MapFrameSections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link KmuProfilingReportCommand}: a bare invocation writes the capture as it was measured
 * to the game log and tells the overlay where it landed, a named view writes the reading it names,
 * the frame flag divides the totals by the beat the framework opens once a frame, {@code reset}
 * clears the profiler and reports that it did, a stray argument is rejected as bad syntax without
 * touching the profiler, and every invocation acts on whichever profiler is bound at the moment it
 * runs.
 */
final class KmuProfilingReportCommandTest {

    private static final String SECTION = "politicalMap.render";
    private static final String CHILD_SECTION = "politicalMap.walk";
    private static final String ORIGIN_LABEL = "MN-6220 - Marat";
    private static final long TWO_MILLIS_IN_NANOS = 2_000_000L;
    private static final long ONE_MILLI_IN_NANOS = 1_000_000L;

    // Two calls of the beat the framework opens once a frame, so a per-frame report divides by a
    // number the rows themselves never state.
    private static final long FRAMES = 2L;

    private static final long TWO_WALKS = 2L;

    private static final String TIMINGS_LOGGED_NOTICE =
        "KMU timings written to the game log (starsector-core/starsector.log).";

    private final Profiler profilerMock = mock(Profiler.class);
    private final CommandOutputFake outputFake = new CommandOutputFake();
    private final CommandOutputFake logFake = new CommandOutputFake();
    // What the holder answers with, so a case can rebind between building the command and running
    // it - which is what the level knob does in play.
    private final AtomicReference<Profiler> boundProfiler = new AtomicReference<>(profilerMock);

    private final KmuProfilingReportCommand command =
        new KmuProfilingReportCommand(boundProfiler::get, outputFake, logFake);

    @Nested
    class RunCommand {

        @Test
        void writesTheFormattedTimingsToTheGameLogForABareInvocation() {
            // Where a capture has to end up: a table wider than the overlay can lay out is
            // unreadable there and properly aligned in the log, which is the file a player
            // attaches anyway. The overlay gets told where the reading went rather than nothing,
            // since a command that visibly did nothing reads as one that failed.
            when(profilerMock.snapshot()).thenReturn(captureOf(nodeOf(SECTION)));

            var result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(logFake.getMessages()).hasSize(1);
            assertThat(logFake.getMessages().get(0)).contains(SECTION);
            assertThat(outputFake.getMessages()).containsExactly(TIMINGS_LOGGED_NOTICE);
        }

        @Test
        void writesTheListingWhenOneIsNamed() {
            // The console word reaching the reading it names: a listing takes the rows out of the
            // tree, so the child is named by its whole path rather than indented under its parent.
            when(profilerMock.snapshot()).thenReturn(captureOf(parentHoldingOneChild()));

            command.runCommand("flat", CommandContext.CAMPAIGN_MAP);

            assertThat(logFake.getMessages().get(0)).contains(SECTION + "/" + CHILD_SECTION);
        }

        @Test
        void writesOnlyTheRowsThatWalkedWhenTheWalksListingIsNamed() {
            // Which counter "walks" means is this command's to say: the library knows a row counted
            // something and not that the something was a traversal of a sector.
            when(profilerMock.snapshot()).thenReturn(captureOf(nodeCountingWalks(), nodeOf(
                CHILD_SECTION)));

            command.runCommand("walks", CommandContext.CAMPAIGN_MAP);

            assertThat(logFake.getMessages().get(0))
                .contains(SECTION)
                .doesNotContain(CHILD_SECTION);
        }

        @Test
        void dividesTheTotalsByTheFrameBeatWhenAskedForPerFrameFigures() {
            // Which beat a frame is counted by is this command's to say as well, and it is the one
            // the framework opens once per frame.
            when(profilerMock.snapshot()).thenReturn(captureOf(frameBeatNode()));

            command.runCommand("perframe", CommandContext.CAMPAIGN_MAP);

            assertThat(logFake.getMessages().get(0))
                .contains("per frame, over 2 of " + MapFrameSections.PREPARE.getName());
        }

        @Test
        void resetClearsTheProfilerAndConfirms() {

            var result = command.runCommand("reset", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(outputFake.getMessages()).containsExactly("KMU timings reset.");
            verify(profilerMock).reset();
        }

        @Test
        void rejectsAStrayArgumentAsBadSyntaxWithoutTouchingTheProfiler() {

            var result = command.runCommand("bogus", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.BAD_SYNTAX);
            assertThat(outputFake.getMessages())
                .anyMatch(message -> message.contains("Invalid view 'bogus'"));
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

    private static List<ProfileOriginTree> captureOf(ProfileNode... roots) {
        return List.of(new ProfileOriginTree(
            ProfileOrigin.registerOrigin(ORIGIN_LABEL), List.of(roots)));
    }

    private static ProfileNode nodeOf(String sectionName) {
        return nodeOf(ProfileSection.registerSection(sectionName), 1, List.of(), List.of());
    }

    // One call of a section holding one call of another, which is the smallest capture whose rows
    // are named differently by the tree and by a listing.
    private static ProfileNode parentHoldingOneChild() {
        return nodeOf(
            ProfileSection.registerSection(SECTION),
            1,
            List.of(),
            List.of(nodeOf(CHILD_SECTION)));
    }

    private static ProfileNode nodeCountingWalks() {
        return nodeOf(
            ProfileSection.registerSection(SECTION),
            1,
            List.of(new ProfileCount(
                SectorWalkCounters.SECTOR_WALKS,
                new CountTotals(TWO_WALKS, TWO_WALKS),
                TWO_WALKS)),
            List.of());
    }

    private static ProfileNode frameBeatNode() {
        return nodeOf(MapFrameSections.PREPARE, FRAMES, List.of(), List.of());
    }

    private static ProfileNode nodeOf(
            ProfileSection section,
            long calls,
            List<ProfileCount> counts,
            List<ProfileNode> children) {

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
            counts,
            children);
    }
}
