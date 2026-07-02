package kmu.politicalmap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;

import org.apache.log4j.Logger;

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
 * discovery - so that accessibility is left to {@link PoliticalMapAccessWatcher},
 * the single place that judges it.
 */
public class PoliticalMapDiscoveryListener implements DiscoverEntityListener {
    private static final Logger LOG = Global.getLogger(PoliticalMapDiscoveryListener.class);

    @Override
    public void reportEntityDiscovered(SectorEntityToken entity) {
        if (entity == null || entity.getMarket() == null) {
            return;
        }
        var market = entity.getMarket();
        // A market not seated in a star system (a deep-hyperspace station) seeds
        // no political-map cell, so its discovery can change no drawing.
        var system = market.getStarSystem();
        if (system == null) {
            return;
        }
        // Logged with the entity, market, and system id so a colony that does (or
        // does not) paint on discovery can be traced to this event.
        LOG.debug("Political map politics stale on discovered market; entity="
                + entity.getId() + " market=" + market.getId() + " system=" + system.getId());
        PoliticalMapRefresh.markSystemPoliticsStale(system.getId());
    }
}
