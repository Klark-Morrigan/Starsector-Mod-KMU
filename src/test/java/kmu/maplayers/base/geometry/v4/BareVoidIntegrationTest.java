package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v4's base partition, over the real sectors.
 *
 * <p><b>The partition itself is the check.</b> The pieces of void and the cells together are
 * everything inside the frame, each point in exactly one of them - so their areas add up to the
 * frame's and no further. A piece overlapping another sums high, a piece missed sums low, and
 * nothing else the construction could get wrong leaves that sum intact. That is the property
 * the whole of v4 rests on, and it is checkable here because the void is now read off the same
 * cells the land is.
 *
 * <p><b>The disc sweep is deliberately NOT asserted against here.</b> Measured pocket by
 * pocket the two readings agree to four decimal places wherever they agree at all - but on the
 * shipped fixtures three of the sweep's fifteen pockets and one of its sixteen have no piece of
 * their own, and why is not yet understood. Pinning a count either way would freeze an answer
 * nobody has justified, so the comparison stays a probe until it is explained.
 */
class BareVoidIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.v4.BareVoidIntegrationTest#provideSectorNames";

    // The cells' own knobs, taken from where they are declared rather than from v3's facade
    // over them. That facade names the cell knobs and the coast rules together, which is the
    // right shape for a report on v3's map and the wrong one here: what the bare void stands
    // at is a fact about the cells, and v4 has no business reading anything of v3's to learn it.
    private static final SectorGeometryParameters KNOBS =
        SectorGeometryParameters.createDefaults();

    // How far, in percent, the areas may sit apart where they are meant to be the same number
    // computed two ways. The partition is exact in principle, so this is only what a shoelace
    // sum over some thousands of corners loses to rounding - measured at five parts in a
    // hundred million, and left an order of room above that.
    private static final double AREA_SHARE = 1e-4;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class ReadBareVoid {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theVoidAndTheCellsCoverTheFrameExactlyOnce(String sector) {
            // The partition check. Everything inside the frame is either a cell or a piece of
            // void, so the two sets of areas add to the frame's - which they can only do if
            // every point is in exactly one of them.
            var bare = readBare(sector);
            var covered = 0.0;

            for (var piece : bare.collectPieces()) {
                covered += piece.measureArea();
            }
            for (var cell : readCellEdges(sector).values()) {
                covered += Math.abs(PolygonRegions.computeSignedArea(readRing(cell)));
            }

            assertThat(covered)
                .isCloseTo(measureFramedArea(bare), withinPercentage(AREA_SHARE));
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theOpenSeaIsOnePieceWithTheCellsCutOutOfIt(String sector) {
            // The piece the sweep cannot see and the one the map is mostly made of. It is the
            // only piece with anything cut out of it, because every group of cells sits inside
            // it and every pocket sits inside a group.
            var bare = readBare(sector);

            assertThat(bare.collectPieces())
                .filteredOn(piece -> !piece.holes().isEmpty())
                .hasSize(1);
        }

    }

    @Nested
    class CollectOutlines {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void collectOutlinesGivesOneRingPerPiece(String sector) {

            var bare = readBare(sector);

            assertThat(bare.collectOutlines())
                .hasSize(bare.countPieces());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void collectOutlinesGivesEveryRingEnoughVerticesToEncloseArea(String sector) {

            assertThat(readBare(sector).collectOutlines())
                .allSatisfy(ring ->
                    assertThat(ring.size())
                        .isGreaterThanOrEqualTo(Limits.MIN_VERTICES_TO_ENCLOSE_AREA));
        }

        // Wound the way every other filled shape on this map is, which is what lets a piece be
        // handed to the painting without asking which direction it came back in.
        @ParameterizedTest
        @MethodSource(SECTORS)
        void collectOutlinesGivesEveryRingTheFillWinding(String sector) {

            assertThat(readBare(sector).collectOutlines())
                .allSatisfy(ring ->
                    assertThat(PolygonRegions.computeSignedArea(ring)).isPositive());
        }
    }

    @Nested
    class CollectPieces {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyEdgeLiesOnTheCellItIsLabelledWith(String sector) {
            // A label is a claim that an edge is a stretch of one cell's own frontier, and a
            // cell's frontier is drawn on a polygon inscribed in its reach - so every corner of
            // it stands between the sagitta and nothing short of that reach. A label put on the
            // wrong edge names a cell somewhere else entirely, which is a different order of
            // wrong from the approximation.
            // Two things move a corner off the true bound, and both are the resolution the
            // map is drawn at rather than error. The frontier is drawn on a polygon inscribed
            // in the bound, which puts a corner up to the sagitta inside it; and welding joins
            // two reports of one corner that stand up to the same distance apart, keeping the
            // first - which can be the neighbour's, a little the other side. So a corner is on
            // its cell's frontier to within that, either way, and a label naming a cell
            // somewhere else entirely is a different order of wrong.
            var bare = readBare(sector);
            var slack = 2 * measureSagitta();
            var nearest = bare.union().reach() - slack;
            var furthest = bare.union().reach() + slack;

            for (var piece : bare.collectPieces()) {
                for (var ring : collectEveryRing(piece)) {

                    var labels = ring.edgeLabels();

                    for (var corner = 0; corner < ring.vertices().size(); corner++) {

                        if (labels[corner] == BareVoid.THE_FRAME) {
                            continue;
                        }

                        var site = bare.union().sites().get(labels[corner]);

                        assertThat(Points.computeDistance(ring.vertices().get(corner), site))
                            .as("corner %d against cell %d", corner, labels[corner])
                            .isBetween(nearest, furthest);
                    }
                }
            }
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void onlyTheSeaRunsAlongTheFrame(String sector) {
            // A pocket is closed by cells the whole way round. One with the frame in its
            // boundary would be a pocket that leaked out to the edge of the sector, which is
            // the sea by another name.
            for (var piece : readBare(sector).collectPieces()) {

                if (!piece.holes().isEmpty()) {
                    continue;
                }

                assertThat(piece.edgeLabels())
                    .doesNotContain(BareVoid.THE_FRAME);
            }
        }
    }

    private static BareVoid readBare(String sector) {

        var fixture = SectorFixture.loadSector(sector);

        return BareVoid.readBareVoid(readCellEdges(sector), fixture.getSites(), KNOBS);
    }

    private static java.util.Map<?, List<CellEdge>> readCellEdges(String sector) {

        return SectorFixture.loadSector(sector)
            .buildCellEdgesBySystemKey(KNOBS.cellRadius(), KNOBS.boundSegments());
    }

    // The whole framed area, taken as the sea's own outline: everything inside the frame,
    // before the cells and the pockets divide it up.
    private static double measureFramedArea(BareVoid bare) {

        for (var piece : bare.collectPieces()) {

            if (!piece.holes().isEmpty()) {
                return Math.abs(PolygonRegions.computeSignedArea(piece.boundary()));
            }
        }
        throw new IllegalStateException("no framed piece to measure against");
    }

    // How far a chord of the cells' bound falls inside the bound at its middle, which is the
    // whole of the difference between a pocket read off the cells and one read off the arcs.
    private static double measureSagitta() {

        return KNOBS.cellRadius() * (1 - Math.cos(Math.PI / KNOBS.boundSegments()));
    }

    private static List<LabelledRing> collectEveryRing(Face piece) {

        var rings = new java.util.ArrayList<LabelledRing>();

        rings.add(new LabelledRing(piece.boundary(), piece.edgeLabels()));
        rings.addAll(piece.holes());

        return rings;
    }

    private static List<double[]> readRing(List<CellEdge> edges) {

        var ring = new java.util.ArrayList<double[]>(edges.size());

        for (var edge : edges) {
            ring.add(new double[] {edge.x1(), edge.y1()});
        }
        return ring;
    }
}
