package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one sum the sizes answer for the rest of the band: how deep inside a cell's ring the
 * band's path is traced.
 *
 * <p>One line of arithmetic, stated on literals, because it is a convention rather than a
 * calculation. The inset is the pad plus a half width because the pad is a promise about the
 * band's edge while the trace needs one about its centre; measuring it from the near edge instead
 * would sit the band on the border the pad exists to keep it off.
 */
final class RibbonStyleTest {

    // Round numbers rather than the shipped defaults, so what is pinned is the arithmetic and not
    // whatever the sliders happen to start at.
    private static final double WIDTH_WORLD = 400.0;
    private static final double INSET_PAD_WORLD = 200.0;
    private static final double MITER_SPIKE_LIMIT = 2.0;
    private static final RibbonSegmentLengths LENGTHS = new RibbonSegmentLengths(3, 1);

    @Nested
    class ComputeCentrelineInset {

        @Test
        void computeCentrelineInsetClearsTheBorderByThePadAndThenHalfTheBand() {
            // 200 of clearance plus half of a 400-wide band, so the band's near edge lands exactly
            // at the pad and its far edge 200 deeper in.
            assertThat(buildStyle().computeCentrelineInset())
                .isEqualTo(400.0);
        }
    }

    private static RibbonStyle buildStyle() {
        return new RibbonStyle(
            WIDTH_WORLD,
            INSET_PAD_WORLD,
            MITER_SPIKE_LIMIT,
            LENGTHS);
    }
}
