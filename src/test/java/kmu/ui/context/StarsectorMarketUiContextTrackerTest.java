package kmu.ui.context;

import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorMarketUiContextTrackerTest {
    @Test
    void tracksOutpostsTabMarketParam() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();

        tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, market);

        assertThat(tracker.getTrackedMarket()).containsSame(market);
    }

    @Test
    void tracksOutpostsTabEntityMarketParam() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();

        tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, entity(market));

        assertThat(tracker.getTrackedMarket()).containsSame(market);
    }

    @Test
    void ignoresNonOutpostsTabParams() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();

        tracker.reportAboutToOpenCoreTab(CoreUITabId.INTEL, market);

        assertThat(tracker.getTrackedMarket()).isEmpty();
    }

    @Test
    void clearsTrackedMarketWhenCoreUiOpensWithoutMarketContext() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();
        tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, market);

        tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, null);

        assertThat(tracker.getTrackedMarket()).isEmpty();
    }

    @Test
    void clearsTrackedMarketWhenNonOutpostsCoreTabOpens() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();
        tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, market);

        tracker.reportAboutToOpenCoreTab(CoreUITabId.INTEL, null);

        assertThat(tracker.getTrackedMarket()).isEmpty();
    }

    @Test
    void tracksAndClearsPlayerMarketEvents() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();

        tracker.reportPlayerOpenedMarket(market);
        assertThat(tracker.getTrackedMarket()).containsSame(market);

        tracker.reportPlayerClosedMarket(market);
        assertThat(tracker.getTrackedMarket()).isEmpty();
    }

    @Test
    void tracksMarketAndCargoUpdatedEvent() {
        MarketAPI market = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();

        tracker.reportPlayerOpenedMarketAndCargoUpdated(market);

        assertThat(tracker.getTrackedMarket()).containsSame(market);
    }

    @Test
    void doesNotClearDifferentTrackedMarket() {
        MarketAPI tracked = market();
        MarketAPI closed = market();
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();
        tracker.reportPlayerOpenedMarket(tracked);

        tracker.reportPlayerClosedMarket(closed);

        assertThat(tracker.getTrackedMarket()).containsSame(tracked);
    }

    @Test
    void clearsClosedMarketWithSameId() {
        MarketAPI tracked = market("same_market");
        MarketAPI closed = market("same_market");
        StarsectorMarketUiContextTracker tracker = new StarsectorMarketUiContextTracker();
        tracker.reportPlayerOpenedMarket(tracked);

        tracker.reportPlayerClosedMarket(closed);

        assertThat(tracker.getTrackedMarket()).isEmpty();
    }

    private static SectorEntityToken entity(MarketAPI market) {
        return proxy(SectorEntityToken.class, (proxy, method, args) -> {
            if (method.getName().equals("getMarket")) {
                return market;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketAPI market() {
        return proxy(MarketAPI.class, StarsectorMarketUiContextTrackerTest::handleObjectMethodOrThrow);
    }

    private static MarketAPI market(String id) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getId")) {
                return id;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static Object handleObjectMethodOrThrow(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass().equals(Object.class)) {
            switch (method.getName()) {
                case "toString":
                    return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException(method.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }
}
