package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.v3.CoastFrontages;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.LandableFrontage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static kmu.maplayers.base.geometry.v4.SectorPartitions.KNOBS;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.measureSea;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.measureVoid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v3's lake bridges laid into v4, over the real sectors.
 *
 * <p>Here rather than beside {@link LakeTier}, because it reads both constructions and the
 * layering keeps each of them from reading the other - in tests as in the code.
 *
 * <p><b>Two things are pinned.</b> That every bridge lands on the lake's frontage, since that is
 * the one place a bridge may land; and what the bridges do once laid over the coast, since a
 * line that divides water may not take any of it away.
 */
class LakeBridgesIntegrationTests {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.ui.LakeBridgesIntegrationTests#provideSectorNames";

    // How many slivers each bridge can close off below the map's resolution: one where each end
    // meets the shore, as for a coast's reach.
    private static final int SLIVERS_PER_BRIDGE = 2;

    // How far two sums of the same void may differ by rounding alone, in units squared. The
    // void is some ten billion units squared, added up piece by piece and in different pieces
    // either side of the bridges; measured at two millionths on 366, where nothing was lost.
    private static final double AREA_ROUNDING = 1e-3;

    // How far, in percent, the open sea may move when the bridges go in. The lakes are inside
    // the continents, so the sea is not touched at all, and this is only rounding.
    private static final double AREA_SHARE = 1e-9;

    // How far apart two points may stand and still be the same place, which is room for the
    // rounding in a distance and nothing more.
    private static final double SAME_POINT = 1e-6;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class LayBridgeWalls {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyBridgeLandsOnItsOwnCellsLakeFrontage(String sector) {
            // The one place a bridge may land: where the lake's coast runs along the cell, or
            // the single point where it touches one. Both ends, each on the cell it names - a
            // foot on a neighbour's frontage would be a bridge carried through the wrong shore.
            var continents = LakePartitions.layContinents(sector);
            var frontages = CoastFrontages.gatherFrontagePoints(
                CoastFrontages.collectLakeFrontages(continents.traceCoasts()));

            assertThat(continents.layLakeSpans())
                .isNotEmpty()
                .allSatisfy(bridge -> {

                    assertThat(frontages.get(bridge.fromSite()))
                        .as("the frontage of cell %d", bridge.fromSite())
                        .contains(bridge.start());
                    assertThat(frontages.get(bridge.toSite()))
                        .as("the frontage of cell %d", bridge.toSite())
                        .contains(bridge.end());
                });
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyBridgeLandsOnOpenFrontageOrAtASinglePoint(String sector) {
            // The rule a bridge is judged by, asked of v4's own frontage: the frontage standing
            // when the bridges go in is what the coast left open, and each end has to stand on
            // its own cell's stretch of it - to within a sagitta, since an end sits on the
            // cell's true rim and the frontage on the polygon drawn inside it. The one exception
            // is a cell the coast only touches between two reaches, which offers a single point
            // and no stretch; the frontage has no runs for those yet.
            var continents = LakePartitions.layContinents(sector);
            var reaches = LakePartitions.collectReaches(continents);
            var open = LandableFrontage.collectLandableRuns(
                LakePartitions.readCoastPartition(sector).collectPieces(),
                piece -> LakeTier.isCaptured(piece, reaches));
            var points = CoastFrontages.collectLakeFrontages(continents.traceCoasts());
            var furthest = KNOBS.measureBoundSagitta() + SAME_POINT;

            for (var bridge : continents.layLakeSpans()) {
                for (var end : List.of(
                        new BridgeEnd(bridge.fromSite(), bridge.start()),
                        new BridgeEnd(bridge.toSite(), bridge.end()))) {

                    if (isSinglePoint(points.get(end.cell()), end.point())) {
                        continue;
                    }
                    assertThat(measureDistanceToRuns(open, end))
                        .as("a bridge end on cell %d", end.cell())
                        .isLessThanOrEqualTo(furthest);
                }
            }
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void eachBridgeDividesOnePieceInTwo(String sector) {
            // The floor under the two checks below, which bridges that divided nothing would
            // pass - and exact, since the search keeps no bridge that crosses another: each runs
            // from one shore of a piece to another shore of the same piece, and cuts it in two.
            var bridgeCount = LakePartitions.layContinents(sector).layLakeSpans().size();

            assertThat(LakePartitions.readBridgedPartition(sector).countPieces())
                .isEqualTo(LakePartitions.readCoastPartition(sector).countPieces() + bridgeCount);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theBridgesTakeNoVoidAwayBarSliversBelowTheMapsResolution(String sector) {
            // Measured against the partition the coast left, since that is what the bridges are
            // laid onto: the void is the same number either side of them, bar the faces the walk
            // does not keep, which are those under a sagitta squared.
            var bridgeCount = LakePartitions.layContinents(sector).layLakeSpans().size();
            var sagitta = KNOBS.measureBoundSagitta();

            assertThat(measureVoid(LakePartitions.readCoastPartition(sector))
                    - measureVoid(LakePartitions.readBridgedPartition(sector)))
                .isBetween(
                    -AREA_ROUNDING,
                    SLIVERS_PER_BRIDGE * bridgeCount * sagitta * sagitta + AREA_ROUNDING);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theOpenSeaIsUntouched(String sector) {
            // Every lake is inside a continent, so no lake bridge reaches the sea.
            assertThat(measureSea(LakePartitions.readBridgedPartition(sector)))
                .isCloseTo(
                    measureSea(LakePartitions.readCoastPartition(sector)),
                    withinPercentage(AREA_SHARE));
        }
    }

    // Whether a point is one of a cell's single-point stretches of lake frontage: where the coast
    // touches the cell between two reaches and runs along none of it.
    private static boolean isSinglePoint(List<List<double[]>> stretches, double[] point) {

        for (var stretch : stretches) {

            if (stretch.size() == 1 && Arrays.equals(stretch.get(0), point)) {
                return true;
            }
        }
        return false;
    }

    // How far a bridge end stands from the nearest stretch of open frontage on its own cell.
    private static double measureDistanceToRuns(
            List<LandableFrontage.Run> runs, BridgeEnd end) {

        var nearest = Double.MAX_VALUE;

        for (var run : runs) {

            if (run.cell() != end.cell()) {
                continue;
            }
            for (var corner = 0; corner + 1 < run.points().size(); corner++) {

                nearest = Math.min(nearest, Segments.computeDistanceToPoint(
                    run.points().get(corner), run.points().get(corner + 1), end.point()));
            }
        }
        return nearest;
    }

    // One end of a bridge and the cell it stands on.
    private record BridgeEnd(int cell, double[] point) {
    }
}
