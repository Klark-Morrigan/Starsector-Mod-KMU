package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.hashing.Avalanche;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;

import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;

/**
 * Decides which star systems appear on the political map, and fingerprints that
 * set so the overlay knows when to rebuild.
 *
 * <p>Two independent reasons put a system on the map, and reachability
 * ({@link StarSystems#isReachable}) is only one of them. A system appears when it
 * has a normal means of arrival OR when it is inhabited - a discovered faction
 * colony, or a revealed decivilised planet. The inhabitation path is what admits
 * an otherwise unreachable system: a transverse-only or abyssal world, hidden
 * from the map by its own design, still shows once it holds a colony or a known
 * dead colony, regardless of the star-hidden / abyssal tags it carries.
 *
 * <p>Affiliation is a separate axis owned elsewhere: a decivilised-only system
 * is inhabited (it seeds a cell and always draws) yet unaffiliated (no faction
 * color, no dominance) - see the ownership pipeline and
 * {@link DecivilisedMarkets}. Reading inhabitation here lets the geometry
 * seeding and the refresh fingerprint share one rule.
 */
public final class PoliticalMapVisibility {
    // Salt XORed into a decivilised system's id hash before the avalanche, so a
    // revealed ruin - a draw-class change on a system already on the map - lands a
    // different contribution from that same system drawn live, and the fingerprint
    // moves even when the membership set does not.
    private static final int DECIVILISED_FINGERPRINT_SALT = 31;

    // Seed XORed into every contribution before the avalanche. fmix32 maps 0 to 0,
    // so an id hashing to 0 would otherwise contribute 0 and vanish from the sum;
    // seeding shifts that single blind spot off 0 onto an arbitrary value (the
    // golden-ratio constant) that no real system id hashes to.
    private static final int FINGERPRINT_SEED = 0x9e3779b9;

    private PoliticalMapVisibility() {
    }

