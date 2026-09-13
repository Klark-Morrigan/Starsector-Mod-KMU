package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.hashing.Avalanche;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.systems.SystemAccessRoutes;
import kmlib.starsector.systems.SystemKey;

/**
 * Decides which star systems appear on a map layer, and fingerprints that
 * set so the overlay knows when to rebuild.
 *
 * <p>Three independent reasons put a system on the map, and reachability
 * ({@link StarSystems#isReachable}) is only one of them. A system appears when it
 * has a normal means of arrival, OR when an installed mod carries fleets in by a way of its own
 * ({@link SystemAccessRoutes#isReachedByAnyRoute}), OR when it is inhabited - somebody living there the
 * player knows of, the people left on a collapsed colony among them. What "knows of" admits is the
 * pass's own colony rule rather than anything decided here, so the cell, the band
 * inside it and the box over it are all reading the one rule.
 *
 * <p>Inhabitation is not asked here at all, and that is the change a collapsed colony being a
 * colony bought: it is {@link Colonies#hasInhabitingColony}'s answer, over the one
 * walk of the system a pass already made. A rule composed here instead would be a
 * second reading of who is present, free to disagree with the listing the box over the
 * same cell names its factions out of.
 *
 * <p>Inhabited means somebody lives there or did, which a derelict hulk is exactly the
 * case against: a system drawn as settled because an abandoned station orbits its star
 * says something false about that system, quite apart from whether the player has been
 * near it. So the callers below take the habitation projection rather than the wider
 * listing of what the player may be told about.
 *
 * <p>The inhabitation path is what admits
 * an otherwise unreachable system: a transverse-only or abyssal world, hidden
 * from the map by its own design, still shows once it holds a colony or a known
 * collapsed colony, regardless of the star-hidden / abyssal tags it carries.
 *
 * <p>The route path admits the system nobody lives on that a mod nonetheless put somewhere the
 * player can go and can see. Such a destination is reached by an entity of the mod's own rather
 * than a jump point, and marked by an icon of the mod's own rather than a star, so both vanilla
 * reads - arrival and draw - answer no about a place plainly on the map. The route answers for
 * both, which is why it does not also have to pass the draw check the other path composes.
 *
 * <p>Affiliation is a separate axis owned elsewhere: a decivilised-only system
 * is inhabited (it seeds a cell and always draws) yet unaffiliated (no faction
 * colour, no dominance) - see the ownership pipeline. Taking inhabitation as an
 * answer here lets the geometry seeding and the refresh fingerprint share one rule.
 */
public final class MapVisibility {

    // Salt XORed into a decivilised system's folded key before the avalanche, so a
    // shown collapse - a draw-class change on a system already on the map - lands a
    // different contribution from that same system drawn live, and the fingerprint
    // moves even when the membership set does not.
    private static final int DECIVILISED_FINGERPRINT_SALT = 31;

    // Seed XORed into every contribution before the avalanche. fmix32 maps 0 to 0,
    // so a key folding to 0 would otherwise contribute 0 and vanish from the sum;
    // seeding shifts that single blind spot off 0 onto an arbitrary value (the
    // golden-ratio constant) that no real system's key folds to.
    private static final int FINGERPRINT_SEED = 0x9e3779b9;

    private MapVisibility() {
    }

