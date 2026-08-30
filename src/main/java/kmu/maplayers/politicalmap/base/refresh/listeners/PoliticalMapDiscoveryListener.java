package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;

/**
 * Marks a discovered market's system politics-stale, so an undiscovered colony or
 * station (e.g. Knights of Ludd's Battlestar Libra) paints its system the moment
 * it is found, not only on reload.
 *
 * <p>Discovering a market reveals a new dominant holder over a cell that already
 * exists - the same per-system holder change a colony resize makes - so it
 * routes through the same targeted refresh
 * ({@link MapLayerRefreshBoard#markSystemGroupingStale}): only that system and its
 * neighbours are re-derived and re-shaped, not the whole economy. Discoveries
 * that could change which systems are reachable (a jump point, a gate) are not
 * handled here - gate activation in particular is a separate, later step from
 * discovery - so that accessibility is left to {@link PoliticalMapStalenessSource},
 * the single place that judges it.
 *
 * <p>Holds the sector it was installed on, so a discovery is reported against that sector's overlay
 * rather than against whichever sector is currently loaded.
 */
public class PoliticalMapDiscoveryListener implements DiscoverEntityListener {

    private final SectorAPI sector;

    /**
     * @param sector the sector this listener is installed on, whose overlay a discovery here
     *               repaints
     */
    public PoliticalMapDiscoveryListener(SectorAPI sector) {
        this.sector = sector;
    }

    @Override
    public void reportEntityDiscovered(SectorEntityToken entity) {

        // Most discoveries carry no market (a jump point, inert salvage); those
        // touch politics not at all, so the marketless case is filtered here before
        // the shared refresh. The discovered entity rides along in the log so a
        // colony that does (or does not) paint on discovery can be traced to it.
        if (entity == null) {
            return;
        }
        MarketPoliticsRefresh.reportMarketChange(
            sector,
            entity.getMarket(),
            "discovered market", // Event.
            "entity=" + entity.getId()); // Context.
    }
}
