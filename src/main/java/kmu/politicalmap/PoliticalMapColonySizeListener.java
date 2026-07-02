package kmu.politicalmap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;

import org.apache.log4j.Logger;

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
    private static final Logger LOG = Global.getLogger(PoliticalMapColonySizeListener.class);

    @Override
    public void reportColonySizeChanged(MarketAPI market, int prevSize) {
        if (market == null) {
            return;
        }
        // A market not seated in a star system (a deep-hyperspace station) seeds
        // no political-map cell, so its resize can change no drawing.
        var system = market.getStarSystem();
        if (system == null) {
            return;
        }
        // Logged with the market and its previous size so a colony that does (or
        // does not) repaint on growth can be traced to this event.
        LOG.debug("Political map politics stale on colony resize; market=" + market.getId()
                + " system=" + system.getId() + " prevSize=" + prevSize);
        PoliticalMapRefresh.markSystemPoliticsStale(system.getId());
    }
}
