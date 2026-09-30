package kmu.maplayers.base.geometry.ui;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.v3.BridgedContinents;
import kmu.maplayers.base.geometry.v3.ShippedMap;
import kmu.maplayers.base.geometry.v3.VoidBridgeCache;
import kmu.maplayers.base.geometry.v4.LabelledWall;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.SectorPartitions;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import java.util.ArrayList;
import java.util.List;

/**
 * The real sectors' partitions with the lake tier's lines laid, as the window lays them: v3
 * finds the lines, and v4 divides its void along them.
 *
 * <p>Shared by the suites that lay the tier, so each substep is judged against the partition the
 * one before it left rather than against a second laying of it that only happens to agree.
 * Here in the viewer's package because it reads both constructions, which nothing below the
 * window may.
 */
final class LakePartitions {

    private LakePartitions() {
    }

    // One sector laid under the shipped rules, at the cell knobs every v4 suite reads the void at.
    static BridgedContinents layContinents(String sector) {

        return BridgedContinents.layContinents(
            SectorFixture.loadSector(sector).getSites(),
            SectorPartitions.KNOBS,
            ShippedMap.COAST_RULES,
            ShippedMap.SPAN_RULES,
            new VoidBridgeCache());
    }

    // Every reach of every lake's coast, under the map's own channel.
    static List<CellGap> collectReaches(BridgedContinents continents) {

        return LakeReaches.collectLakeReaches(
            continents.traceCoasts(), SectorPartitions.KNOBS.borderInset());
    }

    // The void with the lake coast laid.
    static VoidPartition readCoastPartition(String sector) {
        return SectorPartitions.readPartition(sector, layCoastWalls(sector));
    }

    // The void with the lake coast laid, and the bridges over it.
    static VoidPartition readBridgedPartition(String sector) {

        var continents = layContinents(sector);
        var walls = new ArrayList<LabelledWall>(layCoastWalls(sector, continents));

        walls.addAll(LakeTier.layBridgeWalls(
                continents.layLakeSpans(), readFrontier(sector), readSites(sector), SectorPartitions.KNOBS)
            .walls());

        return SectorPartitions.readPartition(sector, walls);
    }

    // The lake coast's walls as laid, which is what a piece's edges lie on and what the tier's
    // capture is judged against.
    static List<LabelledWall> layCoastWalls(String sector) {
        return layCoastWalls(sector, layContinents(sector));
    }

    private static List<LabelledWall> layCoastWalls(String sector, BridgedContinents continents) {

        return LakeTier.layCoastWalls(
                collectReaches(continents), readFrontier(sector), readSites(sector), SectorPartitions.KNOBS)
            .walls();
    }

    private static List<LabelledWall> readFrontier(String sector) {
        return VoidPartition.collectFrontier(SectorPartitions.readCellEdges(sector));
    }

    private static List<double[]> readSites(String sector) {
        return SectorFixture.loadSector(sector).getSites();
    }
}
