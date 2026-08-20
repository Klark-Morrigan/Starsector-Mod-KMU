package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.hashing.Avalanche;
import kmlib.starsector.colonies.SystemColonies;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;

/**
 * Decides which star systems appear on a map layer, and fingerprints that
 * set so the overlay knows when to rebuild.
 *
 * <p>Two independent reasons put a system on the map, and reachability
 * ({@link StarSystems#isReachable}) is only one of them. A system appears when it
 * has a normal means of arrival OR when it is inhabited - somebody living there the
 * player knows of, or a revealed decivilised planet. What "knows of" admits is the
 * pass's own colony rule rather than anything decided here, so the cell, the band
 * inside it and the box over it are all reading the one rule.
 *
 * <p>Inhabited means somebody lives there, which a derelict hulk is exactly the case
 * against: a system drawn as settled because an abandoned station orbits its star says
 * something false about that system, quite apart from whether the player has been near
 * it. So the reads below take the habitation projection rather than the wider listing
 * of what the player may be told about.
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
     * pass's visibility rules: the inhabitation read follows their colony half, and the
     * force override then admits a system that half would leave off.
     *
     * @param sector          the sector the system belongs to; supplies the economy read
     * @param system          the system to test
     * @param visibleStars    the index of systems whose star the map draws, scanned once
     *                     by the caller
     * @param visibilityRules the pass's visibility rules, resolved once by the caller
     * @return true when the system should seed a map cell
     */
    public static boolean shouldAppearOnMap(
            SectorAPI sector,
            StarSystemAPI system,
            VisibleStars visibleStars,
            MapVisibilityRules visibilityRules) {

        return shouldAppearOnMap(
            system,
            visibleStars,
            isInhabited(sector, system, visibilityRules),
            visibilityRules);
    }

    /**
     * Decides map membership from an inhabitation flag the caller already has,
     * rather than re-reading the economy to recompute it.
     *
     * <p>The single-walk fingerprint scan reads each system's markets once - to size
     * dominance and to know if it is inhabited - so it passes that flag straight in
     * here instead of paying for a second economy read through {@link #isInhabited}.
     * Only the force override is read off the rules here: the colony half is already
     * folded into the flag by whoever computed it.
     *
     * @param system          the system to test
     * @param visibleStars    the index of systems whose star the map draws
     * @param isInhabited     whether the system holds a colony somebody lives on or a
     *                     revealed dead colony, decided by the caller
     * @param visibilityRules the pass's visibility rules, resolved once by the caller
     * @return true when the system should seed a map cell
     */
    public static boolean shouldAppearOnMap(
            StarSystemAPI system,
            VisibleStars visibleStars,
            boolean isInhabited,
            MapVisibilityRules visibilityRules) {

        return visibilityRules.isForcedOntoMap()
            || hasVisibleMapAccess(system, visibleStars)
            || isInhabited;
    }

    /**
     * Whether the system counts as inhabited, walking it for both facts - the form for a caller
     * with a sector and a system and nothing else read yet.
     *
     * <p>Two walks: the colony selection, and every planet in the system for a ruin. A caller
     * that has already made either - a pass that read the system once for everything it asks of
     * it - states the composition through the form below instead of paying for them again.
     *
     * @param sector          the sector the system belongs to; null yields false
     * @param system          the system to test; null yields false
     * @param visibilityRules the pass's visibility rules; only the colony half is read
     *                        here, since forcing a system onto the map does not make it
     *                        inhabited
     * @return true when the system holds a colony somebody lives on or a known dead colony
     */
    public static boolean isInhabited(
            SectorAPI sector,
            StarSystemAPI system,
            MapVisibilityRules visibilityRules) {

        return isInhabited(
            SystemColonies
                .readColoniesIn(sector, system)
                .hasInhabitingColony(visibilityRules.colonyVisibility()),
            DecivilisedMarkets.hasRevealedDecivilisedPlanet(system));
    }

    /**
     * Whether the system counts as inhabited: somebody the player knows of lives there, or a dead
     * colony the player has already seen does. The rule itself, and the only statement of it.
     *
     * <p>A disjunction because the two are two ways a system holds people rather than two
     * requirements - and the ruin is the arm no colony read can answer, nobody owning a dead
     * world, which is why it has to be composed rather than looked up.
     *
     * <p>Habitation rather than the wider known listing, which is what a hover box names its
     * factions out of. The two part over the derelict: a hulk somebody has seen belongs in a
     * listing of what the player may be told about, and nobody has ever lived on it, so a
     * system holding one and nothing else is empty space with a wreck in it.
     *
     * <p>Both arrive as facts already read rather than as a colony set and a system to walk. Every
     * caller with a pass has them - the sector scan reads the ruin anyway, to salt a drawn
     * system's fingerprint, and a render pass answers habitation off its own one walk - so taking
     * the answers is what keeps this from being a second walk hidden inside a rule.
     *
     * @param hasInhabitingColony   whether the system holds a colony somebody lives on that the
     *                              player may be shown, as the caller's own habitation read
     *                              reported it
     * @param isRevealedDecivilised whether the system holds a dead colony the player has already
     *                              seen; counts under any rules, a revealed ruin being known for
     *                              good
     * @return true when the system holds a colony somebody lives on or a known dead colony
     */
    public static boolean isInhabited(
            boolean hasInhabitingColony,
            boolean isRevealedDecivilised) {

        return hasInhabitingColony || isRevealedDecivilised;
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
