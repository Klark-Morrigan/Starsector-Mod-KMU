package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DiscUnionBoundary;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for v4's base reading of the void, over the real sectors.
 *
 * <p>What is pinned here is that the base is the SWEEP'S answer and not a second opinion about
 * it. v4 divides the void for itself from 1.4 onward, and the only thing that can say whether
 * a division is right is the undivided void it started from - so if this reading ever drifts
 * from the sweep's, every later check is being made against the wrong baseline and nothing
 * downstream would say so.
 *
 * <p>The sweep is sound here in a way it is not elsewhere: handed no walls it reaches none of
 * the accommodations it carries for walls of no width, which is why it is worth pinning against
 * rather than replacing.
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

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class ReadBareVoid {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void readBareVoidFindsTheSamePiecesTheSweepDoes(String sector) {

            var fixture = SectorFixture.loadSector(sector);

            var read = BareVoid.readBareVoid(fixture.getSites(), KNOBS);
            var swept = DiscUnionBoundary.traceHoles(
                new DiscUnion(fixture.getSites(), KNOBS.cellRadius()),
                KNOBS.boundSegments());

            assertThat(read.countPieces())
                .isEqualTo(swept.size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void readBareVoidFindsSomeVoidInEverySector(String sector) {

            var fixture = SectorFixture.loadSector(sector);

            assertThat(BareVoid.readBareVoid(fixture.getSites(), KNOBS).countPieces())
                .isPositive();
        }
    }

    @Nested
    class CollectOutlines {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void collectOutlinesGivesOneRingPerPiece(String sector) {

            var fixture = SectorFixture.loadSector(sector);
            var bare = BareVoid.readBareVoid(fixture.getSites(), KNOBS);

            assertThat(bare.collectOutlines())
                .hasSize(bare.countPieces());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void collectOutlinesGivesEveryRingEnoughVerticesToEncloseArea(String sector) {

            var fixture = SectorFixture.loadSector(sector);

            assertThat(BareVoid.readBareVoid(fixture.getSites(), KNOBS)
                    .collectOutlines())
                .allSatisfy(ring ->
                    assertThat(ring.size())
                        .isGreaterThanOrEqualTo(Limits.MIN_VERTICES_TO_ENCLOSE_AREA));
        }

        // Wound the way every other filled shape on this map is, which is what lets a piece be
        // handed to the painting without asking which direction it came back in.
        @ParameterizedTest
        @MethodSource(SECTORS)
        void collectOutlinesGivesEveryRingTheFillWinding(String sector) {

            var fixture = SectorFixture.loadSector(sector);

            assertThat(BareVoid.readBareVoid(fixture.getSites(), KNOBS)
                    .collectOutlines())
                .allSatisfy(ring ->
                    assertThat(PolygonRegions.computeSignedArea(ring)).isPositive());
        }
    }
}
