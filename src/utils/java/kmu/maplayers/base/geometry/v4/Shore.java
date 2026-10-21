package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The line between cell and void, as everything that lands a line on it needs it: the frontier,
 * each cell's own part of it, where the cells stand, and how far a line's end can float off it.
 *
 * <p>One value because the four travel together through every tier and are only meaningful
 * together - a frontier indexed against one set of cells and handed beside the sites of another
 * moves ends towards the wrong place - and because the index is worth building once. An end is
 * moved onto its own cell's part of the frontier, and a scan of the whole frontier per end is
 * the same answer found thousands of times over.
 */
public final class Shore {

    private final List<LabelledWall> frontier;
    private final Map<Integer, List<LabelledWall>> frontierByCell;
    private final List<double[]> sites;
    private final double sagitta;

    private Shore(
            List<LabelledWall> frontier,
            Map<Integer, List<LabelledWall>> frontierByCell,
            List<double[]> sites,
            double sagitta) {

        this.frontier = frontier;
        this.frontierByCell = frontierByCell;
        this.sites = sites;
        this.sagitta = sagitta;
    }

    /**
     * Reads the shore off the cells.
     *
     * @param cellEdges  every cell, as its adjacency-tagged edges, in the order the sites come in
     * @param sites      the cells' own positions
     * @param parameters the knobs the cells were drawn under, for the resolution that says how
     *                   far an end can float off the frontier
     * @return the shore
     */
    public static Shore readShore(
            Map<?, List<CellEdge>> cellEdges,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        return indexShore(VoidPartition.collectFrontier(cellEdges), sites, parameters);
    }

    /**
     * Indexes a frontier already read, by the cell each of its walls is labelled with.
     *
     * @param frontier   every cell edge facing void, labelled with its cell
     * @param sites      the cells' own positions
     * @param parameters the knobs the cells were drawn under
     * @return the shore
     */
    public static Shore indexShore(
            List<LabelledWall> frontier,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        var frontierByCell = new LinkedHashMap<Integer, List<LabelledWall>>();

        for (var wall : frontier) {

            frontierByCell
                .computeIfAbsent(wall.label(), cell -> new ArrayList<>())
                .add(wall);
        }

        return new Shore(
            List.copyOf(frontier),
            frontierByCell,
            sites,
            parameters.measureBoundSagitta());
    }

    /**
     * Every cell edge facing void.
     *
     * @return the frontier, in the order it was read
     */
    public List<LabelledWall> getFrontier() {
        return frontier;
    }

    /**
     * One cell's part of the frontier.
     *
     * @param cell the cell
     * @return its edges facing void, in the order the frontier holds them; none for a cell that
     *         faces no void
     */
    public List<LabelledWall> listFrontierOf(int cell) {
        return frontierByCell.getOrDefault(cell, List.of());
    }

    /**
     * Where the cells stand.
     *
     * @return the sites, index-aligned with the cells the frontier is labelled with
     */
    public List<double[]> getSites() {
        return sites;
    }

    /**
     * How far the drawn frontier sits inside the true one at most, which is how far a line
     * anchored on the true reach can float off it.
     *
     * @return the sagitta of the cells' bound
     */
    public double getSagitta() {
        return sagitta;
    }
}
