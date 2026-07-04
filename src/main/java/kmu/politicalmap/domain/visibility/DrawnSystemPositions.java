package kmu.politicalmap.domain.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The live hyperspace position of every system the political map draws, collected in
 * one sector walk.
 *
 * <p>Both the geometry cache (which builds a cell per drawn system) and the position
 * staging (which follows a system that moves) need the same thing: the drawn set and
 * where each of its systems sits. Sharing the walk keeps the two from drifting apart
 * on what "drawn" means - the membership rule is {@link PoliticalMapVisibility}'s
 * alone, applied once here - and folds the per-walk star scan into a single pass.
 */
public final class DrawnSystemPositions {

    private DrawnSystemPositions() {
    }

    /**
     * Walks the sector once and records the live {@code {x, y}} position of every
     * on-map system, keyed by system id, preserving the sector's iteration order.
     *
     * @param sector the sector to read; null yields an empty map
     * @return each drawn system's live hyperspace position; a system with no location
     *         is skipped, since it has no site to place a cell at
     */
    public static Map<String, double[]> collectLivePositions(SectorAPI sector) {
        var positions = new LinkedHashMap<String, double[]>();
        if (sector == null) {
            return positions;
        }
        // Scanned once for the whole walk so each system's access check is an O(1)
        // lookup rather than a per-system hyperspace rescan.
        var visibleStars = MapVisibleStars.scan(sector);
        for (var system : sector.getStarSystems()) {
            var location = system.getLocation();
            if (location == null
                    || !PoliticalMapVisibility.shouldAppearOnMap(sector, system, visibleStars)) {
                continue;
            }
            positions.put(system.getId(), new double[] {location.x, location.y});
        }
        return positions;
    }
}
