package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins that moving one knob moves exactly that knob.
 *
 * <p>The fault these guard is silent. Five components of which four are doubles, rebuilt
 * positionally at every slider in the viewer: a pair swapped there compiles cleanly, and what
 * comes out is a sector built at somebody else's setting with nothing to say so. The whole
 * point of a wither is that the four it does not touch cannot be got wrong, so what is asserted
 * is those four rather than the one that moved.
 *
 * <p>Distinct values throughout, so a wither writing the wrong component is caught by the
 * component it wrote rather than passing because two happened to match.
 */
class SectorGeometryParametersTest {

    private static final double CELL_RADIUS = 4000;
    private static final int BOUND_SEGMENTS = 48;
    private static final double BORDER_INSET = 150;
    private static final double WELD_TOLERANCE = 100;
    private static final double MITER_SPIKE_LIMIT = 4;

    private static final SectorGeometryParameters PARAMETERS = new SectorGeometryParameters(
        CELL_RADIUS, BOUND_SEGMENTS, BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

    @Nested
    class WithCellRadius {

        @Test
        void withCellRadiusMovesTheReachAndNothingElse() {

            assertThat(PARAMETERS.withCellRadius(9000))
                .isEqualTo(new SectorGeometryParameters(9000, 48, 150, 100, 4));
        }

        @Test
        void withCellRadiusLeavesTheParametersItWasAskedOfAlone() {

            PARAMETERS.withCellRadius(9000);

            assertThat(PARAMETERS.cellRadius())
                .isEqualTo(4000);
        }
    }

    @Nested
    class MeasureBoundSagitta {

        @Test
        void theGapFollowsTheReachAndTheBound() {
            // How coarsely the cells are drawn, which is what every reading of the map is
            // welded and filtered at. Four thousand of reach flattened onto forty-eight sides
            // leaves the middle of each side eight and a half units inside the true bound.
            assertThat(PARAMETERS.measureBoundSagitta())
                .isCloseTo(8.5643, within(0.0001));
        }

        @Test
        void aFinerBoundLeavesASmallerGap() {
            // The reason it is read rather than carried: a pass holding a number that was
            // right at one setting goes on judging at a resolution the map no longer has.
            assertThat(PARAMETERS.withBoundSegments(96).measureBoundSagitta())
                .isCloseTo(2.1417, within(0.0001));
        }

        @Test
        void aShorterReachLeavesASmallerGap() {

            assertThat(PARAMETERS.withCellRadius(2000).measureBoundSagitta())
                .isCloseTo(4.2822, within(0.0001));
        }
    }

    @Nested
    class WithBoundSegments {

        @Test
        void withBoundSegmentsMovesTheBoundAndNothingElse() {

            assertThat(PARAMETERS.withBoundSegments(96))
                .isEqualTo(new SectorGeometryParameters(4000, 96, 150, 100, 4));
        }
    }

    @Nested
    class WithBorderInset {

        @Test
        void withBorderInsetMovesTheChannelAndNothingElse() {

            assertThat(PARAMETERS.withBorderInset(300))
                .isEqualTo(new SectorGeometryParameters(4000, 48, 300, 100, 4));
        }
    }

    @Nested
    class WithWeldTolerance {

        @Test
        void withWeldToleranceMovesTheToleranceAndNothingElse() {

            assertThat(PARAMETERS.withWeldTolerance(250))
                .isEqualTo(new SectorGeometryParameters(4000, 48, 150, 250, 4));
        }
    }

    @Nested
    class WithMiterSpikeLimit {

        @Test
        void withMiterSpikeLimitMovesTheLimitAndNothingElse() {

            assertThat(PARAMETERS.withMiterSpikeLimit(9))
                .isEqualTo(new SectorGeometryParameters(4000, 48, 150, 100, 9));
        }
    }
}
