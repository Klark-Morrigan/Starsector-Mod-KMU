package kmu.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;

import kmu.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.politicalmap.refresh.PoliticalMapAccessWatcher;

/**
 * Marks a system's political-map ownership stale when one of its colonies grows
 * or shrinks, so a size change that hands the system to another faction repaints
 * without waiting on a reload.
 *
 * <p>Ownership on the map is decided by combined colony size, so a resize is the
 * one economy change that can flip which faction dominates a system. The engine
 * fires no ownership event, but it does fire {@link ColonySizeChangeListener} on
 * every resize - so this translates that into a targeted, politics-only refresh
 * of just the changed system, leaving the whole-economy rescan for the coarser
 * content refresh. Whether the resize actually flips the owner is decided later,
 * when the plugin re-derives that one system; a resize that does not change the
 * winner costs only that re-derivation, not a redraw.
 *
 * <p>Only the changed market's own system is marked: a colony's size affects
 * dominance in its own system alone. Reachability changes (a colony appearing or
 * vanishing from the map) are a separate axis left to
 * {@link PoliticalMapAccessWatcher}.
 */
public class PoliticalMapColonySizeListener implements ColonySizeChangeListener {

    @Override
    public void reportColonySizeChanged(MarketAPI market, int prevSize) {
        // The previous size rides along in the log so a colony that does (or does
        // not) repaint on growth can be traced to this resize.
        MarketPoliticsRefresh.markSystemStaleForMarket(market, "colony resize",
                "prevSize=" + prevSize);
    }
}
