package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.apache.log4j.Logger;

/**
 * The one translation from a market-scoped economy event to a targeted
 * political-map politics refresh, so every listener that reacts to such an event
 * (a colony resize, a market discovery, a decivilisation, and any added later)
 * applies the same seat guard and emits the same log line instead of copying
 * them.
 *
 * <p>Holder on the map is decided per system from combined colony size, so an
 * event that changes a colony's holder or size can only shift dominance in that
 * colony's own system. This resolves the market's seated system, ignores a
 * market not seated in one (a deep-hyperspace station seeds no cell, so its
 * change can repaint nothing), and marks just that system stale via
 * {@link MapLayerRefresh#markSystemGroupingStale} - the fine-grained refresh
 * that re-derives only the named system and its neighbours rather than rescanning
 * the whole economy. Reachability changes (a system joining or leaving the map)
 * are a separate axis owned by {@link PoliticalMapStalenessSource}.
 */
public final class MarketPoliticsRefresh {
    private static final Logger LOG = Global.getLogger(MarketPoliticsRefresh.class);

    private MarketPoliticsRefresh() {
    }

    /**
     * Marks the market's seated star system politics-stale, ignoring a null or
     * unseated market, and logs the triggering event so a cell that does (or does
     * not) repaint can be traced back to it.
     *
     * @param market  the market whose holder or size changed; null is ignored
     * @param event   short phrase naming what happened, e.g. "colony resize", used
     *                verbatim in the log line
     * @param context extra {@code key=value} detail for the log line, e.g.
     *                "prevSize=3"; empty appends nothing
     */
    public static void markSystemStaleForMarket(MarketAPI market, String event, String context) {
        if (market == null) {
            return;
        }
        // A market not seated in a star system (a deep-hyperspace station) seeds
        // no political-map cell, so its change can repaint nothing.
        var system = market.getStarSystem();
        if (system == null) {
            return;
        }
        LOG.debug("Political map politics stale on " + event + "; market=" + market.getId()
                + " system=" + system.getId() + (context.isEmpty() ? "" : " " + context));
        MapLayerRefresh.markSystemGroupingStale(system.getId());
    }
}
