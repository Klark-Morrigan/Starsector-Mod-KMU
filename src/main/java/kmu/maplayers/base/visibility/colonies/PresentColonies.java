package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.LocationMarkets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every colony the sector still holds, by ID - what a reconciliation asks before it sheds what a
 * sighting was about.
 *
 * <p>Read off the raw market listings rather than off a resolved colony set. This asks what the
 * sector holds at all, not what may be shown of it: a colony set settles which of the markets on a
 * place is the colony and drops the rest, and a market superseded today can win its place tomorrow.
 * An ID present under any market anywhere is an ID a sighting may still be about.
 *
 * <p>Both listings and hyperspace together, the same two ways a sighting is recorded, so a
 * reconciliation cannot drop a colony merely for having been found by a listing this walk forgot
 * about.
 *
 * <p>Swept only when there is something to shed. The commonest load has nothing recorded at all,
 * and reading the whole sector to discover that is the reconciliation's entire cost paid for no
 * result - so this is handed over as a question rather than an answer.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state, and
 * null-defensive like the rest of the map framework.
 */
final class PresentColonies {

    private PresentColonies() {
        // utility class, no instances.
    }

    /**
     * Every colony ID the sector still holds, hyperspace included.
     *
     * @param sector the sector to walk; null yields nothing, there being no listing to read
     * @return the IDs, wherever each colony stands; never null
     */
    static Set<String> readColonyIds(SectorAPI sector) {

        var colonyIds = new HashSet<String>();

        if (sector == null) {
            return colonyIds;
        }
        var systems = sector.getStarSystems();

        if (systems != null) {
            for (var system : systems) {
                collectColonyIdsIn(sector, system, colonyIds);
            }
        }
        collectColonyIdsIn(sector, sector.getHyperspace(), colonyIds);

        return colonyIds;
    }

    private static void collectColonyIdsIn(
            SectorAPI sector,
            LocationAPI location,
            Set<String> colonyIds) {

        for (var market : readMarketsIn(sector, location)) {

            var colonyId = market.getId();

            // A colony the game names with nothing can hold no sighting of its own, a sighting
            // being filed under the very ID this walk is compared against.
            if (colonyId != null) {
                colonyIds.add(colonyId);
            }
        }
    }

    // The markets present in one place, both listings together: the economy's, and the ones hung on
    // the place's own entities that it never registered.
    private static List<MarketAPI> readMarketsIn(SectorAPI sector, LocationAPI location) {

        var markets = new ArrayList<MarketAPI>(LocationMarkets.readMarkets(sector, location));

        markets.addAll(LocationMarkets.readMarketsUnlistedByEconomy(sector, location));

        return markets;
    }
}
