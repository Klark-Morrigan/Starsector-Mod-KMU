package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.Points;
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
import static kmu.maplayers.base.geometry.v4.SectorPartitions.readFrontier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v3's lake bridges laid into v4, over the real sectors.
 *
 * <p>Here rather than beside {@link LakeTier}, because it reads both constructions and the
 * layering keeps each of them from reading the other - in tests as in the code.
 *
 * <p><b>Three things are pinned.</b> That every bridge lands on the lake's frontage, since that
 * is the one place a bridge may land; that where the coast only touches a cell it lands on
 * v4's point of frontage there, since a point is all such a contact offers; and what the
 * bridges do once laid over the coast, since a line that divides water may not take any of it
 * away.
 */
class LakeBridgesIntegrationTests {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.ui.LakeBridgesIntegrationTests#provideSectorNames";

    // How far a bridge end may stand from the frontage, in sagittas: one for the end sitting
    // on the true rim and the frontage on the polygon inside it, and one more for the two
    // walls of a contact crossing that polygon a little apart.
    private static final double CONTACT_SLACK_SAGITTAS = 2;

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
        void everyBridgeLandsOnOpenFrontage(String sector) {
            // The rule a bridge is judged by, asked of v4's own frontage: the frontage standing
            // when the bridges go in is what the coast left open, and each end has to stand on
            // it - within two sagittas, since an end sits on the cell's true rim, the frontage on
            // the polygon drawn inside it, and a contact's two walls cross that polygon a little
            // apart. On whichever cell v4 says: a point on one cell's circle can lie on another
            // cell's border once the cells are clipped against each other, and one contact on 366
            // does. A cell the coast only touches offers a point rather than a stretch, and the
            // points are what most of those ends stand on - which is pinned, or the points would
            // be tested against nothing.
            var continents = LakePartitions.layContinents(sector);
            var coastWalls = LakePartitions.layCoastWalls(sector);
            var frontage = LandableFrontage.collectLandableFrontage(
                LakePartitions.readCoastPartition(sector).collectPieces(),
                piece -> LakeTier.isCaptured(piece, coastWalls),
                readFrontier(sector),
                KNOBS.measureBoundSagitta());
            var contacts = CoastFrontages.collectLakeFrontages(continents.traceCoasts());
            var furthest = CONTACT_SLACK_SAGITTAS * KNOBS.measureBoundSagitta();
            var endsOnAPoint = 0;

            for (var bridge : continents.layLakeSpans()) {
                for (var end : List.of(
                        new BridgeEnd(bridge.fromSite(), bridge.start()),
                        new BridgeEnd(bridge.toSite(), bridge.end()))) {

                    var toARun = measureDistanceToRuns(frontage.runs(), end.point());
                    var toAPoint = measureDistanceToPoints(frontage.points(), end.point());

                    assertThat(Math.min(toARun, toAPoint))
                        .as("a bridge end on cell %d", end.cell())
                        .isLessThanOrEqualTo(furthest);

                    if (isSinglePoint(contacts.get(end.cell()), end.point()) && toAPoint < toARun) {
                        endsOnAPoint++;
                    }
                }
            }

            assertThat(endsOnAPoint)
                .isPositive();
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

            assertThat(measureVoid(LakePartitions.readCoastPartition(sector))
                    - measureVoid(LakePartitions.readBridgedPartition(sector)))
                .isBetween(
                    -LakePartitions.AREA_ROUNDING,
                    LakePartitions.measureSliverAllowance(bridgeCount)
                        + LakePartitions.AREA_ROUNDING);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theOpenSeaIsUntouched(String sector) {
            // Every lake is inside a continent, so no lake bridge reaches the sea.
            assertThat(measureSea(LakePartitions.readBridgedPartition(sector)))
                .isCloseTo(
                    measureSea(LakePartitions.readCoastPartition(sector)),
                    withinPercentage(LakePartitions.SEA_SHARE));
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

    // How far a bridge end stands from the nearest stretch of open frontage.
    private static double measureDistanceToRuns(
            List<LandableFrontage.Run> runs, double[] end) {

        var nearest = Double.MAX_VALUE;

        for (var run : runs) {
            for (var corner = 0; corner + 1 < run.points().size(); corner++) {

                nearest = Math.min(nearest, Segments.computeDistanceToPoint(
                    run.points().get(corner), run.points().get(corner + 1), end));
            }
        }
        return nearest;
    }

    // How far a bridge end stands from the nearest point of open frontage.
    private static double measureDistanceToPoints(
            List<LandableFrontage.Point> points, double[] end) {

        var nearest = Double.MAX_VALUE;

        for (var point : points) {
            nearest = Math.min(nearest, Points.computeDistance(point.point(), end));
        }
        return nearest;
    }

    // One end of a bridge and the cell it stands on.
    private record BridgeEnd(int cell, double[] point) {
    }
}
