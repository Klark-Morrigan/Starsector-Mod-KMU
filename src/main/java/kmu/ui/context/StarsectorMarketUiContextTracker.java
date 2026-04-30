package kmu.ui.context;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;

import java.util.Optional;

public final class StarsectorMarketUiContextTracker extends BaseCampaignEventListener implements CoreUITabListener {
    private static final boolean VERBOSE_LOGGING = true;

    private transient MarketAPI trackedMarket;

    public StarsectorMarketUiContextTracker() {
        super(false);
    }

    public Optional<MarketAPI> getTrackedMarket() {
        return Optional.ofNullable(trackedMarket);
    }

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId id, Object param) {
        log("core tab opening: tab=" + id
                + ", param=" + describeObject(param)
                + ", before=" + describeMarket(trackedMarket));

        Optional<MarketAPI> market = resolveMarket(param);
        if (id == CoreUITabId.OUTPOSTS && market.isPresent()) {
            trackMarket(market.get());
            log("tracked market from OUTPOSTS core tab: market=" + describeMarket(market.get()));
            return;
        }

        log("clearing tracked market from core tab transition: tab=" + id
                + ", resolvedMarket=" + market.map(this::describeMarket).orElse("none"));
        clearTrackedMarket();
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        log("player opened market event: market=" + describeMarket(market)
                + ", before=" + describeMarket(trackedMarket));
        trackMarket(market);
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        log("player opened market and cargo updated event: market=" + describeMarket(market)
                + ", before=" + describeMarket(trackedMarket));
        trackMarket(market);
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        boolean clear = market == null || isTrackedMarket(market);
        log("player closed market event: market=" + describeMarket(market)
                + ", tracked=" + describeMarket(trackedMarket)
                + ", clear=" + clear);
        if (clear) {
            clearTrackedMarket();
        }
    }

    private void trackMarket(MarketAPI market) {
        if (market != null) {
            trackedMarket = market;
            log("tracked market set: market=" + describeMarket(trackedMarket));
        } else {
            log("ignored null market tracking request");
        }
    }

    private void clearTrackedMarket() {
        if (trackedMarket != null) {
            log("tracked market cleared: previous=" + describeMarket(trackedMarket));
        } else {
            log("tracked market already clear");
        }
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
            String marketId = market.getId();
            return marketId != null && marketId.equals(trackedMarket.getId());
        } catch (RuntimeException exception) {
            log("failed to compare tracked market ids: closed=" + describeMarket(market)
                    + ", tracked=" + describeMarket(trackedMarket)
                    + ", error=" + exception.getMessage());
            return false;
        }
    }

    private Optional<MarketAPI> resolveMarket(Object param) {
        if (param instanceof MarketAPI) {
            return Optional.of((MarketAPI) param);
        }

        if (param instanceof SectorEntityToken) {
            try {
                MarketAPI market = ((SectorEntityToken) param).getMarket();
                if (market == null) {
                    log("core tab entity param had no market: param=" + describeObject(param));
                    return Optional.empty();
                }
                return Optional.of(market);
            } catch (RuntimeException exception) {
                log("failed to resolve market from core tab entity param: param=" + describeObject(param)
                        + ", error=" + exception.getMessage());
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private String describeMarket(MarketAPI market) {
        if (market == null) {
            return "none";
        }

        return "id=" + safeMarketId(market)
                + ", name=" + safeMarketName(market)
                + ", object=" + describeObject(market);
    }

    private String safeMarketId(MarketAPI market) {
        try {
            String id = market.getId();
            return id == null ? "null" : id;
        } catch (RuntimeException exception) {
            return "error:" + exception.getMessage();
        }
    }

    private String safeMarketName(MarketAPI market) {
        try {
            String name = market.getName();
            return name == null ? "null" : name;
        } catch (RuntimeException exception) {
            return "error:" + exception.getMessage();
        }
    }

    private String describeObject(Object object) {
        if (object == null) {
            return "null";
        }

        return object.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(object));
    }

    private void log(String message) {
        if (!VERBOSE_LOGGING) {
            return;
        }

        String formattedMessage = "[KMU] Market UI context tracker: " + message;
        logToCampaignEventFeed(formattedMessage);
        if (!logToStarsectorLogger(formattedMessage)) {
            System.out.println(formattedMessage);
        }
    }

    private void logToCampaignEventFeed(String message) {
        try {
            SectorAPI sector = Global.getSector();
            if (sector == null) {
                return;
            }

            CampaignUIAPI campaignUI = sector.getCampaignUI();
            if (campaignUI == null) {
                return;
            }

            campaignUI.addMessage(message);
        } catch (RuntimeException exception) {
            // Debug output must never break campaign UI listeners.
        }
    }

    private boolean logToStarsectorLogger(String message) {
        try {
            Object logger = Global.class
                    .getMethod("getLogger", Class.class)
                    .invoke(null, StarsectorMarketUiContextTracker.class);
            logger.getClass()
                    .getMethod("info", Object.class)
                    .invoke(logger, message);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }
}
