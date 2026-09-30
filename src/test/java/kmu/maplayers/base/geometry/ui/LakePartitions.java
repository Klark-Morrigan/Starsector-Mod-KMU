package kmu.maplayers.base.geometry.ui;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.v3.BridgedContinents;
import kmu.maplayers.base.geometry.v3.SectorPipeline;
import kmu.maplayers.base.geometry.v4.LabelledWall;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.SectorPartitions;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real sectors' partitions with the lake tier's lines laid, as the window lays them: v3
 * finds the lines, and v4 divides its void along them.
 *
 * <p>Shared by the suites that lay the tier, so each substep is judged against the partition the
 * one before it left rather than against a second laying of it that only happens to agree.
 * Here in the viewer's package because it reads both constructions, which nothing below the
 * window may. The laying is v3's own pipeline's, under the shipped rules at the knobs every v4
 * suite reads the void at, so the two constructions' suites are about one map; the walks with
 * the tier's lines in are kept per sector, as the bare one is.
 */
final class LakePartitions {

    private static final Map<String, VoidPartition> COAST_PARTITIONS = new ConcurrentHashMap<>();

    private static final Map<String, VoidPartition> BRIDGED_PARTITIONS = new ConcurrentHashMap<>();

    private static final Map<String, List<LabelledWall>> COAST_WALLS = new ConcurrentHashMap<>();

    private LakePartitions() {
    }

    // One sector laid under the shipped rules, opened once for every suite of either version.
    static BridgedContinents layContinents(String sector) {
        return SectorPipeline.layContinentsIn(sector);
    }

    // Every reach of every lake's coast, under the map's own channel.
    static List<CellGap> collectReaches(BridgedContinents continents) {

        return LakeReaches.collectLakeReaches(
            continents.traceCoasts(), SectorPartitions.KNOBS.borderInset());
    }

    // The void with the lake coast laid.
    static VoidPartition readCoastPartition(String sector) {

        return COAST_PARTITIONS.computeIfAbsent(
            sector, name -> SectorPartitions.readPartition(name, layCoastWalls(name)));
    }

    // The void with the lake coast laid, and the bridges over it.
    static VoidPartition readBridgedPartition(String sector) {

        return BRIDGED_PARTITIONS.computeIfAbsent(sector, name -> {

            var walls = new ArrayList<LabelledWall>(layCoastWalls(name));

            walls.addAll(LakeTier.layBridgeWalls(
                    layContinents(name).layLakeSpans(),
                    SectorPartitions.readFrontier(name),
                    SectorPartitions.loadFixture(name).getSites(),
                    SectorPartitions.KNOBS)
                .walls());

            return SectorPartitions.readPartition(name, walls);
        });
    }

    // The lake coast's walls as laid, which is what a piece's edges lie on and what the tier's
    // capture is judged against.
    static List<LabelledWall> layCoastWalls(String sector) {

        return COAST_WALLS.computeIfAbsent(sector, name -> LakeTier.layCoastWalls(
                collectReaches(layContinents(name)),
                SectorPartitions.readFrontier(name),
                SectorPartitions.loadFixture(name).getSites(),
                SectorPartitions.KNOBS)
            .walls());
    }
}
