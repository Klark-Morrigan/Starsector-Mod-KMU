package kmu.ui.context;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;

import java.util.Optional;

public final class StarsectorMarketUiContextTracker
    extends BaseCampaignEventListener
    implements CoreUITabListener {

    private transient MarketAPI trackedMarket;

    public StarsectorMarketUiContextTracker() {
        super(false);
    }

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
