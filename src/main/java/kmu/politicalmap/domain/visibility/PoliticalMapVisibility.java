package kmu.politicalmap.domain.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.politicalmap.domain.politics.KnownMarketFootprints;
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
        return shouldAppearOnMap(system, visibleStars, isInhabited(sector, system));
    }

    /**
     * Decides map membership from an inhabitation flag the caller already has,
     * rather than re-reading the economy to recompute it. The single-walk
     * fingerprint scan reads each system's markets once - to size dominance and to
     * know if it is inhabited - so it passes that flag straight in here instead of
     * paying for a second economy read through {@link #isInhabited}.
     *
     * @param system       the system to test
     * @param visibleStars the index of systems whose star the map draws
     * @param isInhabited   whether the system holds a known colony or a revealed
     *                      dead colony, decided by the caller
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(StarSystemAPI system, MapVisibleStars visibleStars,
            boolean isInhabited) {
        return hasVisibleMapAccess(system, visibleStars) || isInhabited;
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
        return KnownMarketFootprints.hasKnownOwnedMarket(sector, system)
                || DecivilisedPresence.hasRevealedDecivilisedPlanet(system);
    }

    /**
     * The fingerprint contribution of one on-map system, identifying it by id and
     * folding in its draw class so a decivilised shell reads differently from a
     * live colony on the same system. Summing this over every on-map system gives
     * the visibility fingerprint the sector watcher polls: identity-based and
     * order-independent, so it still moves when one system enters as another
     * leaves, and a freshly revealed ruin - a draw-class flip on a system already
     * shown - shifts it without the membership set changing.
     *
     * <p>The ownership half of the picture (who holds each system) is tracked
     * separately, as a per-system owner map collected in the same walk but never
     * blended in here - this hashes which systems are drawn, not who owns them.
     *
     * @param systemId              the on-map system's id
     * @param isRevealedDecivilised whether the system is drawn only as a revealed
     *                              dead colony, which salts its contribution so a
     *                              live-to-dead flip is caught
     * @return the value to add into the visibility fingerprint
     */
    public static int computeVisibilityContribution(String systemId,
            boolean isRevealedDecivilised) {
        var idHash = systemId.hashCode();
        return isRevealedDecivilised ? DECIVILISED_FINGERPRINT_SALT * idHash : idHash;
    }
}
