package kmu.maplayers.base.geometry.ui;

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
 * suite reads the void at, so the two constructions' suites are about one map; the lakes as
 * handed over and the walks with the tier's lines in are kept per sector, as the bare one is.
 *
 * <p>It also states once what a line may cost the void, since every suite laying one asks.
 */
final class LakePartitions {

    /**
     * How far two sums of the same void may differ by rounding alone, in units squared. The
     * void is some ten billion units squared, added up piece by piece and in different pieces
     * either side of the lines; measured at two millionths on 366, where nothing was lost.
     */
    static final double AREA_ROUNDING = 1e-3;

    /**
     * How far, in percent, the open sea may move when a lake's lines go in. The lakes are inside
     * the continents, so the sea is not touched at all, and this is only rounding.
     */
    static final double SEA_SHARE = 1e-9;

    // How many slivers each laid line can close off below the map's resolution: one where each
    // end meets the shore, which is where a line runs close enough to it to shut in a face
    // thinner than the walk keeps.
    private static final int SLIVERS_PER_LINE = 2;

    private static final Map<String, VoidPartition> COAST_PARTITIONS = new ConcurrentHashMap<>();

    private static final Map<String, VoidPartition> BRIDGED_PARTITIONS = new ConcurrentHashMap<>();

    private static final Map<String, List<LabelledWall>> COAST_WALLS = new ConcurrentHashMap<>();

    private static final Map<String, LakeTier.TracedLakes> TRACED_LAKES =
        new ConcurrentHashMap<>();

    private LakePartitions() {
    }

    // One sector laid under the shipped rules, opened once for every suite of either version.
    static BridgedContinents layContinents(String sector) {
        return SectorPipeline.layContinentsIn(sector);
    }

    // Every lake as the window hands it to v4, under the map's own channel: its reaches, and
    // the cells round it.
    static LakeTier.TracedLakes readTracedLakes(String sector) {

        return TRACED_LAKES.computeIfAbsent(sector, name -> LakeReaches.collectTracedLakes(
            layContinents(name).traceCoasts(), SectorPartitions.KNOBS.borderInset()));
    }

    // The most void a set of laid lines may take away: the slivers the walk does not keep,
    // each under a sagitta squared.
    static double measureSliverAllowance(int lineCount) {

        var sagitta = SectorPartitions.KNOBS.measureBoundSagitta();

        return SLIVERS_PER_LINE * lineCount * sagitta * sagitta;
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
                readTracedLakes(name).reaches(),
                SectorPartitions.readFrontier(name),
                SectorPartitions.loadFixture(name).getSites(),
                SectorPartitions.KNOBS)
            .walls());
    }
}
