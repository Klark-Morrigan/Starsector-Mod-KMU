package kmu.conditions.ui.context;

import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;

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

    private transient MarketAPI trackedMarket;

    public Optional<MarketAPI> getTrackedMarket() {
        return Optional.ofNullable(trackedMarket);
    }

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId id, Object param) {
        var market = resolveMarket(param);
        if (id == CoreUITabId.OUTPOSTS && market.isPresent()) {
            trackMarket(market.get());
            return;
        }

        clearTrackedMarket();
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        trackMarket(market);
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        trackMarket(market);
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        if (market == null || isTrackedMarket(market)) {
            clearTrackedMarket();
        }
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // A trade happens inside a market already tracked, so it moves nothing here.
    }

    private void trackMarket(MarketAPI market) {
        if (market != null) {
            trackedMarket = market;
        }
    }

    private void clearTrackedMarket() {
        trackedMarket = null;
    }

    private boolean isTrackedMarket(MarketAPI market) {
        if (market == trackedMarket) {
            return true;
        }

        if (market == null || trackedMarket == null) {
            return false;
        }

        try {
            var marketId = market.getId();
            return marketId != null && marketId.equals(trackedMarket.getId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Optional<MarketAPI> resolveMarket(Object param) {
        if (param instanceof MarketAPI) {
            return Optional.of((MarketAPI) param);
        }

        if (param instanceof SectorEntityToken) {
            try {
                var market = ((SectorEntityToken) param).getMarket();
                return Optional.ofNullable(market);
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
