package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.Set;

/**
 * Records which star systems show a star on the vanilla hyperspace map, so the
 * political map can mirror that visibility.
 *
 * <p>A system's star is drawn on the map by its star-anchor jump point. The
 * engine suppresses that draw with the {@code star_hidden_on_map} tag for
 * abyssal rogue-stellar objects - a neutron star, magnetar, or black hole whose
 * one-way gravity-well exit is meant to stay hidden - while leaving the system
 * its (fringe) jump points. The tag lives on the hyperspace anchor, not on the
 * system, its in-system jump points, or {@code getHyperspaceAnchor()} (which is
 * the bare {@code system_anchor} token), so it is only reachable by scanning
 * hyperspace.
 *
 * <p>This scans once and indexes the locations of the visible (untagged) star
 * anchors. A star anchor sits at its system's hyperspace coordinates, so a
 * system is map-visible when a visible anchor is indexed at its location.
 * Building the index once keeps the per-system query an O(1) lookup, so callers
 * that walk every system (the visibility fingerprint, the geometry rebuild) do
 * not rescan hyperspace per system.
 */
public final class MapVisibleStars {

    private final Set<String> visibleAnchorLocationKeys;

    private MapVisibleStars(Set<String> visibleAnchorLocationKeys) {
        this.visibleAnchorLocationKeys = visibleAnchorLocationKeys;
    }

    /**
     * Scans the sector's hyperspace for the star anchors the map draws.
     *
     * @param sector the sector to scan; null, or a sector with no hyperspace,
     *               yields an empty index (no star treated as visible)
     * @return an index of the locations holding a visible star anchor
     */
    public static MapVisibleStars scan(SectorAPI sector) {
        var visibleAnchorLocationKeys = new HashSet<String>();
        var hyperspace = sector == null ? null : sector.getHyperspace();
        if (hyperspace != null) {
            indexVisibleStarAnchors(hyperspace, visibleAnchorLocationKeys);
        }
        return new MapVisibleStars(visibleAnchorLocationKeys);
    }

    /**
     * @param system the system to test
     * @return true when a visible star anchor sits at the system's hyperspace
     *         location - i.e. the vanilla map draws its star
     */
    public boolean isStarVisibleForSystem(StarSystemAPI system) {
        return visibleAnchorLocationKeys.contains(locationKey(system.getLocation()));
    }

    private static void indexVisibleStarAnchors(LocationAPI hyperspace, Set<String> keys) {
        for (Object entity : hyperspace.getEntities(JumpPointAPI.class)) {
            var jumpPoint = (JumpPointAPI) entity;
            // Only a star anchor draws a system's star; one tagged hidden (an
            // abyssal rogue object) does not, so its location is left out and the
            // system reads as map-invisible.
            if (jumpPoint.isStarAnchor() && !jumpPoint.hasTag(Tags.STAR_HIDDEN_ON_MAP)) {
                keys.add(locationKey(jumpPoint.getLocation()));
            }
        }
    }

    // A star anchor sits at its system's hyperspace coordinates, so rounded
    // coordinates key the two together. Systems are thousands of units apart, so
    // rounding to the nearest unit cannot collide distinct systems.
    private static String locationKey(Vector2f location) {
        if (location == null) {
            return "null";
        }
        return Math.round(location.x) + ":" + Math.round(location.y);
    }
}
