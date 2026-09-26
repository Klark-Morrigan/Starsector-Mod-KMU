package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.v3.Coastlines;
import kmu.maplayers.base.geometry.v4.LakeCoast;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v3's lake coasts handed to v4, over the real sectors.
 *
 * <p>Here rather than beside either construction, because it reads both and the layering keeps
 * each of them from reading the other - in tests as in the code.
 *
 * <p><b>Two things are pinned.</b> Which steps of a coast cross as reaches, since the channel
 * that decides it is the one knob a caller can get wrong without anything failing; and what
 * the reaches do once laid, since a line that divides water may not take any of it away.
 */
class LakeReachesIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.ui.LakeReachesIntegrationTest#provideSectorNames";

    private static final SectorGeometryParameters KNOBS =
        SectorGeometryParameters.createDefaults();

    // A channel of nothing, which is what a continent trace carries of its own: it lays no
    // walls, so the channel on its walls is zero.
    private static final double NO_CHANNEL = 0;

    // How many slivers each reach can close off below the map's resolution: one where each end
    // meets the shore, which is where a reach runs close enough to it to shut in a face thinner
    // than the walk keeps.
    private static final int SLIVERS_PER_REACH = 2;

    // How far, in percent, the open sea may move when the lake coasts go in. The lakes are
    // inside the continents, so the sea is not touched at all, and this is only rounding.
    private static final double AREA_SHARE = 1e-9;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class CollectLakeReaches {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyReachRunsFromOneCellToAnotherAtLeastAChannelLong(String sector) {
            // The two things that make a step of a coast a reach rather than a fillet or a
            // handover: it leaves one cell and arrives on another, and it crosses void wide
            // enough to have two sides.
            var reaches = LakeReaches.collectLakeReaches(traceCoasts(sector), KNOBS.borderInset());

            assertThat(reaches)
                .isNotEmpty()
                .allSatisfy(reach -> {

                    assertThat(reach.fromCell())
                        .isNotEqualTo(reach.toCell());
                    assertThat(Points.computeDistance(reach.from(), reach.to()))
                        .isGreaterThanOrEqualTo(KNOBS.borderInset());
                });
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void aChannelOfNothingTakesHandoversForReaches(String sector) {
            // Why the channel is handed in rather than read off the trace. Where two cells touch,
            // the coast hands over from one to the other in a step of no length, and under a
            // channel of nothing that step names two cells and is long enough - so it would be
            // laid as a wall across the point where two cells meet.
            var traced = traceCoasts(sector);

            assertThat(LakeReaches.collectLakeReaches(traced, NO_CHANNEL))
                .hasSizeGreaterThan(
                    LakeReaches.collectLakeReaches(traced, KNOBS.borderInset()).size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theReachesDivideTheLakes(String sector) {
            // The floor under the two checks below: a coast that divided nothing would pass
            // them both.
            var fixture = SectorFixture.loadSector(sector);
            var cellEdges = fixture.buildCellEdgesBySystemKey(KNOBS);
            var before = VoidPartition.readVoidPartition(cellEdges, fixture.getSites(), KNOBS);

            assertThat(readWithLakeCoast(sector).countPieces())
                .isGreaterThan(before.countPieces());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theReachesTakeNoVoidAwayBarSliversBelowTheMapsResolution(String sector) {
            // A line divides water without consuming any, so the void's area before the lines
            // and after them is the same number - bar the faces the walk does not keep, which
            // are those under a sagitta squared. A reach can shut one in at each end, where it
            // meets the shore; a leak, a lake walked as a tree of no area, costs millions.
            var fixture = SectorFixture.loadSector(sector);
            var cellEdges = fixture.buildCellEdgesBySystemKey(KNOBS);
            var before = VoidPartition.readVoidPartition(cellEdges, fixture.getSites(), KNOBS);
            var reachCount = LakeReaches.collectLakeReaches(
                    traceCoasts(sector),
                    KNOBS.borderInset())
                .size();

            var sagitta = KNOBS.measureBoundSagitta();

            assertThat(measureVoid(before) - measureVoid(readWithLakeCoast(sector)))
                .isBetween(0.0, SLIVERS_PER_REACH * reachCount * sagitta * sagitta);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theOpenSeaIsUntouched(String sector) {
            // Every lake is inside a continent, so no lake coast reaches the sea.
            var fixture = SectorFixture.loadSector(sector);
            var cellEdges = fixture.buildCellEdgesBySystemKey(KNOBS);
            var before = VoidPartition.readVoidPartition(cellEdges, fixture.getSites(), KNOBS);

            assertThat(measureSea(readWithLakeCoast(sector)))
                .isCloseTo(measureSea(before), withinPercentage(AREA_SHARE));
        }
    }

    private static Coastlines.TracedCoasts traceCoasts(String sector) {

        return Coastlines.traceContinentCoasts(
            SectorFixture.loadSector(sector).getSites(),
            KNOBS,
            Coastlines.DEFAULT_RULES);
    }

    // The partition with every lake's coast laid, as the window lays it.
    private static VoidPartition readWithLakeCoast(String sector) {

        var fixture = SectorFixture.loadSector(sector);
        var sites = fixture.getSites();
        var laid = LakeCoast.layCoastWalls(
            LakeReaches.collectLakeReaches(traceCoasts(sector), KNOBS.borderInset()),
            sites,
            KNOBS);

        return VoidPartition.readVoidPartition(
            fixture.buildCellEdgesBySystemKey(KNOBS),
            sites,
            KNOBS,
            laid.walls());
    }

    private static double measureVoid(VoidPartition partition) {

        var area = 0.0;

        for (var piece : partition.collectPieces()) {
            area += piece.measureArea();
        }
        return area;
    }

    // The one piece with holes, which is the sea.
    private static double measureSea(VoidPartition partition) {

        for (var piece : partition.collectPieces()) {

            if (!piece.holes().isEmpty()) {
                return piece.measureArea();
            }
        }
        throw new IllegalStateException("no sea to measure");
    }
}