    /**
     * Decides map membership from an inhabitation flag the caller already has.
     *
     * <p>The only form, and the walk it is missing is the point. The caller is a pass that has
     * already read each system once and answers habitation off that reading, so a form that took
     * a sector and read the economy again would be a second walk of every system hidden inside a
     * membership test. Only the force override is read off the rules here: the colony half is
     * already folded into the flag by whoever computed it.
     *
     * @param system          the system to test
     * @param visibleStars    the index of systems whose star the map draws
     * @param isInhabited     whether the system holds a colony somebody lives on or a
     *                     collapsed colony the player may be shown, decided by the caller
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
     * The fingerprint contribution of one on-map system, identifying it by key and
     * folding in its draw class so a decivilised shell reads differently from a
     * live colony on the same system. Summing this over every on-map system gives
     * the visibility fingerprint the sector watcher polls: order-independent, so it
     * still moves when one system enters as another leaves, and collision-resistant
     * because each contribution is avalanched before the sum - a freshly revealed
     * collapse, a draw-class flip on a system already shown, shifts it without the
     * membership set changing.
     *
     * <p>Identified by the whole key rather than by the id alone, because two systems may answer
     * to one id. Both would then contribute the same value, so one entering the drawn set as the
     * other left would move the fingerprint by nothing and the map would go on showing whatever it
     * last built there.
     *
     * <p>The ownership half of the picture (who holds each system) is tracked
     * separately, as a per-system owner map collected in the same walk but never
     * blended in here - this hashes which systems are drawn, not who owns them.
     *
     * @param systemKey             the on-map system's key
     * @param isRevealedDecivilised whether the system is drawn only as a revealed
     *                              collapsed colony, which salts its contribution so a
     *                              live-to-dead flip is caught
     * @return the value to add into the visibility fingerprint
     */
    public static int computeVisibilityContribution(
            SystemKey systemKey,
            boolean isRevealedDecivilised) {

        // Seed, fold the draw class in, then avalanche before the caller sums it.
        // Summing raw key hashes lets structured values cancel - hashes that are
        // small or related can net to no change across a swap - so the drawn set
        // could shift without moving the fingerprint. Spreading each key across all
        // 32 bits makes such a cancellation need a full 32-bit coincidence, while
        // staying a sum keeps the fingerprint order-independent. The seed covers
        // fmix32's lone fixed point at 0, so a 0-hash key still contributes non-zero.
        var keyHash = foldKeyArms(systemKey);
        var drawClassSalt = isRevealedDecivilised ? DECIVILISED_FINGERPRINT_SALT : 0;

        return Avalanche.mixBits(keyHash ^ FINGERPRINT_SEED ^ drawClassSalt);
    }

    // A key's three arms folded into one value, each arm avalanched into the chain before the next
    // is XORed in, so two keys fold together only on a full 32-bit coincidence - the same bar the
    // sum above sets. A linear fold (a multiplier per arm, as a string hashes its characters) would
    // not clear it: string hashes are themselves linear in their characters, so the pair a live
    // sector actually holds - one id, procgen centre names one character apart, short engine-minted
    // anchor ids - can shift one arm by exactly what the other arm shifts back, and fold to one
    // value. Chaining also keeps the fold positional: an entity id standing as one system's centre
    // and another's anchor enters at a different link and lands apart.
    //
    // Folded here rather than taken off the key's own hash, which is derived from the same three
    // arms but by a combination the language does not pin down - a fingerprint has to state how it
    // identifies a system rather than inherit it.
    private static int foldKeyArms(SystemKey systemKey) {

        var fold = Avalanche.mixBits(systemKey.systemId().hashCode());

        fold = Avalanche.mixBits(fold ^ systemKey.centreEntityId().hashCode());

        return Avalanche.mixBits(fold ^ systemKey.anchorEntityId().hashCode());
    }

    // The access path onto the map: an installed mod's own route reaches the
    // system, or it is reachable AND the vanilla map draws it. Composing
    // reachability with the draw check here keeps StarSystems.isReachable about
    // reachability alone.
    //
    // A route stands on its own because the draw check cannot see what a mod
    // paints. A mod-made destination is marked by an icon of the mod's own, never
    // by a star anchor the hyperspace scan can index, so requiring a drawn star of
    // it would hide a place the player is looking straight at - while a granted
    // route is exactly the statement that the mod put a marker there.
    private static boolean hasVisibleMapAccess(
            StarSystemAPI system,
            VisibleStars visibleStars) {

        if (SystemAccessRoutes.isReachedByAnyRoute(system)) {
            return true;
        }
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
