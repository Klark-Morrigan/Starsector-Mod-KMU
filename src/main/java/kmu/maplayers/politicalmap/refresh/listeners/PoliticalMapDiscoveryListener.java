package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;

import kmu.maplayers.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.refresh.PoliticalMapStalenessSource;

/**
 * Marks a discovered market's system politics-stale, so an undiscovered colony or
 * station (e.g. Knights of Ludd's Battlestar Libra) paints its system the moment
 * it is found, not only on reload.
 *
 * <p>Discovering a market can reveal a new dominant holder over a cell that already exists, which
 * {@link MarketPoliticsRefresh} turns into a targeted mark. A discovery that could change which
 * systems are reachable - a jump point, a gate - is not handled here: gate activation is a separate,
 * later step from discovery, so reachability is left to {@link PoliticalMapStalenessSource}.
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
