package kmu.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;

import kmu.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.politicalmap.refresh.PoliticalMapRefresh;
import kmu.politicalmap.refresh.PoliticalMapSectorWatcher;

/**
 * Marks a discovered market's system politics-stale, so a concealed colony or
 * station (e.g. Knights of Ludd's Battlestar Libra) paints its system the moment
 * it is found, not only on reload.
 *
 * <p>Discovering a market reveals a new dominant owner over a cell that already
 * exists - the same per-system ownership change a colony resize makes - so it
 * routes through the same targeted refresh
 * ({@link PoliticalMapRefresh#markSystemPoliticsStale}): only that system and its
 * neighbours are re-derived and re-shaped, not the whole economy. Discoveries
 * that could change which systems are reachable (a jump point, a gate) are not
 * handled here - gate activation in particular is a separate, later step from
 * discovery - so that accessibility is left to {@link PoliticalMapSectorWatcher},
 * the single place that judges it.
 */
public class PoliticalMapDiscoveryListener implements DiscoverEntityListener {

    @Override
    public void reportEntityDiscovered(SectorEntityToken entity) {
        // Most discoveries carry no market (a jump point, inert salvage); those
        // touch politics not at all, so the marketless case is filtered here before
        // the shared refresh. The discovered entity rides along in the log so a
        // colony that does (or does not) paint on discovery can be traced to it.
        if (entity == null) {
            return;
        }
        MarketPoliticsRefresh.markSystemStaleForMarket(entity.getMarket(), "discovered market",
                "entity=" + entity.getId());
    }
}
