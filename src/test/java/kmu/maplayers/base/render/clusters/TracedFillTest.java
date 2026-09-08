package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;
import kmlib.opengl.GlVertexRuns;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.SilentProfiler;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;

import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.theme.ThemeFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a per-state fill records about each body's cut: how many strokes it came back as, and
 * how its joins closed.
 *
 * <p>Worth its own suite because the run is the only place the join readings exist. What the build
 * hands on is a draw record carrying segments alone, so a reading not taken as the body is cut is
 * not merely unreported - it is gone, and no later pass could reconstruct it.
 */
final class TracedFillTest {

    // A square large enough that a hatch at the spacing below crosses it several times, so a body
    // that hatches comes back with segments rather than with a single degenerate touch.
    private static final List<double[]> SQUARE = List.of(
        new double[] {0, 0},
        new double[] {1000, 0},
        new double[] {1000, 1000},
        new double[] {0, 1000});

    private static final double HATCH_SPACING = 200.0;
    private static final double HATCH_ANGLE_RADIANS = 0;
    private static final double HATCH_WIDTH_PIXELS = 1.0;

    private static final String CUT_HATCH_SECTION = "mapLayer.cutHatch";

    // Every case leaves profiling as it found it, since the holder is process-wide: a recording
    // profiler left bound would follow the next case into a capture it never asked for.
    @AfterEach
    void releaseBoundProfiler() {
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
    }

    @Nested
    class BuildFillFor {

        @Test
        void countsTheStrokesItCutForThisBody() {
            var profiler = new RecordingProfiler();

            ActiveProfiler.bindProfiler(profiler);

            var fill = createPerFillState(List.of(SQUARE))
                .buildFillFor(new RingRegion(SQUARE, List.of()));

            // Counted off the run that went on to be drawn, not off a second cut of the same fill -
            // so the number in the row describes what the frame actually shows.
            assertThat(readCutSegments(profiler))
                .isEqualTo(fill.hatchSegments().length / GlVertexRuns.FLOATS_PER_SEGMENT);
        }

        @Test
        void namesTheCallWithHowItsJoinsClosed() {
            // The readings that settle whether the join tolerance is load-bearing, which exist
            // nowhere but on the run this call held.
            var profiler = new RecordingProfiler();

            ActiveProfiler.bindProfiler(profiler);

            createPerFillState(List.of(SQUARE))
                .buildFillFor(new RingRegion(SQUARE, List.of()));

            assertThat(readCutRow(profiler).getWorstCall().getTag())
                .contains("exactJoins=")
                .contains("widestClosedGap=");
        }

        @Test
        void recordsABodyThatHatchesNothingAsACutOfNoStrokes() {
            // A splitting owner whose members are all solid still cuts every body. The cut is
            // recorded, since it happened and cost something; what tells it from a body whose
            // hatch came out empty for a reason is the count, which is why it is a count.
            var profiler = new RecordingProfiler();

            ActiveProfiler.bindProfiler(profiler);

            var fill = createPerFillState(List.of())
                .buildFillFor(new RingRegion(SQUARE, List.of()));

            assertThat(readCutSegments(profiler))
                .isZero();
            assertThat(readCutRow(profiler).getWorstCall().getTag())
                .isEmpty();
            assertThat(fill.hatchSegments())
                .isEmpty();
        }
    }

    // What the one cut this suite's subject made counted.
    private static long readCutSegments(RecordingProfiler profiler) {
        return readCutRow(profiler)
            .findCount(MapBuildCounters.HATCH_SEGMENTS)
            .getTotals()
            .getTotal();
    }

    // The row the cut landed on. Opened with nothing else running, so it is the one root of the
    // one origin the capture holds.
    private static ProfileNode readCutRow(RecordingProfiler profiler) {

        var cutRow = profiler.snapshot().get(0).getRoots().get(0);

        assertThat(cutRow.getSection().getName())
            .isEqualTo(CUT_HATCH_SECTION);

        return cutRow;
    }

    // A per-state fill hatching the given rings, with everything else it needs pinned: this suite's
    // subject is what the cut records, so the solid state is left empty and the hatch is laid out
    // to whatever crosses the square above.
    private static TracedFill.PerFillState createPerFillState(List<List<double[]>> hatchedRings) {

        return new TracedFill.PerFillState(
            List.of(),
            hatchedRings,
            ThemeFixtures.createHatchStyle(
                HATCH_SPACING,
                HATCH_ANGLE_RADIANS,
                HATCH_WIDTH_PIXELS));
    }
}
