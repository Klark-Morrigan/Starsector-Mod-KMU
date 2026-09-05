package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.profiling.ProfileTiming;
import kmlib.profiling.Profiler;
import kmlib.profiling.RecordingProfiler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pins that a bake's four costs reach the readout apart from one another, and that a pass is one
 * run of each.
 *
 * <p>Both halves are the point of splitting the measurement. A bake traces, carves, counts and
 * strokes, and those grow on different axes - the trace with the cells, the carve with the cells
 * times the names, the count with what the systems hold - so a total that merges them can say a
 * bake got slower without saying which of them did. And a pass's cells are summed into one run
 * rather than recorded one by one, so the average in the readout is what a bake costs; recorded
 * per cell, the profiler's own bookkeeping would be a share of what it reported.
 */
final class RibbonBakeTimingsTest {

    private static final String PLAN_SECTION = "politicalMap.bakeRibbons.plan";
    private static final String TRACE_SECTION = "politicalMap.bakeRibbons.trace";
    private static final String CARVE_SECTION = "politicalMap.bakeRibbons.carve";
    private static final String STROKE_SECTION = "politicalMap.bakeRibbons.stroke";

    private final RibbonBakeTimings timings = new RibbonBakeTimings();
    private final Profiler profiler = new RecordingProfiler();

    @Nested
    class RecordPhaseTotals {

        @Test
        void recordsEachPhaseUnderItsOwnSection() {
            // Four distinct durations, so a phase recorded under another's name shows as a
            // number in the wrong row rather than as a total that happens to add up.
            timings.addPlanNanos(11L);
            timings.addTraceNanos(22L);
            timings.addCarveNanos(33L);
            timings.addStrokeNanos(44L);

            timings.recordPhaseTotals(profiler);

            assertThat(profiler.snapshot())
                .extracting(node -> node.getSection().getName(), node -> node.getTiming().getTotalNanos())
                .containsExactly(
                    tuple(PLAN_SECTION, 11L),
                    tuple(TRACE_SECTION, 22L),
                    tuple(CARVE_SECTION, 33L),
                    tuple(STROKE_SECTION, 44L));
        }

        @Test
        void sumsWhatEveryCellOfAPassSpentOnOnePhase() {
            // Three cells' traces, which is what the loop hands over: the pass adds into a long
            // per phase and the profiler is told once.
            timings.addTraceNanos(100L);
            timings.addTraceNanos(200L);
            timings.addTraceNanos(300L);

            timings.recordPhaseTotals(profiler);

            assertThat(readSectionTiming(TRACE_SECTION).getTotalNanos())
                .isEqualTo(600L);
        }

        @Test
        void recordsAWholePassAsOneRun() {
            // What makes the readout's average a bake rather than a cell. Two cells go in and
            // one run comes out, so a sector's cell count cannot flatten the number a reader
            // compares against the label fit.
            timings.addCarveNanos(400L);
            timings.addCarveNanos(600L);

            timings.recordPhaseTotals(profiler);

            assertThat(readSectionTiming(CARVE_SECTION).getCount())
                .isEqualTo(1L);
            assertThat(readSectionTiming(CARVE_SECTION).getAverageNanos())
                .isEqualTo(1000L);
        }

        @Test
        void recordsAPhaseThatCostNothingRatherThanLeavingItOut() {
            // A pass with the bands switched off spends nothing anywhere, and a row of zeroes is
            // what says so - an absent row reads as a phase nobody measured.
            timings.recordPhaseTotals(profiler);

            assertThat(profiler.snapshot())
                .extracting(node -> node.getSection().getName(), node -> node.getTiming().getTotalNanos())
                .containsExactly(
                    tuple(PLAN_SECTION, 0L),
                    tuple(TRACE_SECTION, 0L),
                    tuple(CARVE_SECTION, 0L),
                    tuple(STROKE_SECTION, 0L));
        }

        @Test
        void keepsEachPassOfARebuildAsItsOwnRun() {
            // A full rebuild bakes once and an incremental refresh bakes again, each through its
            // own accumulator; the profiler holds both, so the slowest is the worst bake rather
            // than the sum of every bake since the game loaded.
            timings.addStrokeNanos(700L);
            timings.recordPhaseTotals(profiler);

            var laterPass = new RibbonBakeTimings();

            laterPass.addStrokeNanos(300L);
            laterPass.recordPhaseTotals(profiler);

            assertThat(readSectionTiming(STROKE_SECTION).getCount())
                .isEqualTo(2L);
            assertThat(readSectionTiming(STROKE_SECTION).getMaxNanos())
                .isEqualTo(700L);
        }
    }

    @Nested
    class DescribePhaseTotals {

        @Test
        void namesEveryPhaseAndItsMillisecondsInTheOrderACellPaysThem() {
            // The line a reader watching a rebuild in the log sees. Each phase is named where its
            // number is, since four bare durations on one line say nothing about which is which.
            timings.addPlanNanos(1_500_000L);
            timings.addTraceNanos(250_000L);
            timings.addCarveNanos(12_000_000L);

            assertThat(timings.describePhaseTotals())
                .isEqualTo("plan=1.50ms trace=0.25ms carve=12.00ms stroke=0.00ms");
        }
    }

    private ProfileTiming readSectionTiming(String section) {
        return profiler
            .snapshot()
            .stream()
            .filter(node -> node.getSection().getName().equals(section))
            .findFirst()
            .orElseThrow()
            .getTiming();
    }
}
