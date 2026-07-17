package kmu.maplayers.politicalmap.base.geometry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rule that splits unowned cells into the two kinds the frontier treats differently:
 * frontier-empty and deep-empty.
 *
 * <p>An unowned cell that touches an owned neighbour is frontier-empty - the dead star sits
 * against somebody's territory, so its space is what an owner reaches into and it is the only
 * cell a frontier pass redistributes. An unowned cell surrounded only by other unowned cells
 * is deep-empty: distant void nobody borders, left exactly as a plain neutral cell so far-off
 * space keeps its character. An owned cell is neither.
 *
 * <p>The split rides on {@link EdgeClassifier}: an unowned cell's edge is an OPEN_FRONTIER
 * exactly when an owner holds the far side, so "has an owned neighbour" needs no second
 * ownership rule of its own. Kept free of the geometry cache and of GL so it can be exercised
 * on hand-built cells and key maps.
 */
public final class EmptyCellClassifier {

    private EmptyCellClassifier() {
    }

    /**
     * Every frontier-empty cell in a cell set: unowned, and touching at least one owned
     * neighbour. Owned cells and deep-empty cells are both absent, so a caller iterating
     * the result visits precisely the cells a frontier pass redistributes.
     *
     * @param cellEdgesByCellId each cell, as its adjacency-tagged edges
     * @param grouping          which system each cell draws as and each system's grouping
     *                          key; a cell with no system, or an ungrouped one, is unowned
     * @return the ids of the unowned cells that touch an owned one, possibly empty
     */
    public static Set<String> collectFrontierEmptyCellIds(
            Map<String, List<CellEdge>> cellEdgesByCellId,
            CellGrouping grouping) {
        var frontierEmptyCellIds = new LinkedHashSet<String>();
        for (var cell : cellEdgesByCellId.entrySet()) {
            var cellId = cell.getKey();
            if (isFrontierEmpty(grouping.resolveGroupKeyOf(cellId), cell.getValue(),
                    grouping.groupKeyBySystemId())) {
                frontierEmptyCellIds.add(cellId);
            }
        }
        return frontierEmptyCellIds;
    }

    /**
     * Whether one cell is frontier-empty: it is itself unowned and at least one of its edges
     * faces an owned system. The map-reach bound never qualifies - it has no star across it -
     * because {@link EdgeClassifier#classifyAcross} rules it a plain boundary.
     *
     * @param ownGroupKey         the grouping key of this cell, or null if it is unowned
     * @param cellEdges           this cell's edges, each tagged with what lies across it
     * @param groupKeyBySystemId  the grouping key per system, to resolve each neighbour
     * @return true only for an unowned cell with an owned neighbour
     */
    public static boolean isFrontierEmpty(
            String ownGroupKey,
            List<CellEdge> cellEdges,
            Map<String, String> groupKeyBySystemId) {
        // An owned cell is never redistributed, whoever it borders, so it short-circuits
        // before the edge walk.
        if (ownGroupKey != null) {
            return false;
        }
        for (var edge : cellEdges) {
            // Own key is null here, so an OPEN_FRONTIER edge means the far side is owned.
            if (EdgeClassifier.classifyAcross(edge, null, groupKeyBySystemId)
                    == EdgeClass.OPEN_FRONTIER) {
                return true;
            }
        }
        return false;
    }
}
