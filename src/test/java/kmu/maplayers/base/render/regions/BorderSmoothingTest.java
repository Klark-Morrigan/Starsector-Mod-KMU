package kmu.maplayers.base.render.regions;

import kmu.maplayers.base.theme.BorderSmoothingStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the smoothing passes contribute over the geometry library they call: that a pass
 * works to the profile it is handed and to nothing ambient, that the composed pass honours the
 * profile's gates and runs sanding before rounding, and that one loop rounded on its own comes
 * out exactly as it does inside a set of loops.
 *
 * <p>The last of those is the invariant that keeps a factionless cell's outline flush with the
 * cluster border beside it. It can only hold while both reach the rounding through one profile,
 * so it is asserted rather than left to the two call sites to keep in step.
 *
 * <p>No settings mock anywhere here, which is the point: a pass that reads its numbers from its
 * argument needs no ambient state to be exercised.
 */
class BorderSmoothingTest {
    // A ring with four sharp right-angle corners and no slivers: rounding has something to do
    // to it and sanding has nothing, so the two passes are told apart by their effect.
    private static final List<double[]> SQUARE = List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 100},
            new double[] {0, 100});

    // The same ring with a needle at the top edge: the apex sits 2 units above its neighbours'
    // chord and turns through about 53 degrees, so it clears both of sanding's bars while the
    // corners either side of it are too obtuse to be candidates.
    private static final double NEEDLE_APEX_Y = 102;
    private static final List<double[]> NEEDLED = List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 100},
            new double[] {51, 100},
            new double[] {50, NEEDLE_APEX_Y},
            new double[] {49, 100},
            new double[] {0, 100});

    // Sanding thresholds that admit the needle: it protrudes 2 units and turns through ~0.93
    // radians, so a 5-unit height bar and a 1.2-radian angle bar both clear it.
    private static final double SPIKE_HEIGHT = 5.0;
    private static final double SPIKE_ANGLE_RADIANS = 1.2;

    // A corner shape that visibly rounds: a positive radius and segment count, with the chamfer
    // threshold off (non-positive) so every corner arcs rather than some being cut flat.
    private static final double CORNER_RADIUS = 10.0;
    private static final int CORNER_SEGMENTS = 3;
    private static final double NO_CHAMFER = 0.0;

    // A second radius, used to prove a pass answers to the profile it is given rather than to
    // one fixed set of numbers.
    private static final double WIDER_CORNER_RADIUS = 25.0;

    private static final BorderSmoothingStyle BOTH_GATES_OFF = styleWithGates(false, false);
    private static final BorderSmoothingStyle SANDING_ONLY = styleWithGates(true, false);
    private static final BorderSmoothingStyle ROUNDING_ONLY = styleWithGates(false, true);
    private static final BorderSmoothingStyle BOTH_GATES_ON = styleWithGates(true, true);

    // The profile with both gates as asked and one shared shape, so a test naming a gate is not
    // also silently choosing a radius.
    private static BorderSmoothingStyle styleWithGates(
            boolean shouldSandSpikes,
            boolean shouldRoundCorners) {

        return new BorderSmoothingStyle(
                shouldSandSpikes,
                shouldRoundCorners,
                SPIKE_HEIGHT,
                SPIKE_ANGLE_RADIANS,
                CORNER_RADIUS,
                CORNER_SEGMENTS,
                NO_CHAMFER);
    }

    // Loops as plain coordinate lists, so two results compare by value: a loop is a list of
    // double[], and arrays compare by identity, which would make every equality assertion pass
    // or fail for the wrong reason.
    private static List<List<Double>> flattenLoops(List<List<double[]>> loops) {
        var flattened = new ArrayList<List<Double>>(loops.size());
        for (var loop : loops) {
            var coordinates = new ArrayList<Double>(loop.size() * 2);
            for (var vertex : loop) {
                coordinates.add(vertex[0]);
                coordinates.add(vertex[1]);
            }
            flattened.add(coordinates);
        }
        return flattened;
    }

    @Nested
    class SmoothBorderLoops {

        @Test
        void smoothBorderLoopsReturnsTheLoopsUntouchedWhenBothGatesAreOff() {
            var loops = List.of(NEEDLED);

            var smoothed = BorderSmoothing.smoothBorderLoops(loops, BOTH_GATES_OFF);

            // The same list, not an equal one: with nothing to do the pass must not cost a copy,
            // and a caller may compare by identity to skip re-uploading unchanged geometry.
            assertThat(smoothed).isSameAs(loops);
        }

        @Test
        void smoothBorderLoopsRunsOnlyTheSandingPassWhenOnlyThatGateIsOn() {
            var loops = List.of(NEEDLED);

            var smoothed = BorderSmoothing.smoothBorderLoops(loops, SANDING_ONLY);

            assertThat(flattenLoops(smoothed))
                    .isEqualTo(flattenLoops(BorderSmoothing.sandBorderSpikes(loops, SANDING_ONLY)));
        }

        @Test
        void smoothBorderLoopsRunsOnlyTheRoundingPassWhenOnlyThatGateIsOn() {
            var loops = List.of(SQUARE);

            var smoothed = BorderSmoothing.smoothBorderLoops(loops, ROUNDING_ONLY);

            assertThat(flattenLoops(smoothed)).isEqualTo(
                    flattenLoops(BorderSmoothing.roundBorderCorners(loops, ROUNDING_ONLY)));
        }

        @Test
        void smoothBorderLoopsSandsBeforeItRoundsWhenBothGatesAreOn() {
            var loops = List.of(NEEDLED);

            var smoothed = BorderSmoothing.smoothBorderLoops(loops, BOTH_GATES_ON);

            // Compared against the passes composed in the required order rather than against
            // fixed coordinates: what is pinned is the order, which rounding first would break
            // by arcing a needle the sanding pass would then no longer recognise.
            var sandedThenRounded = BorderSmoothing.roundBorderCorners(
                    BorderSmoothing.sandBorderSpikes(loops, BOTH_GATES_ON),
                    BOTH_GATES_ON);
            assertThat(flattenLoops(smoothed)).isEqualTo(flattenLoops(sandedThenRounded));
        }
    }

    @Nested
    class SandBorderSpikes {

        @Test
        void sandBorderSpikesSplicesOutTheNeedleApex() {
            var sanded = BorderSmoothing.sandBorderSpikes(List.of(NEEDLED), SANDING_ONLY);

            assertThat(sanded.get(0)).noneMatch(vertex -> vertex[1] > 100);
            assertThat(sanded.get(0)).hasSize(NEEDLED.size() - 1);
        }

        @Test
        void sandBorderSpikesSandsEveryLoopRatherThanOnlyTheFirst() {
            var sanded = BorderSmoothing.sandBorderSpikes(
                    List.of(NEEDLED, NEEDLED),
                    SANDING_ONLY);

            assertThat(sanded).hasSize(2);
            assertThat(sanded).allSatisfy(loop -> assertThat(loop).hasSize(NEEDLED.size() - 1));
        }
    }

    @Nested
    class RoundBorderCorners {

        @Test
        void roundBorderCornersReplacesEachSharpCornerWithAnArc() {
            var rounded = BorderSmoothing.roundBorderCorners(List.of(SQUARE), ROUNDING_ONLY);

            // Every corner becomes several vertices, so the ring grows; and no original corner
            // point survives, since each is stepped back from on both sides.
            assertThat(rounded.get(0)).hasSizeGreaterThan(SQUARE.size());
            assertThat(rounded.get(0)).noneMatch(vertex -> vertex[0] == 0 && vertex[1] == 0);
        }

        @Test
        void roundBorderCornersRoundsEveryLoopRatherThanOnlyTheFirst() {
            var rounded = BorderSmoothing.roundBorderCorners(
                    List.of(SQUARE, SQUARE),
                    ROUNDING_ONLY);

            assertThat(rounded).hasSize(2);
            assertThat(rounded).allSatisfy(
                    loop -> assertThat(loop).hasSizeGreaterThan(SQUARE.size()));
        }

        @Test
        void roundBorderCornersWorksToTheProfileItIsGivenRatherThanOneFixedShape() {
            var narrow = BorderSmoothing.roundBorderCorners(List.of(SQUARE), ROUNDING_ONLY);
            var wide = BorderSmoothing.roundBorderCorners(
                    List.of(SQUARE),
                    new BorderSmoothingStyle(
                            false,
                            true,
                            SPIKE_HEIGHT,
                            SPIKE_ANGLE_RADIANS,
                            WIDER_CORNER_RADIUS,
                            CORNER_SEGMENTS,
                            NO_CHAMFER));

            assertThat(flattenLoops(wide)).isNotEqualTo(flattenLoops(narrow));
        }
    }

    @Nested
    class RoundLoopCorners {

        @Test
        void roundLoopCornersMatchesWhatTheSameLoopGetsInsideASetOfLoops() {
            var alone = BorderSmoothing.roundLoopCorners(SQUARE, ROUNDING_ONLY);
            var withinLoops = BorderSmoothing.roundBorderCorners(List.of(SQUARE), ROUNDING_ONLY);

            // The invariant behind a lone cell's outline sitting flush against the cluster border
            // next to it: both rounds are the same rounding, reached through one profile.
            assertThat(flattenLoops(List.of(alone))).isEqualTo(flattenLoops(withinLoops));
        }
    }
}
