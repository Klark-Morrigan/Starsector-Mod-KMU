package kmu.maplayers.base.theme;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the shape of the halo's stack - {@link HoverGlowStyle#computeLayerWidth} and
 * {@link HoverGlowStyle#computeLayerAlpha}:
 *  - the innermost layer is the thinnest and brightest, the outermost the widest and faintest,
 *  - the widest layer spans the halo's full width and the brightest carries its full opacity,
 *  - the pulse rides every layer alike, starting full, bottoming out midway, and returning,
 *  - it never dims further than its strength allows, and holds steady when off or degenerate.
 *
 * <p>The stack is the halo's whole look expressed as arithmetic, and the piece a flipped
 * falloff or a doubled period would leave merely looking wrong on the map rather than failing
 * outright, so it is pinned here against a clock the test supplies rather than the wall clock
 * the renderer reads.
 */
final class HoverGlowStyleTest {
    private static final double TOLERANCE = 1e-9;

    // A four-layer halo reaching 20 pixels at full opacity, and breathing down to half
    // brightness over a two-second cycle - values picked so each layer, the crest, and the
    // trough land on exact fractions.
    private static HoverGlowStyle buildBreathingHalo() {
        return new HoverGlowStyle(1, 20, 4, 0.5, 2);
    }

    // The same halo with its pulse off, so a layer's alpha reads as its falloff alone.
    private static HoverGlowStyle buildSteadyHalo() {
        return new HoverGlowStyle(1, 20, 4, 0, 2);
    }

    @Nested
    class ComputeLayerWidth {
        @Test
        void theLayersWidenEvenlyOutToTheHalosFullWidth() {
            var style = buildSteadyHalo();

            assertThat(style.computeLayerWidth(0)).isCloseTo(5, within(TOLERANCE));
            assertThat(style.computeLayerWidth(1)).isCloseTo(10, within(TOLERANCE));
            assertThat(style.computeLayerWidth(2)).isCloseTo(15, within(TOLERANCE));
            assertThat(style.computeLayerWidth(3)).isCloseTo(20, within(TOLERANCE));
        }

        @Test
        void aSingleLayerHaloStrokesAtTheFullWidth() {
            // With nothing to fall off across, the one layer is both innermost and outermost.
            var style = new HoverGlowStyle(1, 20, 1, 0, 2);

            assertThat(style.computeLayerWidth(0)).isCloseTo(20, within(TOLERANCE));
        }
    }

    @Nested
    class ComputeLayerAlpha {
        @Test
        void theLayersFadeOutwardFromTheFullOpacityAtTheRim() {
            var style = buildSteadyHalo();

            assertThat(style.computeLayerAlpha(0, 0)).isCloseTo(1, within(TOLERANCE));
            assertThat(style.computeLayerAlpha(1, 0)).isCloseTo(0.75, within(TOLERANCE));
            assertThat(style.computeLayerAlpha(2, 0)).isCloseTo(0.5, within(TOLERANCE));
            assertThat(style.computeLayerAlpha(3, 0)).isCloseTo(0.25, within(TOLERANCE));
        }

        @Test
        void aWiderLayerIsNeverBrighterThanTheOneInsideIt() {
            // The falloff is what makes the stack read as a halo rather than a thick line, so
            // its direction is pinned rather than left to the arithmetic.
            var style = buildSteadyHalo();

            for (var layer = 1; layer < style.layers(); layer++) {
                assertThat(style.computeLayerAlpha(layer, 0))
                        .isLessThan(style.computeLayerAlpha(layer - 1, 0));
                assertThat(style.computeLayerWidth(layer))
                        .isGreaterThan(style.computeLayerWidth(layer - 1));
            }
        }

        @Test
        void theBreathStartsFullBottomsOutMidwayAndReturns() {
            var style = buildBreathingHalo();

            assertThat(style.computeLayerAlpha(0, 0)).isCloseTo(1, within(TOLERANCE));
            assertThat(style.computeLayerAlpha(0, 1)).isCloseTo(0.5, within(TOLERANCE));
            assertThat(style.computeLayerAlpha(0, 2)).isCloseTo(1, within(TOLERANCE));
        }

        @Test
        void theBreathScalesEveryLayerAlike() {
            // One pulse over the whole stack, so the halo breathes as one thing rather than
            // its layers sliding against each other.
            var style = buildBreathingHalo();

            assertThat(style.computeLayerAlpha(2, 1))
                    .isCloseTo(style.computeLayerAlpha(2, 0) * 0.5, within(TOLERANCE));
        }

        @Test
        void theBreathNeverDimsPastItsStrengthAtAnyPhase() {
            var style = buildBreathingHalo();

            // Sampled across a whole cycle at a phase that hits neither crest nor trough
            // squarely, so an inverted or overdriven pulse shows up as an out-of-range alpha.
            for (var step = 0; step <= 40; step++) {
                assertThat(style.computeLayerAlpha(0, step * 0.05)).isBetween(0.5, 1.0);
            }
        }

        @Test
        void aHaloWithNoPulseStrengthHoldsSteady() {
            assertThat(buildSteadyHalo().computeLayerAlpha(0, 0.7)).isEqualTo(1);
        }

        @Test
        void aHaloWithNoPulsePeriodHoldsSteadyRatherThanDividingByZero() {
            var style = new HoverGlowStyle(1, 20, 4, 0.5, 0);

            assertThat(style.computeLayerAlpha(0, 0.7)).isEqualTo(1);
        }
    }
}
