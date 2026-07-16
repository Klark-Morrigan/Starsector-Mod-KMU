package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link FrontierSetback#computeSetback}: the shared distance from the Voronoi
 * bisector to the keep-out line, the single derivation both frontier consumers offset by
 * so they meet on that line rather than by two coincidentally-equal offsets.
 */
final class FrontierSetbackTest {

    @Nested
    class ComputeSetback {

        @Test
        void computeSetbackReturnsHalfTheSpacingLessTheRadius() {
            // dist 1000 -> bisector at 500; keep-out 200 from the star leaves a 300 push.
            var setback = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {1000, 0}, 200);

            assertThat(setback).isCloseTo(300, within(1e-9));
        }

        @Test
        void computeSetbackClampsToZeroWhenSystemsAreCloserThanTwiceTheRadius() {
            // dist 300 -> bisector at 150, inside the 200 keep-out: the owned colour must
            // not push past the halfway line, so the setback floors at zero.
            var setback = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {300, 0}, 200);

            assertThat(setback).isZero();
        }

        @Test
        void computeSetbackIsZeroWhenSpacingEqualsTwiceTheRadius() {
            // The boundary case: the keep-out line coincides with the bisector, no push.
            var setback = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {400, 0}, 200);

            assertThat(setback).isZero();
        }

        @Test
        void computeSetbackDecreasesAsTheKeepOutRadiusGrows() {
            // A wider keep-out pocket pulls the shared line back toward the bisector.
            var tighter = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {1000, 0}, 200);
            var wider = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {1000, 0}, 400);

            assertThat(wider).isLessThan(tighter);
        }

        @Test
        void computeSetbackIncreasesAsTheSpacingGrows() {
            // Farther-apart systems leave more room, so the owned edge pushes out further.
            var closer = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {1000, 0}, 200);
            var farther = FrontierSetback.computeSetback(
                    new double[] {0, 0}, new double[] {1400, 0}, 200);

            assertThat(farther).isGreaterThan(closer);
        }

        @Test
        void computeSetbackIsIndependentOfWhichSiteIsOwned() {
            // The setback depends only on the spacing, so swapping owned and empty sites -
            // and off-axis positions - yields the same distance.
            var forward = FrontierSetback.computeSetback(
                    new double[] {100, 200}, new double[] {700, 1000}, 150);
            var reversed = FrontierSetback.computeSetback(
                    new double[] {700, 1000}, new double[] {100, 200}, 150);

            assertThat(reversed).isCloseTo(forward, within(1e-9));
        }
    }
}
