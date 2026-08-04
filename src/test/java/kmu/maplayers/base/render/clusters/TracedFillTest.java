package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.theme.ThemeFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a per-state fill offers its observer as it cuts each body: the seam between building
 * the hatch and reading something off it.
 *
 * <p>Worth its own suite because the run is the only place the join readings exist. What the build
 * hands on is a draw record carrying segments alone, so a run not offered here is not merely
 * unreported - it is gone, and no later pass could reconstruct it.
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

    @Nested
    class BuildFillFor {

        @Test
        void buildFillForOffersTheObserverTheRunItCutForThisBody() {
            var observed = new ArrayList<TimedHatchRun>();

            var fill = createPerFillState(List.of(SQUARE), observed::add)
                .buildFillFor(new RingRegion(SQUARE, List.of()));

            assertThat(observed)
                .hasSize(1);

            // The run offered is the one that went on to be drawn, not a second cut of the same
            // fill - so a reading taken off it describes what the frame actually shows.
            assertThat(observed.get(0).hatchRun().segments())
                .isSameAs(fill.hatchSegments());
        }

        @Test
        void buildFillForTimesTheCutItOffers() {
            var observed = new ArrayList<TimedHatchRun>();

            createPerFillState(List.of(SQUARE), observed::add)
                .buildFillFor(new RingRegion(SQUARE, List.of()));

            // Only that the clock ran and was read the right way round; how long a cut takes is
            // the machine's business, not this suite's.
            assertThat(observed.get(0).elapsedNanos())
                .isPositive();
        }

        @Test
        void buildFillForOffersNothingForABodyThatHatchesNothing() {
            var observed = new ArrayList<TimedHatchRun>();

            // A splitting owner whose members are all solid still cuts every body, and an observer
            // offered those would count bodies rather than hatches - which reads as a hatch that
            // came back empty for a reason rather than one that was never asked for.
            var fill = createPerFillState(List.of(), observed::add)
                .buildFillFor(new RingRegion(SQUARE, List.of()));

            assertThat(observed)
                .isEmpty();
            assertThat(fill.hatchSegments())
                .isEmpty();
        }
    }

    // A per-state fill hatching the given rings, with everything else it needs pinned: this suite's
    // subject is what reaches the observer, so the solid state is left empty and the hatch is laid
    // out to whatever crosses the square above.
    private static TracedFill.PerFillState createPerFillState(
            List<List<double[]>> hatchedRings,
            HatchRunObserver hatchRunObserver) {

        return new TracedFill.PerFillState(
            List.of(),
            hatchedRings,
            ThemeFixtures.createHatchStyle(
                HATCH_SPACING,
                HATCH_ANGLE_RADIANS,
                HATCH_WIDTH_PIXELS),
            hatchRunObserver);
    }
}
