package kmu.maplayers.politicalmap.base.politics;

import kmu.maplayers.base.geometry.CellGrouping;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The faction holding a star system on the political map, paired with the two
 * palette colors its cell can draw in.
 *
 * <p>The render-ready output of the ownership pipeline: {@link SectorPolitics}
 * resolves the dominant faction and its palette into this value, so the render
 * layer consumes a plain owner and never reaches into the economy or touches
 * {@code FactionAPI}. The two colors are the faction's own authored UI shades -
 * {@link #primaryColor()} its bright color, {@link #secondaryColor()} its dark
 * color - and the player points each map element (fill, outer border, inner seam)
 * at one of them through the "Faction ... color" settings. Naming them by palette
 * slot rather than by element keeps the record neutral about which element uses
 * which, since that pairing is the player's choice. Retaining the id beside the
 * colors keeps the owner available for per-owner styling (dimming independent-held
 * space, for one) and later per-owner behaviour, decided off the same dominance the
 * fill was.
 */
public record DominantOwner(String factionId, Color primaryColor, Color secondaryColor) {

    /**
     * Maps each owned system to its dominant-faction id - the per-system grouping key the
     * political-map geometry clusters by. Adapts the faction ownership map to the opaque
     * {@code Map<String, String>} the agnostic geometry ({@code CellShaper},
     * {@code SystemClusters}, {@code SystemClusterBorders}, {@code PoliticalBorderTrace}) fuses on, so
     * the faction layer supplies "who owns this" while the geometry stays ignorant of factions.
     *
     * @param ownerBySystemId the dominant owner per owned system
     * @return each system's faction id, in the map's iteration order
     */
    public static Map<String, String> mapFactionIdBySystemId(
            Map<String, DominantOwner> ownerBySystemId) {
        var keyBySystemId = new LinkedHashMap<String, String>();
        for (var entry : ownerBySystemId.entrySet()) {
            keyBySystemId.put(entry.getKey(), entry.getValue().factionId());
        }
        return keyBySystemId;
    }

    /**
     * The drawn cells' grouping under an ownership map: which system each cell draws as, paired
     * with each owned system's faction id. The one place the two halves are joined into a
     * {@link CellGrouping}, so the production build, the incremental refresh, the label pass, and
     * the debug overlay all group the cells the same way rather than each re-composing the pair.
     *
     * <p>Lives here rather than on {@code CellGrouping} so the geometry layer stays ignorant of
     * factions: this is the faction layer supplying "who owns this cell", exactly as
     * {@link #mapFactionIdBySystemId} does for the key half.
     *
     * @param systemIdByCellId the system each cell draws as, from the geometry cache
     * @param ownerBySystemId  the dominant owner per owned system
     * @return the cells grouped by the faction id of the system each draws as
     */
    public static CellGrouping mapCellGrouping(
            Map<String, String> systemIdByCellId,
            Map<String, DominantOwner> ownerBySystemId) {
        return new CellGrouping(systemIdByCellId, mapFactionIdBySystemId(ownerBySystemId));
    }
}
