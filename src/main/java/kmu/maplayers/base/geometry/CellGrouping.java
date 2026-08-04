package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the drawn cells group: which system each cell draws as, and the owner each
 * system carries.
 *
 * <p>A cell is not a system. Most cells are one star's own and draw as that star,
 * but a cell can also be one an owner holds without a star of its own in it, or a
 * leftover piece of space no owner holds. So "who owns this cell" is two
 * lookups, not one: the cell resolves to the system it draws as, and that system resolves
 * to an owner. Pairing the two maps here keeps that resolution in one place rather
 * than leaving each consumer to compose them - and keeps the owner map itself purely about
 * systems, which is the level a layer assigns owners at.
 *
 * <p>Cell ids and system ids share one namespace, and a system's own cell is always keyed
 * by that system's id. That is what lets an edge tagged with a system across it resolve to
 * the neighbouring cell as well as to the neighbour's owner. A cell with no system of its
 * own takes an id belonging to neither, and is simply absent from {@code systemIdByCellId}.
 *
 * <p>Pure lookups over opaque owners, so any layer can group by whatever it clusters on.
 *
 * @param systemIdByCellId the system each cell draws as; a cell absent here has no system
 *                         of its own and so no owner, wherever on the map it sits
 * @param ownerBySystemId  the owner per system; a system absent here is unowned
 */
public record CellGrouping(
    Map<String, String> systemIdByCellId,
    Map<String, String> ownerBySystemId) {

    /**
     * The system one cell draws as - whose owner, palette, and name it takes.
     *
     * @param cellId the cell to resolve
     * @return that cell's system, or null when the cell has no system of its own
     */
    public String resolveDrawnSystemIdOf(String cellId) {
        return systemIdByCellId.get(cellId);
    }

    /**
     * The owner one cell falls under - the owner of the system it draws as.
     *
     * @param cellId the cell to resolve
     * @return that cell's owner, or null when the cell has no system or its system
     *         is unowned
     */
    public String resolveOwnerOf(String cellId) {
        var systemId = resolveDrawnSystemIdOf(cellId);
        return systemId == null
            ? null
            : ownerBySystemId.get(systemId);
    }

    /**
     * Inverts the grouping into each owner's member cells, so an owner's cluster can be traced
     * from the cells that actually draw it rather than from the systems behind them - the
     * two differ wherever an owner holds a cell no star of its own sits in.
     *
     * @return each owner mapped to its member cell ids, in cell iteration order;
     *         unowned cells appear under no owner
     */
    public Map<String, List<String>> groupCellIdsByOwner() {
        var cellIdsByOwner = new LinkedHashMap<String, List<String>>();
        for (var cellId : systemIdByCellId.keySet()) {
            var owner = resolveOwnerOf(cellId);
            if (owner != null) {
                cellIdsByOwner.computeIfAbsent(owner, key -> new ArrayList<>()).add(cellId);
            }
        }
        return cellIdsByOwner;
    }
}
