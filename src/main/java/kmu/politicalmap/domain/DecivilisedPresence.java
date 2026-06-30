package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects star systems holding a decivilised planet the player has learned
 * about - a former colony, now factionless ruins.
 *
 * <p>A decivilised planet makes its system count as inhabited for the political
 * map even though no faction owns it: the system seeds a cell and always draws,
 * but politically unaffiliated, so it takes no faction color and never counts
 * toward dominance. This is the single source of truth for "is there a revealed
 * dead colony here", read both by the visibility rule that admits such a system
 * to the geometry and by the overlay that draws it neutral.
 *
 * <p>Reveal mirrors vanilla's own visibility rather than second-guessing it: the
 * player must have encountered the planet (its market is past
 * {@link MarketAPI.SurveyLevel#NONE}), and the {@code decivilized} condition
 * must be visible under its own survey rule - immediately when it does not
 * require surveying, otherwise once surveyed. A colony that decivilises during
 * play has its condition force-revealed by the engine, so it surfaces at once; a
 * procgen dead world follows whatever survey bar the condition carries.
 */
public final class DecivilisedPresence {

    private DecivilisedPresence() {
    }

    /**
     * @param system the system to scan; null yields false
     * @return true when the system holds at least one planet whose decivilised
     *         status is revealed to the player
     */
    public static boolean hasRevealedDecivilisedPlanet(StarSystemAPI system) {
        if (system == null) {
            return false;
        }
        for (var planet : system.getPlanets()) {
            var market = planet.getMarket();
            if (market != null && isRevealedDecivilised(market)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param sector the sector to scan; null yields an empty set
     * @return the ids of every system holding a revealed decivilised planet
     */
    public static Set<String> findRevealedDecivilisedSystemIds(SectorAPI sector) {
        var systemIds = new HashSet<String>();
        if (sector == null) {
            return systemIds;
        }
        for (var system : sector.getStarSystems()) {
            if (hasRevealedDecivilisedPlanet(system)) {
                systemIds.add(system.getId());
            }
        }
        return systemIds;
    }

    // Whether this market is a dead colony the player can already see is dead.
    // Two independent gates: encountered at all (a never-visited world sits at
    // SurveyLevel.NONE), and the decivilised condition being visible under
    // vanilla's own per-condition survey rule.
    private static boolean isRevealedDecivilised(MarketAPI market) {
        var hasBeenEncountered = market.getSurveyLevel() != MarketAPI.SurveyLevel.NONE;
        if (!hasBeenEncountered) {
            return false;
        }
        var condition = market.getSpecificCondition(Conditions.DECIVILIZED);
        return condition != null
                && (!condition.requiresSurveying() || condition.isSurveyed());
    }
}
