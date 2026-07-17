package kmu.maplayers.politicalmap.base.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the drawn cells group: which system each cell draws as, and the grouping key each
 * system carries.
 *
 * <p>A cell is not a system. Most cells are one star's own ground and draw as that star,
 * but a cell can also be ground one owner holds without a star of its own in it, or a
 * leftover piece of space belonging to nobody. So "who holds this cell" is two lookups,
 * not one: the cell resolves to the system it draws as, and that system resolves to a
 * grouping key. Pairing the two maps here keeps that resolution in one place rather than
 * leaving each consumer to compose them - and keeps the key map itself purely about
 * systems, which is what the politics layer actually knows.
 *
 * <p>Cell ids and system ids share one namespace, and a system's own cell is always keyed
 * by that system's id. That is what lets an edge tagged with a system across it resolve to
 * the neighbouring cell as well as to the neighbour's key. A cell with no system of its
 * own takes an id belonging to neither, and is simply absent from {@code systemIdByCellId}.
 *
 * <p>Pure lookups over opaque keys, so any layer can group by whatever it clusters on (the
 * faction layer keys by dominant-faction id).
 *
 * @param systemIdByCellId   the system each cell draws as; a cell absent here has no system
 *                           of its own and so no owner, whatever ground it sits on
 * @param groupKeyBySystemId the grouping key per system; a system absent here is ungrouped
 */
public record CellGrouping(
        Map<String, String> systemIdByCellId,
        Map<String, String> groupKeyBySystemId) {

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
     * The grouping key one cell falls under - the key of the system it draws as.
     *
     * @param cellId the cell to resolve
     * @return that cell's grouping key, or null when the cell has no system or its system
     *         is ungrouped
     */
    public String resolveGroupKeyOf(String cellId) {
        var systemId = resolveDrawnSystemIdOf(cellId);
        return systemId == null ? null : groupKeyBySystemId.get(systemId);
    }

    /**
     * Inverts the grouping into each key's member cells, so a key's region can be traced
     * from the cells that actually draw it rather than from the systems behind them - the
     * two differ wherever an owner holds ground no star of its own sits in.
     *
     * @return each grouping key mapped to its member cell ids, in cell iteration order;
     *         ungrouped cells appear under no key
     */
    public Map<String, List<String>> groupCellIdsByKey() {
        var cellIdsByKey = new LinkedHashMap<String, List<String>>();
        for (var cellId : systemIdByCellId.keySet()) {
            var groupKey = resolveGroupKeyOf(cellId);
            if (groupKey != null) {
                cellIdsByKey.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(cellId);
            }
        }
        return cellIdsByKey;
    }
}
