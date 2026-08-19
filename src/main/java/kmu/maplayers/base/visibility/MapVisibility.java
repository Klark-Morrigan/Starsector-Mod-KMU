package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.hashing.Avalanche;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.systems.SystemColonies;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides which star systems appear on a map layer, and fingerprints that
 * set so the overlay knows when to rebuild.
 *
 * <p>Two independent reasons put a system on the map, and reachability
 * ({@link StarSystems#isReachable}) is only one of them. A system appears when it
 * has a normal means of arrival OR when it is inhabited - a faction colony the
 * player knows of, or a revealed decivilised planet. Knowing of a colony is wider
 * than having been to it: one the game lists publicly counts before the player has
 * reached it, which is what the cell and the box over it share.
 *
 * <p>The inhabitation path is what admits
 * an otherwise unreachable system: a transverse-only or abyssal world, hidden
 * from the map by its own design, still shows once it holds a colony or a known
 * dead colony, regardless of the star-hidden / abyssal tags it carries.
 *
 * <p>Affiliation is a separate axis owned elsewhere: a decivilised-only system
 * is inhabited (it seeds a cell and always draws) yet unaffiliated (no faction
 * colour, no dominance) - see the ownership pipeline and
 * {@link DecivilisedMarkets}. Reading inhabitation here lets the geometry
 * seeding and the refresh fingerprint share one rule.
 */
public final class MapVisibility {

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

    private MapVisibility() {
    }

    /**
     * Decides map membership by reading the system's inhabitation itself, under the
     * pass's reveal overrides: the inhabitation read widens to undiscovered colonies
     * when the overrides ask for it, and the force override then admits a system the
     * normal rule would omit.
     *
     * @param sector       the sector the system belongs to; supplies the economy read
     * @param system       the system to test
     * @param visibleStars the index of systems whose star the map draws, scanned once
     *                     by the caller
     * @param overrides    the pass's reveal overrides, resolved once by the caller
     * @return true when the system should seed a map cell
     */
    public static boolean shouldAppearOnMap(
            SectorAPI sector,
            StarSystemAPI system,
            VisibleStars visibleStars,
            MapVisibilityOverrides overrides) {

        return shouldAppearOnMap(
            system,
            visibleStars,
            isInhabited(sector, system, overrides),
            overrides);
    }

    /**
     * Decides map membership from an inhabitation flag the caller already has,
     * rather than re-reading the economy to recompute it.
     *
     * <p>The single-walk fingerprint scan reads each system's markets once - to size
     * dominance and to know if it is inhabited - so it passes that flag straight in
     * here instead of paying for a second economy read through {@link #isInhabited}.
     * Only the force override is read off the overrides here: the widening half is
     * already folded into the flag by whoever computed it.
     *
     * @param system       the system to test
     * @param visibleStars the index of systems whose star the map draws
     * @param isInhabited  whether the system holds a folded colony or a revealed
     *                     dead colony, decided by the caller
     * @param overrides    the pass's reveal overrides, resolved once by the caller
     * @return true when the system should seed a map cell
     */
    public static boolean shouldAppearOnMap(
            StarSystemAPI system,
            VisibleStars visibleStars,
            boolean isInhabited,
            MapVisibilityOverrides overrides) {

        return overrides.isForcedOntoMap()
            || hasVisibleMapAccess(system, visibleStars)
            || isInhabited;
    }

    /**
     * Whether the system counts as inhabited - a colony the player knows of or a
     * revealed decivilised planet, plus an undiscovered colony when the overrides widen
     * the read. Drives admission to the map independently of how (or whether) the
     * system can be reached. A revealed decivilised planet counts under any overrides -
     * it is always known once revealed.
     *
     * <p>Walks the system for its colonies and for its ruins, so a caller already holding
     * either - a pass that read the system once for everything it asks of it - wants the
     * form below instead.
     *
     * @param sector    the sector the system belongs to; null yields false
     * @param system    the system to test; null yields false
     * @param overrides the pass's reveal overrides; only the undiscovered-colony
     *                  widening is read here, since forcing a system onto the map does
     *                  not make it inhabited
     * @return true when the system holds a colony or a known dead colony
     */
    public static boolean isInhabited(
            SectorAPI sector,
            StarSystemAPI system,
            MapVisibilityOverrides overrides) {

        return isInhabited(
            SystemColonies.readColoniesIn(sector, system),
            DecivilisedMarkets.hasRevealedDecivilisedPlanet(system),
            overrides);
    }

    /**
     * Whether the system counts as inhabited, answered off the two reads a caller may
     * already have made rather than repeating either.
     *
     * <p>The rule itself, and the one the sector-taking form above resolves its inputs for.
     * Stated over the shared colony set so that "somebody lives here" is the known projection
     * being non-empty - the very set a ribbon counts runs from and a hover box names factions
     * out of - with the revealed dead colony added, which no colony read answers. Two surfaces
     * drawn from one system can then no longer disagree about whether it holds anybody.
     *
     * <p>The ruin arrives as a flag for the same reason {@link #shouldAppearOnMap} takes
     * inhabitation as one: the sector scan reads it anyway, to salt the drawn system's
     * fingerprint, and re-deriving it here would walk every planet in the system a second time.
     *
     * @param colonies              the system's colonies, as one walk of it reported
     * @param isRevealedDecivilised whether the system holds a dead colony the player has
     *                              already seen; counts under any overrides, a revealed ruin
     *                              being known for good
     * @param overrides             the pass's reveal overrides; only the undiscovered-colony
     *                              widening is read here, since forcing a system onto the map
     *                              does not make it inhabited
     * @return true when the system holds a colony or a known dead colony
     */
    public static boolean isInhabited(
            SystemColonies colonies,
            boolean isRevealedDecivilised,
            MapVisibilityOverrides overrides) {

        return colonies.hasKnownColony(overrides.shouldIncludeUndiscoveredMarkets())
            || isRevealedDecivilised;
    }

    /**
     * The sector-wide roll-up of {@link #isInhabited}: every system something stands in,
     * live colony or known ruin alike.
     *
     * <p>Scanned once per pass because the answer is wanted per cell, and a cell asking the
     * economy for itself would put a market walk inside the per-cell styling loop.
     *
     * <p>Read separately from holding because it is a different question. A layer resolves
     * holding under a rule of its own, and a rule that admits only some factions leaves an
     * inhabited system with nobody holding it - which is not the same state as empty space,
     * however alike the two look to a holder lookup that came back null.
     *
     * @param sector    the sector to scan; null yields an empty set
     * @param overrides the pass's reveal overrides, resolved once by the caller
     * @return the ids of every inhabited system
     */
    public static Set<String> findInhabitedSystemIds(
            SectorAPI sector,
            MapVisibilityOverrides overrides) {

        var systemIds = new HashSet<String>();
        if (sector == null) {
            return systemIds;
        }
        for (var system : sector.getStarSystems()) {
            if (isInhabited(sector, system, overrides)) {
                systemIds.add(system.getId());
            }
        }
        return systemIds;
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
    // keeps a reachable nebula on the map. A deliberately hidden star
    // (star_hidden_on_map, an abyssal rogue object) has neither a visible anchor
    // nor the nebula flag, so it stays off - mirroring what the player sees drawn.
    private static boolean isDrawnOnMap(
            StarSystemAPI system,
            VisibleStars visibleStars) {
        return visibleStars.isStarVisibleForSystem(system)
            || system.isNebula();
    }
}
