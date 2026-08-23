package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;

import kmu.maplayers.politicalmap.base.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;

/**
 * Marks a system's political-map holding stale when one of its colonies grows
 * or shrinks, so a size change that hands the system to another faction repaints
 * without waiting on a reload.
 *
 * <p>Holder on the map is decided by combined colony size, so a resize is the
 * one economy change that can flip which faction dominates a system. The engine
 * fires no holding event, but it does fire {@link ColonySizeChangeListener} on
 * every resize - so this translates that into a targeted, politics-only refresh
 * of just the changed system, leaving the whole-economy rescan for the coarser
 * content refresh. Whether the resize actually flips the holder is decided later,
 * when the plugin re-derives that one system; a resize that does not change the
 * winner costs only that re-derivation, not a redraw.
 *
 * <p>Only the changed market's own system is marked: a colony's size affects
 * dominance in its own system alone. Reachability changes (a colony appearing or
 * vanishing from the map) are a separate axis left to
 * {@link PoliticalMapStalenessSource}.
 */
public class PoliticalMapColonySizeListener implements ColonySizeChangeListener {

    @Override
    public void reportColonySizeChanged(MarketAPI market, int prevSize) {

        // The previous size rides along in the log so a colony that does (or does
        // not) repaint on growth can be traced to this resize.
        MarketPoliticsRefresh.reportMarketChange(
            market,
            "colony resize", // Event.
            "prevSize=" + prevSize); // Context.
    }
}
