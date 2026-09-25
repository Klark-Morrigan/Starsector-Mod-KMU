package kmu.maplayers.ownermap.render.ribbon;

import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two sums the sizes answer for the rest of the band: how deep inside a cell's ring the
 * band's path is traced, and how deep it is traced on a cell with no room for that.
 *
 * <p>Two lines of arithmetic, stated on literals, because they are conventions rather than
 * calculations. The inset is the pad plus a half width because the pad is a promise about the
 * band's edge while the trace needs one about its centre; measuring it from the near edge instead
 * would sit the band on the border the pad exists to keep it off. The fallback gives up the pad
 * and keeps the half width for the same reason read backwards: without it the band would hang over
 * the border rather than merely touch it.
 */
final class RibbonStyleTest {

    // Round numbers rather than the shipped defaults, so what is pinned is the arithmetic and not
    // whatever the sliders happen to start at.
    private static final double WIDTH_WORLD = 400.0;
    private static final double INSET_PAD_WORLD = 200.0;
    private static final double MITER_SPIKE_LIMIT = 2.0;
    private static final RibbonSegmentLengths LENGTHS = new RibbonSegmentLengths(3, 1);

    // Whether a cell with no room for the band draws one anyway, which neither sum below reads -
    // both are arithmetic over the sizes, and which of them a cell is traced at is the builder's
    // question rather than the style's.
    private static final boolean BAND_ALWAYS_DRAWN = true;

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

    @Nested
    class ComputeUnpaddedCentrelineInset {

        @Test
        void computeUnpaddedCentrelineInsetKeepsHalfTheBandAndNothingElse() {
            // The pad given up entirely, so the band's near edge lands on the cell's own border
            // rather than clear of it - as deep as a cell short of room can be traced at without
            // hanging the band outside the cell it reports on.
            assertThat(buildStyle().computeUnpaddedCentrelineInset())
                .isEqualTo(200.0);
        }
    }

    private static RibbonStyle buildStyle() {
        return new RibbonStyle(
            WIDTH_WORLD,
            INSET_PAD_WORLD,
            MITER_SPIKE_LIMIT,
            LENGTHS,
            BAND_ALWAYS_DRAWN);
    }
}
