package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.systems.ModdedSystemAccessRoutes;
import kmlib.starsector.systems.StarSystems;

/**
 * Decides which star systems appear on a map layer. What that set hashes to, once decided, is
 * {@link MapVisibilityFingerprint}'s.
 *
 * <p>Independent reasons put a system on the map, and an ordinary jump point
 * ({@link StarSystems#hasJumpPointArrival}) is only one of them. A system appears when it
 * has that normal means of arrival and the map draws it, OR when a lit gate
 * ({@link StarSystems#hasActiveGate}) or an installed mod carries fleets in by a way of its own
 * ({@link ModdedSystemAccessRoutes#isReachedByAnyRoute}), OR when it is inhabited - somebody living there the
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
 * near it. So the rule below takes the habitation projection rather than the wider
 * listing of what the player may be told about.
 *
 * <p>The inhabitation path is what admits
 * an otherwise unreachable system: a transverse-only or abyssal world, hidden
 * from the map by its own design, still shows once it holds a colony or a known
 * collapsed colony, regardless of the star-hidden / abyssal tags it carries.
 *
 * <p>The gate path admits the empty, hidden system a lit gate leads to. Lighting a gate joins it to
 * the network every other gate lists, so the player is shown it from wherever they are standing and
 * can conclude the system is there - and the vanilla draw check, which asks only whether a star or a
 * cloud is painted at that point, cannot see any of that. Left to compose with the draw check, a
 * gate the player has just lit would lead somewhere the map draws as nothing.
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

    // The access path onto the map, taken arm by arm rather than through the reachability fold
    // the three of them sum to. Two arrivals stand on their own; the third composes with the draw
    // check, which keeps StarSystems.isReachable about reachability alone.
    //
    // Two arrivals stand on their own, and for one reason: each is itself a thing the player can
    // see, which the draw check cannot know about.
    //
    // A lit gate joins the network every other gate lists, so a player standing at any of them is
    // shown this one and can conclude the system is there - whatever the map draws where it sits.
    // Requiring a drawn star of it would leave a gate the player has just lit leading somewhere
    // the map says is nothing.
    //
    // A mod-made destination is marked by an icon of the mod's own, never by a star anchor the
    // hyperspace scan can index, so requiring a drawn star of it would hide a place the player is
    // looking straight at - while a granted route is exactly the statement that the mod put a
    // marker there.
    //
    // Ordinary jump-point arrival is asked for on its own rather than through isReachable, which
    // would re-ask the gate and the route this has already ruled out - two further walks of the
    // system per test, over a set walked once per drawn system per rebuild.
    private static boolean hasVisibleMapAccess(
            StarSystemAPI system,
            VisibleStars visibleStars) {

        return StarSystems.hasActiveGate(system)
            || ModdedSystemAccessRoutes.isReachedByAnyRoute(system)
            || (StarSystems.hasJumpPointArrival(system) && isDrawnOnMap(system, visibleStars));
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
