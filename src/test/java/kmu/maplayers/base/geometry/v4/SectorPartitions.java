package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.List;
import java.util.Map;

/**
 * The real sectors' partitions, read the one way every suite over them reads them, and the two
 * areas a tier's lines are judged by.
 *
 * <p>Shared rather than copied per suite, so every suite is about the same partition under the
 * same knobs, and every tier is held to one reading of "the void came back whole". Public
 * because the suites that lay a tier's lines sit in the viewer's package, which is the one place
 * allowed to read both constructions.
 */
public final class SectorPartitions {

    /**
     * The cells' own knobs, taken from where they are declared rather than from v3's facade
     * over them. That facade names the cell knobs and the coast rules together, which is the
     * right shape for a report on v3's map and the wrong one here: what the void stands at is a
     * fact about the cells, and v4 has no business reading anything of v3's to learn it.
     */
    public static final SectorGeometryParameters KNOBS = SectorGeometryParameters.createDefaults();

    /** What a partition of the cells alone has laid into it. */
    public static final List<LabelledWall> NOTHING_LAID = List.of();

    private SectorPartitions() {
    }

    /**
     * A sector's void with the given lines laid into it.
     *
     * @param sector    the fixture's name
     * @param laidWalls the lines laid, or {@link #NOTHING_LAID}
     * @return the partition
     */
    public static VoidPartition readPartition(String sector, List<LabelledWall> laidWalls) {

        return VoidPartition.readVoidPartition(
            readCellEdges(sector),
            SectorFixture.loadSector(sector).getSites(),
            KNOBS,
            laidWalls);
    }

    /**
     * A sector's cells as their adjacency-tagged edges, which is what its void is read off.
     *
     * @param sector the fixture's name
     * @return each cell's edges, in the order the cells are numbered
     */
    public static Map<?, List<CellEdge>> readCellEdges(String sector) {
        return SectorFixture.loadSector(sector).buildCellEdgesBySystemKey(KNOBS);
    }

    /**
     * Every piece's area together, holes cut out, which is the void the partition covers.
     *
     * @param partition the partition
     * @return the area
     */
    public static double measureVoid(VoidPartition partition) {

        var area = 0.0;

        for (var piece : partition.collectPieces()) {
            area += piece.measureArea();
        }
        return area;
    }

    /**
     * The sea's area: the one piece with holes, holes cut out.
     *
     * @param partition the partition
     * @return the area
     */
    public static double measureSea(VoidPartition partition) {

        for (var piece : partition.collectPieces()) {

            if (!piece.holes().isEmpty()) {
                return piece.measureArea();
            }
        }
        throw new IllegalStateException("no sea to measure");
    }
}
