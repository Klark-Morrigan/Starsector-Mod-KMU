package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit coverage for what the lake tier decides: which label each of its substeps lays under,
 * which kind each piece its lines closed is, which of them are captured, and what they are
 * called.
 *
 * <p>How a line becomes walls is {@link CarriedLinesTests}'s. The kind and the capture are
 * pinned on the two halves of a square a reach runs across, since which half is the bay is the
 * whole of the question, and a sign read the wrong way round passes every count while filling
 * the bays as the lake.
 */
class LakeTierTests {

    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    private static final List<double[]> SITES = List.of(new double[] {0, 0}, new double[] {1000, 0});

    private static final CellGap LINE =
        new CellGap(0, 1, new double[] {0, 300}, new double[] {1000, 300}, 1000);

    // No lake's ring of cells is any of the squares here, so a piece the lines do not touch
    // is untouched.
    private static final List<Set<Integer>> NO_LAKES = List.of();

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
    class ReadKind {

        @Test
        void theWaterInFrontOfAReachIsTheLake() {

            assertThat(LakeTier.readKind(
                    buildUpperHalf(LakeTier.THE_LAKE_COAST), List.of(EASTWARD_WALL), NO_LAKES))
                .isEqualTo(LakeTier.Kind.LAKE);
        }

        @Test
        void theBayBehindAReachIsTheMargin() {

            assertThat(LakeTier.readKind(
                    buildLowerHalf(LakeTier.THE_LAKE_COAST), List.of(EASTWARD_WALL), NO_LAKES))
                .isEqualTo(LakeTier.Kind.MARGIN);
        }

        @Test
        void aReachTurnedRoundSwapsTheTwo() {
            // The side is read off the reach's direction, so a reach handed over the wrong way
            // round fills the bay as the lake and the lake as the margin.
            assertThat(List.of(
                    LakeTier.readKind(
                        buildUpperHalf(LakeTier.THE_LAKE_COAST), List.of(WESTWARD_WALL), NO_LAKES),
                    LakeTier.readKind(
                        buildLowerHalf(LakeTier.THE_LAKE_COAST), List.of(WESTWARD_WALL), NO_LAKES)))
                .containsExactly(LakeTier.Kind.MARGIN, LakeTier.Kind.LAKE);
        }

        @Test
        void eitherSideOfABridgeIsTheLake() {
            // A bridge divides the lake's water, and neither half stops being the lake.
            assertThat(List.of(
                    buildLowerHalf(LakeTier.THE_LAKE_BRIDGES),
                    buildUpperHalf(LakeTier.THE_LAKE_BRIDGES)))
                .allSatisfy(piece -> assertThat(LakeTier.readKind(piece, List.of(), NO_LAKES))
                    .isEqualTo(LakeTier.Kind.LAKE));
        }

        @Test
        void anEdgeBehindAReachMakesTheMarginWhateverElseBoundsIt() {
            // The lower half with a bridge along its bottom as well as the reach along its
            // top: the bay is outside the coast, so no bridge can make it the lake.
            var piece = Face.encloseFace(new LabelledRing(
                LOWER_HALF,
                new int[] {LakeTier.THE_LAKE_BRIDGES, 1, LakeTier.THE_LAKE_COAST, 3}));

            assertThat(LakeTier.readKind(piece, List.of(EASTWARD_WALL), NO_LAKES))
                .isEqualTo(LakeTier.Kind.MARGIN);
        }

        @Test
        void aPieceTheTierNeverTouchedIsUntouched() {

            assertThat(LakeTier.readKind(buildLowerHalf(2), List.of(EASTWARD_WALL), NO_LAKES))
                .isEqualTo(LakeTier.Kind.UNTOUCHED);
        }

        @Test
        void aPieceNoLineTouchesIsTheLakeWhenItsCellsAreALakesRing() {
            // A lake whose coast is all fillets lays nothing, so its one piece is bounded by
            // cells alone; the trace's ring of cells is what says it is a lake.
            assertThat(LakeTier.readKind(
                    buildLowerHalf(2), List.of(), List.of(Set.of(9), Set.of(0, 1, 2, 3))))
                .isEqualTo(LakeTier.Kind.LAKE);
        }

        @Test
        void aRingThatOnlyOverlapsThePiecesCellsIsAnotherHole() {
            // Every cell must match: a puddle sharing cells with a lake is not that lake.
            assertThat(LakeTier.readKind(
                    buildLowerHalf(2), List.of(), List.of(Set.of(0, 1, 2))))
                .isEqualTo(LakeTier.Kind.UNTOUCHED);
        }

        @Test
        void aHoleAlongAReachCountsAsMuchAsTheOutline() {
            // The upper half's ring turned into a hole of a piece round it, its edge along the
            // reach now walked west - against the wall - as a hole is walked: the piece round
            // it is on the bay's side.
            var piece = Face.encloseFace(new LabelledRing(
                    List.of(
                        new double[] {-100, -100},
                        new double[] {200, -100},
                        new double[] {200, 200},
                        new double[] {-100, 200}),
                    new int[] {5, 5, 5, 5}))
                .cutOut(new LabelledRing(
                    List.of(UPPER_HALF.get(1), UPPER_HALF.get(0), UPPER_HALF.get(3), UPPER_HALF.get(2)),
                    new int[] {LakeTier.THE_LAKE_COAST, 3, 2, 1}));

            assertThat(LakeTier.readKind(piece, List.of(EASTWARD_WALL), NO_LAKES))
                .isEqualTo(LakeTier.Kind.MARGIN);
        }
    }

    @Nested
    class NamePiece {

        private static final List<String> SYSTEM_IDS = List.of("alpha", "beta", "gamma", "delta");

        private static final List<double[]> FOUR_SITES = List.of(
            new double[] {0, 0},
            new double[] {1000, 0},
            new double[] {1000, 1000},
            new double[] {0, 1000});

        @Test
        void theLakesWaterIsNamedAsTheLake() {

            assertThat(LakeTier.namePiece(
                    buildUpperHalf(LakeTier.THE_LAKE_COAST),
                    LakeTier.Kind.LAKE,
                    FOUR_SITES,
                    SYSTEM_IDS))
                .startsWith("void_lake--");
        }

        @Test
        void theMarginIsNamedApartFromTheLake() {
            // The same cells either side of one reach: the prefix is what keeps the two apart
            // where the side of the pair might not.
            assertThat(LakeTier.namePiece(
                    buildLowerHalf(LakeTier.THE_LAKE_COAST),
                    LakeTier.Kind.MARGIN,
                    FOUR_SITES,
                    SYSTEM_IDS))
                .startsWith("void_lakemargin--");
        }

        @Test
        void aPieceTheTierNeverTouchedIsNotItsToName() {

            assertThatIllegalArgumentException()
                .isThrownBy(() -> LakeTier.namePiece(
                    buildLowerHalf(2), LakeTier.Kind.UNTOUCHED, FOUR_SITES, SYSTEM_IDS));
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
