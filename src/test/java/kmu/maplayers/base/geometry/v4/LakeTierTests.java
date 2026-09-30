package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for what the lake tier decides: which label each of its substeps lays under,
 * and which pieces its lines captured.
 *
 * <p>How a line becomes walls is {@link CarriedLinesTests}'s. The capture is pinned on the two
 * halves of a square a reach runs across, since which half is the bay is the whole of the
 * question, and a sign read the wrong way round passes every count while capturing the lake.
 */
class LakeTierTests {

    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {1000, 0});

    private static final CellGap LINE =
        new CellGap(0, 1, new double[] {0, 300}, new double[] {1000, 300}, 1000);

    // No shore to move the ends onto; where they end is CarriedLinesTests' subject.
    private static final List<LabelledWall> NO_SHORE = List.of();

    // A coast wall across the middle of a square 100 across, running east, so its left - the
    // lake's water - is the upper half and the bay behind it the lower.
    private static final LabelledWall EASTWARD_WALL =
        new LabelledWall(new Segment(0, 50, 100, 50), LakeTier.THE_LAKE_COAST);

    // The same wall running west, which puts the water below it.
    private static final LabelledWall WESTWARD_WALL =
        new LabelledWall(new Segment(100, 50, 0, 50), LakeTier.THE_LAKE_COAST);

    // The two halves of that square, each wound counter-clockwise as a bounded piece is and so
    // walked with itself on the left, their shared side labelled with whatever lies along it.
    private static final List<double[]> LOWER_HALF = List.of(
        new double[] {0, 0},
        new double[] {100, 0},
        new double[] {100, 50},
        new double[] {0, 50});

    private static final List<double[]> UPPER_HALF = List.of(
        new double[] {0, 50},
        new double[] {100, 50},
        new double[] {100, 100},
        new double[] {0, 100});

    @Nested
    class LayCoastWalls {

        @Test
        void everyWallCarriesTheLakeCoastLabel() {

            var laid = LakeTier.layCoastWalls(List.of(LINE), NO_SHORE, SITES, PARAMETERS);

            assertThat(laid.walls())
                .isNotEmpty()
                .extracting(LabelledWall::label)
                .containsOnly(LakeTier.THE_LAKE_COAST);
        }
    }

    @Nested
    class IsCaptured {

        @Test
        void theBayBehindAReachIsCaptured() {

            assertThat(LakeTier.isCaptured(
                    buildLowerHalf(LakeTier.THE_LAKE_COAST), List.of(EASTWARD_WALL)))
                .isTrue();
        }

        @Test
        void theWaterInFrontOfAReachIsOpen() {
            // Where the bridges are to land, so capturing it would leave them nowhere.
            assertThat(LakeTier.isCaptured(
                    buildUpperHalf(LakeTier.THE_LAKE_COAST), List.of(EASTWARD_WALL)))
                .isFalse();
        }

        @Test
        void aReachTurnedRoundCapturesTheOtherSide() {
            // The side is read off the reach's direction and nothing else, so turning it round
            // is the whole difference between the lake and the bay.
            assertThat(LakeTier.isCaptured(
                    buildUpperHalf(LakeTier.THE_LAKE_COAST), List.of(WESTWARD_WALL)))
                .isTrue();
        }

        @Test
        void aCoastEdgeOffEveryReachCapturesNothing() {
            // A stub carried through the shore at an angle, which bounds a piece without being
            // the reach: it says nothing about which side is the bay.
            assertThat(LakeTier.isCaptured(
                    buildLowerHalf(LakeTier.THE_LAKE_COAST), List.of()))
                .isFalse();
        }

        @Test
        void aBridgeCapturesBothSides() {

            assertThat(List.of(
                    buildLowerHalf(LakeTier.THE_LAKE_BRIDGES),
                    buildUpperHalf(LakeTier.THE_LAKE_BRIDGES)))
                .allSatisfy(piece -> assertThat(LakeTier.isCaptured(piece, List.of()))
                    .isTrue());
        }

        @Test
        void aPieceTheTierNeverTouchedIsOpen() {

            assertThat(LakeTier.isCaptured(buildLowerHalf(2), List.of(EASTWARD_WALL)))
                .isFalse();
        }
    }

    @Nested
    class LayBridgeWalls {

        @Test
        void everyWallCarriesTheLakeBridgesLabel() {

            var laid = LakeTier.layBridgeWalls(List.of(LINE), NO_SHORE, SITES, PARAMETERS);

            assertThat(laid.walls())
                .isNotEmpty()
                .extracting(LabelledWall::label)
                .containsOnly(LakeTier.THE_LAKE_BRIDGES);
        }

        @Test
        void theBridgesLabelIsNeitherTheCoastsNorTheFrames() {
            // Two lines under one number would name every piece either closed after whichever
            // was asked about.
            assertThat(LakeTier.THE_LAKE_BRIDGES)
                .isNotEqualTo(LakeTier.THE_LAKE_COAST)
                .isNotEqualTo(VoidPartition.THE_FRAME)
                .isNegative();
        }
    }

    // The lower half, its sides on cells 0, 1 and 3 but for its top, walked west along the
    // square's middle, which lies on the given line.
    private static Face buildLowerHalf(int middleLabel) {
        return Face.encloseFace(new LabelledRing(LOWER_HALF, new int[] {0, 1, middleLabel, 3}));
    }

    // The upper half, its bottom walked east along the square's middle on the given line.
    private static Face buildUpperHalf(int middleLabel) {
        return Face.encloseFace(new LabelledRing(UPPER_HALF, new int[] {middleLabel, 1, 2, 3}));
    }
}
