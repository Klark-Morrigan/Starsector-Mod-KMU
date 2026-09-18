package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.BareVoidBoundary;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidHole;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v4's base partition, over the real sectors, against the sweep.
 *
 * <p>The sweep is the oracle. Handed no walls it reaches none of the accommodations it carries
 * for walls of no width, so its holes are the cells' own void exactly - and the walk, closing
 * those same rings with nothing laid across them, has to hand every one of them back corner for
 * corner. Anything less is the walk being wrong, and there is nowhere else to learn that from.
 *
 * <p>This is also where the welding meets real load: hundreds of corners at sector scale, some
 * a few units apart where two arcs cross at a shallow angle, rather than the four corners of a
 * hand-built square. A tolerance that swallows any of them shows up here as a piece lost or a
 * corner moved, which the corner-for-corner check is there to see.
 *
 * <p>The partition check at this tier is area conservation: the pieces cover the sweep's void
 * once over, so their areas sum to its. A piece overlapping another would sum high and a piece
 * missing would sum low. Chosen over a vertex-inside test because from the next tier on the
 * pieces share edges, and a vertex on a neighbour's boundary is not inside it.
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

    // How far two corners may stand apart and still be one. The walk hands back the sweep's
    // own points, so anything above rounding is a different corner.
    private static final double SAME_POINT = 1e-6;

    // How far, in percent, the pieces' total area may sit from the sweep's. The shoelace over
    // the same corners in the same order is bit-identical, so this is room for the sum's
    // rounding across a few hundred corners and nothing more.
    private static final double AREA_SHARE = 1e-9;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class ReadBareVoid {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void readBareVoidFindsTheSamePiecesTheSweepDoes(String sector) {

            assertThat(readBare(sector).countPieces())
                .isEqualTo(traceSweep(sector).size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyHoleTheSweepFindsComesBackAsOnePieceCornerForCorner(String sector) {
            // Exactly one, not at least one: the same hole walked twice is the fault a
            // half-edge started from both directions would produce, and it passes every count
            // of pieces against holes that happens to lose another piece elsewhere.
            var outlines = readBare(sector).collectOutlines();

            for (var hole : traceSweep(sector)) {

                assertThat(outlines)
                    .filteredOn(outline -> isTheSameRing(outline, hole.boundary()))
                    .as("the hole ringed by cells %s", hole.ringing())
                    .hasSize(1);
            }
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void thePiecesCoverTheSweepsVoidOnceOver(String sector) {

            var covered = 0.0;

            for (var outline : readBare(sector).collectOutlines()) {
                covered += PolygonRegions.computeSignedArea(outline);
            }

            var swept = 0.0;

            for (var hole : traceSweep(sector)) {
                swept += Math.abs(PolygonRegions.computeSignedArea(hole.boundary()));
            }

            assertThat(covered)
                .isCloseTo(swept, withinPercentage(AREA_SHARE));
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void readBareVoidFindsSomeVoidInEverySector(String sector) {

            assertThat(readBare(sector).countPieces())
                .isPositive();
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

    private static BareVoid readBare(String sector) {
        return BareVoid.readBareVoid(SectorFixture.loadSector(sector).getSites(), KNOBS);
    }

    private static List<VoidHole> traceSweep(String sector) {

        return BareVoidBoundary.traceBareHoles(
            DiscUnion.buildAtCellReach(SectorFixture.loadSector(sector).getSites(), KNOBS),
            KNOBS.boundSegments());
    }

    // Whether two rings are one ring: the same corners in the same order, starting anywhere. A
    // walk starts each face at whichever edge it reached first, so the outline is a rotation
    // of the hole rather than a copy of it.
    private static boolean isTheSameRing(List<double[]> outline, List<double[]> hole) {

        if (outline.size() != hole.size()) {
            return false;
        }

        for (var offset = 0; offset < hole.size(); offset++) {
            if (matchesFrom(outline, hole, offset)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFrom(List<double[]> outline, List<double[]> hole, int offset) {

        for (var index = 0; index < outline.size(); index++) {

            var corner = outline.get(index);
            var expected = hole.get((index + offset) % hole.size());

            if (Points.computeDistance(corner, expected) > SAME_POINT) {
                return false;
            }
        }
        return true;
    }
}
