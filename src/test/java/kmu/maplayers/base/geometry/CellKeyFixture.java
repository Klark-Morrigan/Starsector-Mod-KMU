package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posing the cells of a map: a {@link SystemKey} per named system, and the keyed collections the
 * partition and everything cut from it are addressed by.
 *
 * <p>Every key states the id arm alone, which is what lets a case go on naming its systems "A" and
 * "B" while the code under it addresses them the way a live cut does. A case about two systems
 * sharing an id states the other arms itself, that being the very thing it is about.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class CellKeyFixture {

    private CellKeyFixture() {
        // fixture of static wiring, no instances.
    }

    /**
     * The key one named system is addressed by, stating its id and neither entity arm.
     */
    public static SystemKey buildCellKey(String systemId) {
        return new SystemKey(systemId, null, null);
    }

    /**
     * The keys of several named systems, in the order they are named - a cluster's members, or the
     * cells a trace is asked over.
     */
    public static List<SystemKey> buildCellKeys(String... systemIds) {

        var cellKeys = new ArrayList<SystemKey>(systemIds.length);

        for (var systemId : systemIds) {
            cellKeys.add(buildCellKey(systemId));
        }
        return cellKeys;
    }

    /**
     * A map a case states by name, re-addressed by key and keeping the order it was stated in -
     * for the cell-keyed stores whose values are the case's own, such as a cell's edges or its
     * painted extent.
     */
    public static <V> Map<SystemKey, V> buildKeyedValues(Map<String, V> valuesBySystemId) {

        var valuesByCellKey = new LinkedHashMap<SystemKey, V>();

        for (var entry : valuesBySystemId.entrySet()) {
            valuesByCellKey.put(buildCellKey(entry.getKey()), entry.getValue());
        }
        return valuesByCellKey;
    }

    /**
     * A grouping in which every cell draws as its own star - the identity draws-as over the cells
     * given, which is every cell of a partition that holds no cell without a star - paired with
     * the owners a case states by name.
     */
    public static CellGrouping buildIdentityGrouping(
            Collection<SystemKey> cellKeys,
            Map<String, String> ownerBySystemId) {

        var systemKeyByCellKey = new LinkedHashMap<SystemKey, SystemKey>();

        for (var cellKey : cellKeys) {
            systemKeyByCellKey.put(cellKey, cellKey);
        }
        return new CellGrouping(systemKeyByCellKey, ownerBySystemId);
    }

    /**
     * The identity grouping over cells a case names rather than keys.
     */
    public static CellGrouping buildIdentityGroupingByName(
            Collection<String> systemIds,
            Map<String, String> ownerBySystemId) {

        return buildIdentityGrouping(
            buildCellKeys(systemIds.toArray(String[]::new)),
            ownerBySystemId);
    }

    /**
     * The identity grouping over exactly the systems an owner map names - a partition in which
     * every cell is owned.
     */
    public static CellGrouping buildIdentityGroupingOver(Map<String, String> ownerBySystemId) {
        return buildIdentityGroupingByName(ownerBySystemId.keySet(), ownerBySystemId);
    }

    /**
     * The system each cell draws as, stated by name on both sides - the half of a
     * {@link CellGrouping} that is keyed by cell and valued by system.
     */
    public static Map<SystemKey, SystemKey> buildDrawnSystemKeys(
            Map<String, String> systemIdByCellId) {

        var systemKeyByCellKey = new LinkedHashMap<SystemKey, SystemKey>();

        for (var entry : systemIdByCellId.entrySet()) {
            systemKeyByCellKey.put(
                buildCellKey(entry.getKey()),
                buildCellKey(entry.getValue()));
        }
        return systemKeyByCellKey;
    }
}
