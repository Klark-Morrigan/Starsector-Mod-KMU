package kmu.maplayers.politicalmap.base.politics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Inverts the ownership map into each faction's member systems, so every faction's
     * cluster(s) can be traced from its own members. Takes the owner map rather than any
     * render state, so the production build, the incremental refresh, and the debug overlay -
     * which resolves owners without building draw lists - all group the same way.
     *
     * @param ownerBySystemId the dominant owner per owned system
     * @return each faction id mapped to its owned system ids, in the map's iteration order
     */
    public static Map<String, List<String>> groupSystemIdsByFactionId(
            Map<String, DominantOwner> ownerBySystemId) {
        var systemIdsByFactionId = new LinkedHashMap<String, List<String>>();
        for (var entry : ownerBySystemId.entrySet()) {
            systemIdsByFactionId
                    .computeIfAbsent(entry.getValue().factionId(), factionId -> new ArrayList<>())
                    .add(entry.getKey());
        }
        return systemIdsByFactionId;
    }
}
