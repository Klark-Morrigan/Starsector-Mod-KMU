package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SectorStarSystems;

import java.util.Map;
import java.util.function.Predicate;

/**
 * The rule that decides which systems a map layer draws, and their live
 * positions, in one place.
 *
 * <p>Several passes need the same drawn set: the geometry cache builds a cell per
 * drawn system, and the motion tracker follows a drawn system that moves. Owning the
 * membership rule here - {@link MapVisibility} applied over one hyperspace
 * scan - keeps those passes from drifting apart on what "drawn" means, and lets each
 * walk it independently through the same predicate rather than re-deriving it.
 */
public final class DrawnSystemPositions {

    private DrawnSystemPositions() {
    }

    /**
     * The drawn-set membership rule as a predicate over a single hyperspace scan -
     * the one place that rule lives, so every walk that needs the drawn set (the
     * geometry sites, the motion tracker) shares it rather than re-deriving it and
     * drifting.
     *
     * @param sector          the sector whose hyperspace is scanned for visible stars
     * @param visibilityRules the pass's visibility rules - the colony rule and the force
     *                        override, resolved once by the caller
     * @return a predicate accepting exactly the systems the map draws under those rules
     */
    public static Predicate<StarSystemAPI> buildDrawnSystemPredicate(
            SectorAPI sector,
            MapVisibilityRules visibilityRules) {

        // Scanned once here so the predicate's per-system check is an O(1) lookup
        // rather than a per-system hyperspace rescan.
        var visibleStars = VisibleStars.scan(sector);
        return system -> MapVisibility.shouldAppearOnMap(
            sector,
            system,
            visibleStars,
            visibilityRules);
    }

    /**
     * Walks the sector once under the drawn-set rule and records the live
     * {@code {x, y}} position of every on-map system, keyed by system id.
     *
     * @param sector          the sector to read; null yields an empty map
     * @param visibilityRules the pass's visibility rules applied by the drawn-set rule
     * @return each drawn system's live hyperspace position keyed by id; a system with
     *         no location is skipped, since it has no site to place a cell at
     */
    public static Map<String, double[]> collectLivePositions(
            SectorAPI sector,
            MapVisibilityRules visibilityRules) {

        return SectorStarSystems.collectPositionsById(
            sector,
            buildDrawnSystemPredicate(sector, visibilityRules));
    }
}
