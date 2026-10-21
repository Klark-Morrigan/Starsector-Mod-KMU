package kmu.conditions.ui.context;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;

import org.apache.log4j.Logger;

import java.util.Optional;

/**
 * Remembers which market the player has a UI open on, off the market and core-tab events.
 *
 * <p>Registered with the listener manager, which hands each event only to the listeners of the
 * interface it belongs to - the market events to {@link ColonyInteractionListener}. A campaign event
 * listener's methods of the same names are never called from there, so the tracker implements the
 * interface rather than extending that base.
 */
public final class StarsectorMarketUiContextTracker
        implements ColonyInteractionListener, CoreUITabListener {

    private static final Logger LOG = Global.getLogger(StarsectorMarketUiContextTracker.class);

    // Transient on top of the transient registration, so a market never reaches a save even if the
    // tracker is one day registered another way: what a UI was open on means nothing after a load.
    private transient MarketAPI trackedMarket;

    /**
     * @return the market the player has a UI open on, or empty when none is tracked
     */
    public Optional<MarketAPI> getTrackedMarket() {
        return Optional.ofNullable(trackedMarket);
    }

    /**
     * Tracks the market the colony tab opens on, and clears the tracked market for any other tab -
     * the player has moved off whatever the last one was open on.
     *
     * @param id    the core tab about to open
     * @param param what the tab opens on: a market, an entity holding one, or anything else
     */
    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId id, Object param) {

        var market = resolveMarketOf(param);

        if (id == CoreUITabId.OUTPOSTS && market.isPresent()) {
            trackMarket(market.get());
            return;
        }

        clearTrackedMarket();
    }

    /**
     * Clears the tracked market when the market closed is the one tracked, matched by identity or ID.
     *
     * @param market the market closed; null clears whatever is tracked
     */
    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {

        if (market == null || isTrackedMarket(market)) {
            clearTrackedMarket();
        }
    }

    /**
     * Moves nothing: a trade happens inside a market already tracked.
     *
     * @param transaction the trade made
     */
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
    }

    /**
     * Tracks the market opened.
     *
     * @param market the market opened; null leaves the tracked market alone
     */
    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        trackMarket(market);
    }

    /**
     * Tracks the market opened, as {@link #reportPlayerOpenedMarket} does.
     *
     * @param market the market opened; null leaves the tracked market alone
     */
    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        trackMarket(market);
    }

    private void clearTrackedMarket() {
        trackedMarket = null;
    }

    // Matched by ID as well as identity, since the close can arrive with a different instance of the
    // market the open reported.
    private boolean isTrackedMarket(MarketAPI market) {

        if (market == trackedMarket) {
            return true;
        }

        if (trackedMarket == null) {
            return false;
        }

        // A market the game cannot name answers as untracked rather than failing the close, which
        // would leave the editor open on a market the player has left.
        try {
            var marketId = market.getId();

            return marketId != null
                && marketId.equals(trackedMarket.getId());

        } catch (RuntimeException runtimeException) {

            LOG.warn("Failed to compare a closed market with the tracked one", runtimeException);
            return false;
        }
    }

    // The market a core tab opens on, wherever the tab's argument carries one.
    private Optional<MarketAPI> resolveMarketOf(Object param) {

        if (param instanceof MarketAPI market) {
            return Optional.of(market);
        }

        if (!(param instanceof SectorEntityToken entity)) {
            return Optional.empty();
        }

        // An entity that cannot answer for its market answers as holding none, so the tab still
        // opens with nothing tracked.
        try {
            return Optional.ofNullable(entity.getMarket());

        } catch (RuntimeException runtimeException) {

            LOG.warn("Failed to read the market of a core tab's entity", runtimeException);
            return Optional.empty();
        }
    }

    private void trackMarket(MarketAPI market) {

        if (market != null) {
            trackedMarket = market;
        }
    }
}
