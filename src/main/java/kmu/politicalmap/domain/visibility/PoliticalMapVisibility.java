package kmu.politicalmap.domain.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.politicalmap.domain.politics.SectorPolitics;

/**
 * Decides which star systems appear on the political map, and fingerprints that
 * set so the overlay knows when to rebuild.
 *
 * <p>Two independent reasons put a system on the map, and {@link SystemAccess}
 * is only one of them. A system appears when it has a normal means of arrival
 * (the access rule) OR when it is inhabited - a discovered faction colony, or a
 * revealed decivilised planet. The inhabitation path is what admits an otherwise
 * unreachable system: a transverse-only or abyssal world, hidden from the map by
 * its own design, still shows once it holds a colony or a known dead colony,
 * regardless of the star-hidden / abyssal tags it carries.
 *
 * <p>Affiliation is a separate axis owned elsewhere: a decivilised-only system
 * is inhabited (it seeds a cell and always draws) yet unaffiliated (no faction
 * color, no dominance) - see {@link SectorPolitics} and
 * {@link DecivilisedPresence}. Reading inhabitation here lets the geometry
 * seeding and the refresh fingerprint share one rule.
 */
public final class PoliticalMapVisibility {
    // Salt folded into the fingerprint for an inhabited-but-unaffiliated
    // decivilised system, so a freshly revealed ruin - a draw-class change on a
    // system that was already on the map - moves the fingerprint, not only a
    // system entering or leaving the set.
    private static final int DECIVILISED_FINGERPRINT_SALT = 31;

    private PoliticalMapVisibility() {
    }

    /**
     * Convenience for single-system callers: scans hyperspace for visible stars,
     * then defers to {@link #shouldAppearOnMap(SectorAPI, StarSystemAPI,
     * MapVisibleStars)}. Callers that walk every system should scan once and pass
     * the index instead, to avoid rescanning hyperspace per system.
     *
     * @param sector the sector the system belongs to; supplies the economy read
     * @param system the system to test
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(SectorAPI sector, StarSystemAPI system) {
        return shouldAppearOnMap(sector, system, MapVisibleStars.scan(sector));
    }

    /**
     * @param sector       the sector the system belongs to; supplies the economy
     *                     read
     * @param system       the system to test
     * @param visibleStars the index of systems whose star the map draws, scanned
     *                     once by the caller
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(SectorAPI sector, StarSystemAPI system,
            MapVisibleStars visibleStars) {
        return hasVisibleMapAccess(system, visibleStars) || isInhabited(sector, system);
    }

    // The access path onto the map: the system is reachable AND the vanilla map
    // draws it. Composing access with the draw check here keeps SystemAccess about
    // reachability alone.
    private static boolean hasVisibleMapAccess(StarSystemAPI system, MapVisibleStars visibleStars) {
        return SystemAccess.hasMapAccess(system) && isDrawnOnMap(system, visibleStars);
    }

    // Whether the vanilla map draws the system at all: as its star (a visible
    // star anchor leads into it) or as a nebula cloud. A nebula carries no star
    // anchor, so it is absent from the visible-star index; reading isNebula() here
    // keeps a reachable nebula on the political map. A deliberately hidden star
    // (star_hidden_on_map, an abyssal rogue object) has neither a visible anchor
    // nor the nebula flag, so it stays off - mirroring what the player sees drawn.
    private static boolean isDrawnOnMap(StarSystemAPI system, MapVisibleStars visibleStars) {
        return visibleStars.isStarVisibleForSystem(system) || system.isNebula();
    }

    /**
     * Whether the system counts as inhabited - a discovered faction colony or a
     * revealed decivilised planet. Drives admission to the map independently of
     * how (or whether) the system can be reached.
     *
     * @param sector the sector the system belongs to; null yields false
     * @param system the system to test; null yields false
     * @return true when the system holds a colony or a known dead colony
     */
    public static boolean isInhabited(SectorAPI sector, StarSystemAPI system) {
        return SectorPolitics.hasDiscoveredOwnedMarket(sector, system)
                || DecivilisedPresence.hasRevealedDecivilisedPlanet(system);
    }

    /**
     * A cheap fingerprint of the on-map set, polled to detect when it changes -
     * a gate lighting, a colony founded, a ruin surveyed. Identity-based and
     * order-independent so it still moves when one system enters as another
     * leaves, and it folds in each decivilised system so a draw-class flip on an
     * already-shown system is caught too.
     *
     * @param sector the sector to scan; null yields 0
     * @return a hash of the on-map systems' ids
     */
    public static int computeVisibilityFingerprint(SectorAPI sector) {
        if (sector == null) {
            return 0;
        }
        // Scanned once for the whole walk so the per-system access check stays an
        // O(1) lookup rather than rescanning hyperspace each time.
        var visibleStars = MapVisibleStars.scan(sector);
        var fingerprint = 0;
        for (var system : sector.getStarSystems()) {
            var hasDecivilised = DecivilisedPresence.hasRevealedDecivilisedPlanet(system);
            // The access-and-visibility check is the cheap, common case; the
            // economy read for owned markets runs only when neither it nor a
            // decivilised planet already puts the system on the map.
            var isOnMap = hasVisibleMapAccess(system, visibleStars) || hasDecivilised
                    || SectorPolitics.hasDiscoveredOwnedMarket(sector, system);
            if (!isOnMap) {
                continue;
            }
            var idHash = system.getId().hashCode();
            fingerprint += hasDecivilised ? DECIVILISED_FINGERPRINT_SALT * idHash : idHash;
        }
        return fingerprint;
    }
}
