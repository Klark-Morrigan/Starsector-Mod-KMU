package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the frontage over the bare partition, on the real sectors.
 *
 * <p>At the base every piece is open, so the frontage is every cell border that faces void -
 * which the cells themselves already name, one edge at a time. So the checks come in two
 * halves. Against the PIECES it is exact: every edge of a piece that lies on a cell is one
 * run corner, outline and holes alike, and the sea's holes are most of them. Against the
 * CELLS' own edges it cannot be, because the walk carries only what the map's resolution
 * holds - an edge shorter than the weld has both ends welded into one vertex, and an edge
 * bounding a piece too small to enclose area goes with that piece. So that half pins the
 * remainder at a handful rather than at nothing.
 */
class LandableFrontageIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.v4.LandableFrontageIntegrationTest#provideSectorNames";

    private static final SectorGeometryParameters KNOBS =
        SectorGeometryParameters.createDefaults();

    // How many frontier edges the walk may drop for being finer than the map is drawn at.
    // Measured at fifteen of 366's 5,535 and twenty-one of 491's 8,671, and left room above
    // that: what it guards is that the figure stays a handful, since a real fault - a frontier
    // that breaks and leaks one pocket into the next - costs hundreds at once.
    private static final int BELOW_THE_MAPS_RESOLUTION = 60;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class CollectLandableRuns {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyCellEdgeOfEveryPieceIsFrontageExactlyOnce(String sector) {
            // What the frontage promises, as an identity rather than a sample: a piece's
            // every edge that lies on a cell is covered by exactly one run, outline and holes
            // alike, and the sea's holes are most of them. A run of n points covers the n - 1
            // edges between them, so counting points would count each run's far end twice -
            // and counting starts alone would drop it, which is the notch at every junction
            // this once drew.
            var bare = readBare(sector, readCellEdges(sector));

            var onCells = 0;
            var frontage = 0;

            for (var piece : bare.collectPieces()) {

                onCells += countCellEdges(piece.edgeLabels());

                for (var hole : piece.holes()) {
                    onCells += countCellEdges(hole.edgeLabels());
                }

                for (var run : LandableFrontage.collectLandableRuns(piece)) {
                    frontage += run.points().size() - 1;
                }
            }

            assertThat(frontage).isEqualTo(onCells);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theFrontageIsNearlyEveryFrontierEdgeTheCellsReport(String sector) {
            // The pieces are read off the cells, so the two counts are the same count bar what
            // the walk cannot carry: an edge shorter than the welding tolerance has both ends
            // welded into one vertex, and an edge bounding a piece too small to enclose area at
            // this resolution goes with that piece. Both are the map's own resolution rather
            // than anything lost, and between them they are a few dozen of several thousand -
            // so what is pinned is that the remainder is that small, not that it is nothing.
            var cellEdges = readCellEdges(sector);
            var frontier = 0;

            for (var edges : cellEdges.values()) {
                for (var edge : edges) {
                    if (edge.target() == EdgeTarget.REACH_BOUND) {
                        frontier++;
                    }
                }
            }

            var frontage = 0;

            for (var piece : readBare(sector, cellEdges).collectPieces()) {
                for (var run : LandableFrontage.collectLandableRuns(piece)) {
                    frontage += run.points().size() - 1;
                }
            }

            assertThat(frontage)
                .isBetween(frontier - BELOW_THE_MAPS_RESOLUTION, frontier);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theSeaHasAShore(String sector) {
            // The piece the earlier reading left with nothing: its shore is its holes.
            var bare = readBare(sector, readCellEdges(sector));

            for (var piece : bare.collectPieces()) {

                if (piece.holes().isEmpty()) {
                    continue;
                }

                assertThat(LandableFrontage.collectLandableRuns(piece))
                    .isNotEmpty();
            }
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyRunLiesOnTheCellItNames(String sector) {
            // A run's corners stand on its cell's frontier: on the polygon inscribed in the
            // bound, welded to a neighbour's within the same distance either way.
            var bare = readBare(sector, readCellEdges(sector));
            var slack = 2 * measureSagitta();

            for (var piece : bare.collectPieces()) {
                for (var run : LandableFrontage.collectLandableRuns(piece)) {

                    var site = bare.union().sites().get(run.cell());

                    assertThat(run.points())
                        .as("a run on cell %d", run.cell())
                        .allSatisfy(point ->
                            assertThat(Points.computeDistance(point, site))
                                .isBetween(
                                    bare.union().reach() - slack,
                                    bare.union().reach() + slack));
                }
            }
        }
    }

    private static BareVoid readBare(String sector, Map<?, List<CellEdge>> cellEdges) {

        return BareVoid.readBareVoid(
            cellEdges, SectorFixture.loadSector(sector).getSites(), KNOBS);
    }

    private static Map<?, List<CellEdge>> readCellEdges(String sector) {

        return SectorFixture.loadSector(sector)
            .buildCellEdgesBySystemKey(KNOBS.cellRadius(), KNOBS.boundSegments());
    }

    private static int countCellEdges(int[] labels) {

        var onCells = 0;

        for (var label : labels) {
            if (label != BareVoid.THE_FRAME) {
                onCells++;
            }
        }
        return onCells;
    }

    private static double measureSagitta() {

        return KNOBS.cellRadius() * (1 - Math.cos(Math.PI / KNOBS.boundSegments()));
    }
}