    /**
     * Convenience for single-system callers: scans hyperspace for visible stars,
     * then defers to {@link #shouldAppearOnMap(SectorAPI, StarSystemAPI,
     * VisibleStars)}. Callers that walk every system should scan once and pass
     * the index instead, to avoid rescanning hyperspace per system.
     *
     * @param sector the sector the system belongs to; supplies the economy read
     * @param system the system to test
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(
            SectorAPI sector,
            StarSystemAPI system) {

        return shouldAppearOnMap(
                sector,
                system,
                VisibleStars.scan(sector));
    }

    /**
     * @param sector       the sector the system belongs to; supplies the economy
     *                     read
     * @param system       the system to test
     * @param visibleStars the index of systems whose star the map draws, scanned
     *                     once by the caller
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(
            SectorAPI sector,
            StarSystemAPI system,
            VisibleStars visibleStars) {
                
        return shouldAppearOnMap(
                sector,
                system,
                visibleStars,
                PoliticalMapDevOverrides.NONE);
    }

    /**
     * Convenience for single-system callers under the dev reveal overrides: reads the
     * system's inhabitation with undiscovered colonies folded in when show-all-factions
     * is on, then applies the force override so a system the normal rule would omit
     * still appears.
     *
     * @param sector       the sector the system belongs to; supplies the economy read
     * @param system       the system to test
     * @param visibleStars the index of systems whose star the map draws, scanned once
     *                     by the caller
     * @param overrides    the pass's dev reveal overrides - show-all-factions widens the
     *                     inhabitation read, force-all-systems admits the system outright
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(
            SectorAPI sector,
            StarSystemAPI system,
            VisibleStars visibleStars,
            PoliticalMapDevOverrides overrides) {

        return shouldAppearOnMap(
                system,
                visibleStars,
                isInhabited(
                        sector,
                        system,
                        overrides.isShowingAllFactions()),
                overrides.isForcingAllSystemsOnMap());
    }

    /**
     * Decides map membership from an inhabitation flag the caller already has,
     * rather than re-reading the economy to recompute it, with a dev force override
     * that admits the system outright.
     *
     * <p>The single-walk fingerprint scan reads each system's markets once - to size
     * dominance and to know if it is inhabited - so it passes that flag straight in
     * here instead of paying for a second economy read through {@link #isInhabited}.
     *
     * @param system          the system to test
     * @param visibleStars    the index of systems whose star the map draws
     * @param isInhabited     whether the system holds a folded colony or a revealed
     *                        dead colony, decided by the caller
     * @param isForcedOntoMap whether the "force all systems" dev reveal admits the
     *                        system regardless of access or inhabitation
     * @return true when the system should seed a political-map cell
     */
    public static boolean shouldAppearOnMap(
            StarSystemAPI system,
            VisibleStars visibleStars,
            boolean isInhabited,
            boolean isForcedOntoMap) {

        return isForcedOntoMap
                || hasVisibleMapAccess(system, visibleStars)
                || isInhabited;
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
    public static boolean isInhabited(
            SectorAPI sector,
            StarSystemAPI system) {

        return isInhabited(
                sector,
                system,
                false); // Should not include undiscovered colonies by default.
    }

    /**
     * Whether the system counts as inhabited under the dev reveal, folding in
     * undiscovered colonies when show-all-factions is on. A revealed decivilised
     * planet still counts regardless of the flag - it is always known once revealed.
     *
     * @param sector                       the sector the system belongs to; null yields
     *                                     false
     * @param system                       the system to test; null yields false
     * @param shouldIncludeUndiscoveredMarkets whether an undiscovered colony counts as
     *                                     inhabitation (the "show all factions" dev
     *                                     reveal); false applies the normal filter
     * @return true when the system holds a colony or a known dead colony
     */
    public static boolean isInhabited(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        return StarSystems.hasKnownOwnedMarket(
                    sector,
                    system,
                    shouldIncludeUndiscoveredMarkets)
                || DecivilisedMarkets.hasRevealedDecivilisedPlanet(system);
    }

    /**
     * The fingerprint contribution of one on-map system, identifying it by id and
     * folding in its draw class so a decivilised shell reads differently from a
     * live colony on the same system. Summing this over every on-map system gives
     * the visibility fingerprint the sector watcher polls: order-independent, so it
     * still moves when one system enters as another leaves, and collision-resistant
     * because each contribution is avalanched before the sum - a freshly revealed
     * ruin, a draw-class flip on a system already shown, shifts it without the
     * membership set changing.
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
    public static int computeVisibilityContribution(
            String systemId,
            boolean isRevealedDecivilised) {

        // Seed, fold the draw class in, then avalanche before the caller sums it.
        // Summing raw id hashes lets structured values cancel - hashes that are
        // small or related can net to no change across a swap - so the drawn set
        // could shift without moving the fingerprint. Spreading each id across all
        // 32 bits makes such a cancellation need a full 32-bit coincidence, while
        // staying a sum keeps the fingerprint order-independent. The seed covers
        // fmix32's lone fixed point at 0, so a 0-hash id still contributes non-zero.
        var idHash = systemId.hashCode();
        var drawClassSalt = isRevealedDecivilised ? DECIVILISED_FINGERPRINT_SALT : 0;

        return Avalanche.mixBits(idHash ^ FINGERPRINT_SEED ^ drawClassSalt);
    }

    // The access path onto the map: the system is reachable AND the vanilla map
    // draws it. Composing reachability with the draw check here keeps
    // StarSystems.isReachable about reachability alone.
    private static boolean hasVisibleMapAccess(
            StarSystemAPI system,
            VisibleStars visibleStars) {

        return StarSystems.isReachable(system)
                && isDrawnOnMap(system, visibleStars);
    }

    // Whether the vanilla map draws the system at all: as its star (a visible
    // star anchor leads into it) or as a nebula cloud. A nebula carries no star
    // anchor, so it is absent from the visible-star index; reading isNebula() here
    // keeps a reachable nebula on the political map. A deliberately hidden star
    // (star_hidden_on_map, an abyssal rogue object) has neither a visible anchor
    // nor the nebula flag, so it stays off - mirroring what the player sees drawn.
    private static boolean isDrawnOnMap(
            StarSystemAPI system,
            VisibleStars visibleStars) {
        return visibleStars.isStarVisibleForSystem(system)
                || system.isNebula();
    }
}
