package kmu.maplayers.base.geometry.ui;

import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.v3.BridgedContinents;
import kmu.maplayers.base.geometry.v3.CoastFrontages;
import kmu.maplayers.base.geometry.v3.ShippedMap;
import kmu.maplayers.base.geometry.v3.VoidBridgeCache;
import kmu.maplayers.base.geometry.v4.LabelledWall;
import kmu.maplayers.base.geometry.v4.LakeBridges;
import kmu.maplayers.base.geometry.v4.LakeCoast;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v3's lake bridges laid into v4, over the real sectors.
 *
 * <p>Here rather than beside {@link LakeBridges}, because it reads both constructions and the
 * layering keeps each of them from reading the other - in tests as in the code.
 *
 * <p><b>Three things are pinned.</b> That v4 lays exactly the bridges v3 draws, since the tier is
 * v3's search placed into a partition and not a second search; that every bridge lands on the
 * lake's frontage, since that is the one place a bridge may land; and what the bridges do once
 * laid over the coast, since a line that divides water may not take any of it away.
 */
class LakeBridgesIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.ui.LakeBridgesIntegrationTest#provideSectorNames";

    private static final SectorGeometryParameters KNOBS = ShippedMap.KNOBS;

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

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class LayBridgeWalls {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theBridgesLaidAreExactlyTheOnesV3Draws(String sector) {
            // Placed, not searched again: a tier that filtered or moved a bridge on the way in
            // would be a second answer that agrees with the first only until one is tuned.
            var bridges = layContinents(sector).layLakeSpans();
            var laid = LakeBridges.layBridgeWalls(
                bridges, SectorFixture.loadSector(sector).getSites(), KNOBS);

            assertThat(laid.lines())
                .hasSameSizeAs(bridges);

            for (var index = 0; index < bridges.size(); index++) {

                assertThat(laid.lines().get(index))
                    .containsExactly(bridges.get(index).start(), bridges.get(index).end());
            }
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyBridgeLandsOnItsOwnCellsLakeFrontage(String sector) {
            // The one place a bridge may land: where the lake's coast runs along the cell, or
            // the single point where it touches one. Both ends, each on the cell it names - a
            // foot on a neighbour's frontage would be a bridge carried through the wrong shore.
            var continents = layContinents(sector);
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
        void eachBridgeDividesOnePieceInTwo(String sector) {
            // The floor under the two checks below, which bridges that divided nothing would
            // pass - and exact, since the search keeps no bridge that crosses another: each runs
            // from one shore of a piece to another shore of the same piece, and cuts it in two.
            var bridgeCount = layContinents(sector).layLakeSpans().size();

            assertThat(readPartition(sector, true).countPieces())
                .isEqualTo(readPartition(sector, false).countPieces() + bridgeCount);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theBridgesTakeNoVoidAwayBarSliversBelowTheMapsResolution(String sector) {
            // Measured against the partition the coast left, since that is what the bridges are
            // laid onto: the void is the same number either side of them, bar the faces the walk
            // does not keep, which are those under a sagitta squared.
            var bridgeCount = layContinents(sector).layLakeSpans().size();
            var sagitta = KNOBS.measureBoundSagitta();

            assertThat(PartitionAreas.measureVoid(readPartition(sector, false))
                    - PartitionAreas.measureVoid(readPartition(sector, true)))
                .isBetween(
                    -AREA_ROUNDING,
                    SLIVERS_PER_BRIDGE * bridgeCount * sagitta * sagitta + AREA_ROUNDING);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theOpenSeaIsUntouched(String sector) {
            // Every lake is inside a continent, so no lake bridge reaches the sea.
            assertThat(PartitionAreas.measureSea(readPartition(sector, true)))
                .isCloseTo(PartitionAreas.measureSea(readPartition(sector, false)), withinPercentage(AREA_SHARE));
        }
    }

    private static BridgedContinents layContinents(String sector) {

        return BridgedContinents.layContinents(
            SectorFixture.loadSector(sector).getSites(),
            KNOBS,
            ShippedMap.COAST_RULES,
            ShippedMap.SPAN_RULES,
            new VoidBridgeCache());
    }

    // The partition with the lake coast laid, and the bridges over it where asked for - the
    // order the window lays them in.
    private static VoidPartition readPartition(String sector, boolean shouldLayBridges) {

        var fixture = SectorFixture.loadSector(sector);
        var sites = fixture.getSites();
        var continents = layContinents(sector);
        var walls = new ArrayList<LabelledWall>(LakeCoast.layCoastWalls(
                LakeReaches.collectLakeReaches(continents.traceCoasts(), KNOBS.borderInset()),
                sites,
                KNOBS)
            .walls());

        if (shouldLayBridges) {
            walls.addAll(
                LakeBridges.layBridgeWalls(continents.layLakeSpans(), sites, KNOBS).walls());
        }

        return VoidPartition.readVoidPartition(
            fixture.buildCellEdgesBySystemKey(KNOBS), sites, KNOBS, walls);
    }
}
