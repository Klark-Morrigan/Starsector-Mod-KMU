package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.SectorStarSystems;
import kmlib.starsector.systems.SystemColoniesIndex;

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
     * The drawn-set membership rule as a predicate over a pass's own reading of the sector -
     * the one place that rule lives, so every walk that needs the drawn set (the geometry
     * sites, the motion tracker) shares it rather than re-deriving it and drifting.
     *
     * <p>Takes the pass's colony index and hyperspace scan rather than the sector they were
     * made over, so a caller running several walks in one pass pays for one selection per
     * system between them. A predicate given the sector would walk each system afresh for
     * every walk it is handed to.
     *
     * @param colonies        the pass's colony index, which the habitation half is answered
     *                        through; also the sector every read is made against
     * @param visibleStars    the pass's hyperspace scan of which stars the map draws
     * @param visibilityRules the pass's visibility rules - the colony rule and the force
     *                        override, resolved once by the caller
     * @return a predicate accepting exactly the systems the map draws under those rules
     */
    public static Predicate<StarSystemAPI> buildDrawnSystemPredicate(
            SystemColoniesIndex colonies,
            VisibleStars visibleStars,
            MapVisibilityRules visibilityRules) {

        return system -> MapVisibility.shouldAppearOnMap(
            system,
            visibleStars,
            isInhabited(colonies, system, visibilityRules),
            visibilityRules);
    }

    /**
     * Walks the sector once under the drawn-set rule and records the live
     * {@code {x, y}} position of every on-map system, keyed by system id.
     *
     * <p>Opens the index and the hyperspace scan this one walk needs, and discards both with
     * it. That is a pass of its own - the geometry rebuild's - rather than a shortcut past
     * the rule above: one selection per system is paid, and nothing survives to answer a
     * later walk off a sector that has since moved on.
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
            buildDrawnSystemPredicate(
                new SystemColoniesIndex(sector),
                VisibleStars.scan(sector),
                visibilityRules));
    }

    // The two facts membership composes, read off the pass rather than off the sector: the
    // colony set the index already holds, and the system's own planets for a ruin. Stated here
    // because MapVisibility takes the answer rather than the walk that produces it - which is
    // what keeps a second walk of every system from hiding inside a membership test.
    private static boolean isInhabited(
            SystemColoniesIndex colonies,
            StarSystemAPI system,
            MapVisibilityRules visibilityRules) {

        return MapVisibility.isInhabited(
            colonies
                .readColoniesIn(system)
                .hasInhabitingColony(visibilityRules.colonyVisibility()),
            DecivilisedMarkets.hasRevealedDecivilisedPlanet(system));
    }
}
