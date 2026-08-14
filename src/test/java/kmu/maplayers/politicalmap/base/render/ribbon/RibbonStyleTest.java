package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two sums the sizes answer for the rest of the band: how deep inside a cell's ring the
 * band's path is traced, and whether what that path carries is still thick enough on screen to be
 * worth drawing.
 *
 * <p>Both are one line of arithmetic and both are stated on literals, because each is a convention
 * rather than a calculation. The inset is the pad plus a half width because the pad is a promise
 * about the band's edge while the trace needs one about its centre; measuring it from the near
 * edge instead would sit the band on the border the pad exists to keep it off. The floor is
 * compared at the band's own width, since compression shortens a crowded cell's runs but never
 * thins it - so one comparison holds for every band in a frame.
 */
final class RibbonStyleTest {

    // Round numbers rather than the shipped defaults, so what is pinned is the arithmetic and not
    // whatever the sliders happen to start at.
    private static final double WIDTH_WORLD = 400.0;
    private static final double INSET_PAD_WORLD = 200.0;
    private static final double MITER_SPIKE_LIMIT = 2.0;
    private static final RibbonSegmentLengths LENGTHS = new RibbonSegmentLengths(3, 1);

    // Two pixels of floor against the 400-unit width above: the band clears it at a map scale of
    // 0.005 and falls below it under that.
    private static final double MIN_DRAWN_WIDTH_PIXELS = 2.0;
    private static final double SCALE_AT_THE_FLOOR = 0.005;
    private static final double SCALE_BELOW_THE_FLOOR = 0.004;
    private static final double SCALE_ABOVE_THE_FLOOR = 0.006;

    @Nested
    class ComputeCentrelineInset {

        @Test
        void computeCentrelineInsetClearsTheBorderByThePadAndThenHalfTheBand() {
            // 200 of clearance plus half of a 400-wide band, so the band's near edge lands exactly
            // at the pad and its far edge 200 deeper in.
            assertThat(buildStyle(MIN_DRAWN_WIDTH_PIXELS).computeCentrelineInset())
                .isEqualTo(400.0);
        }
    }

    @Nested
    class IsVisibleAtScale {

        @Test
        void isVisibleAtScaleDrawsABandWiderOnScreenThanTheFloor() {
            assertThat(buildStyle(MIN_DRAWN_WIDTH_PIXELS).isVisibleAtScale(SCALE_ABOVE_THE_FLOOR))
                .isTrue();
        }

        @Test
        void isVisibleAtScaleDrawsABandExactlyAtTheFloor() {
            // The floor is how thin a band may still be drawn, not the first width refused.
            assertThat(buildStyle(MIN_DRAWN_WIDTH_PIXELS).isVisibleAtScale(SCALE_AT_THE_FLOOR))
                .isTrue();
        }

        @Test
        void isVisibleAtScaleDropsABandThinnerOnScreenThanTheFloor() {
            // Zoomed out past this the band would read as a tint on the border rather than as a
            // band, which is the artefact the floor exists to cut off.
            assertThat(buildStyle(MIN_DRAWN_WIDTH_PIXELS).isVisibleAtScale(SCALE_BELOW_THE_FLOOR))
                .isFalse();
        }

        @Test
        void isVisibleAtScaleDrawsAtEveryZoomWithNoFloorSet() {
            // The bottom of the slider's range, which a player uses to say "never drop them".
            assertThat(buildStyle(0.0).isVisibleAtScale(SCALE_BELOW_THE_FLOOR))
                .isTrue();
        }
    }

    private static RibbonStyle buildStyle(double minDrawnWidthPixels) {
        return new RibbonStyle(
            WIDTH_WORLD,
            INSET_PAD_WORLD,
            MITER_SPIKE_LIMIT,
            LENGTHS,
            minDrawnWidthPixels);
    }
}
