package kmu.maplayers.ownermap.owners;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The owner a star system is painted by on an owner map, paired with the two shades its cell can
 * draw in.
 *
 * <p>The render-ready output of a layer's owner source: the layer settles who owns each system and
 * pairs that answer with the owner's shades, so the render stages consume a plain owner and never
 * learn what the ID means - a faction, a group of factions, or a value with nothing to do with
 * either. The player points each map element (fill, outer border, inner seam) at one of the two
 * slots through the settings, which is why the shades are carried by slot rather than by element.
 * Retaining the ID beside the shades keeps the owner available for per-owner styling and naming,
 * decided off the same holding the fill was.
 *
 * @param ownerId the owner's ID - the key the geometry fuses cells by
 * @param palette the two shades this owner's cells may draw in
 */
public record SystemOwner(
    String ownerId,
    OwnerPalette palette) {

    /**
     * Maps each owned system to its owner's ID - the per-system key the owner-map geometry clusters
     * by. Adapts the owner map to the opaque {@code Map<SystemKey, String>} the agnostic geometry
     * ({@code CellShaper}, {@code SystemClusters}, {@code SystemClusterBorders},
     * {@code ClusterBorderTrace}) fuses on, so the owner side supplies "who owns this" while the
     * geometry stays ignorant of what an owner is.
     *
     * @param ownerBySystemKey the owner per owned system
     * @return each system's owner ID, in the map's iteration order
     */
    public static Map<SystemKey, String> mapOwnerIdBySystemKey(
            Map<SystemKey, SystemOwner> ownerBySystemKey) {

        var keyBySystemKey = new LinkedHashMap<SystemKey, String>();
        for (var entry : ownerBySystemKey.entrySet()) {
            keyBySystemKey.put(entry.getKey(), entry.getValue().ownerId());
        }
        return keyBySystemKey;
    }

    /**
     * The drawn cells' grouping under an owner map: which system each cell draws as, paired with
     * each owned system's owner ID. The one place the two halves are joined into a
     * {@link CellGrouping}, so every pass that groups the drawn cells groups them the same way
     * rather than each re-composing the pair.
     *
     * <p>Lives here rather than on {@code CellGrouping} so the geometry layer stays ignorant of
     * owners: this is the owner side supplying "who owns this cell", exactly as
     * {@link #mapOwnerIdBySystemKey} does for the key half.
     *
     * @param systemKeyByCellKey the system each cell draws as, from the geometry cache
     * @param ownerBySystemKey   the owner per owned system
     * @return the cells grouped by the owner ID of the system each draws as
     */
    public static CellGrouping mapCellGrouping(
            Map<SystemKey, SystemKey> systemKeyByCellKey,
            Map<SystemKey, SystemOwner> ownerBySystemKey) {
        return new CellGrouping(systemKeyByCellKey, mapOwnerIdBySystemKey(ownerBySystemKey));
    }

    /**
     * Which of the given systems nobody owns, under an owner map.
     *
     * <p>The candidate rule for a spotlit owner's presence, which is only ever asked about systems
     * the map attributes to nobody: one somebody owns already draws in that owner's cluster group,
     * so where the pick also lives there changes nothing. Stated once here, so a source resolving
     * the whole sector and the refresh folding one system cannot come to disagree about what
     * "unowned" means.
     *
     * @param ownerBySystemKey    who owns each system; a system nobody owns is absent
     * @param candidateSystemKeys the systems to filter, in the order they are to be answered in
     * @return those of them no owner was resolved for
     */
    public static Set<SystemKey> selectUnownedSystemKeysAmong(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            Set<SystemKey> candidateSystemKeys) {

        var unownedSystemKeys = new LinkedHashSet<SystemKey>();

        for (var systemKey : candidateSystemKeys) {
            if (!ownerBySystemKey.containsKey(systemKey)) {
                unownedSystemKeys.add(systemKey);
            }
        }
        return unownedSystemKeys;
    }
}
