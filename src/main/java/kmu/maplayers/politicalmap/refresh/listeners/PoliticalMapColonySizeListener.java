package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;

import kmu.maplayers.politicalmap.refresh.MarketPoliticsRefresh;

/**
 * Marks a system's political-map holding stale when one of its colonies grows
 * or shrinks, so a size change that hands the system to another faction repaints
 * without waiting on a reload.
 *
 * <p>A colony's size feeds its weight, so a resize can flip which faction dominates a system. The
 * engine fires {@link ColonySizeChangeListener} on every resize, and what that event costs is
 * {@link MarketPoliticsRefresh}'s.
 */
public final class PoliticalMapColonySizeListener
        implements ColonySizeChangeListener {

    private final SectorAPI sector;

    /**
     * @param sector the sector this listener is installed on, whose overlay a resize here repaints
     */
    public PoliticalMapColonySizeListener(SectorAPI sector) {
        this.sector = sector;
    }

    @Override
    public void reportColonySizeChanged(MarketAPI market, int prevSize) {

        // The previous size rides along in the log so a colony that does (or does
        // not) repaint on growth can be traced to this resize.
        MarketPoliticsRefresh.reportMarketChange(
            sector,
            market,
            "colony resize",
            "prevSize=" + prevSize);
    }
}
