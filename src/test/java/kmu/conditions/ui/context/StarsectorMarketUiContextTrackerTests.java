package kmu.conditions.ui.context;

import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorMarketUiContextTrackerTests {

    @Nested
    class ReportAboutToOpenCoreTab {

        @Test
        void tracksOutpostsTabMarketParam() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, market);

            assertThat(tracker.getTrackedMarket())
                .containsSame(market);
        }

        @Test
        void tracksOutpostsTabEntityMarketParam() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, buildEntity(market));

            assertThat(tracker.getTrackedMarket())
                .containsSame(market);
        }

        @Test
        void ignoresNonOutpostsTabParams() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportAboutToOpenCoreTab(CoreUITabId.INTEL, market);

            assertThat(tracker.getTrackedMarket())
                .isEmpty();
        }

        @Test
        void clearsTrackedMarketWhenCoreUiOpensWithoutMarketContext() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, market);
            tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, null);

            assertThat(tracker.getTrackedMarket())
                .isEmpty();
        }

        @Test
        void clearsTrackedMarketWhenNonOutpostsCoreTabOpens() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, market);
            tracker.reportAboutToOpenCoreTab(CoreUITabId.INTEL, null);

            assertThat(tracker.getTrackedMarket())
                .isEmpty();
        }
    }

    @Nested
    class ReportPlayerOpenedMarketAndCargoUpdated {

        @Test
        void tracksMarketAndCargoUpdatedEvent() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportPlayerOpenedMarketAndCargoUpdated(market);

            assertThat(tracker.getTrackedMarket())
                .containsSame(market);
        }
    }

    @Nested
    class ReportPlayerClosedMarket {

        @Test
        void tracksAndClearsPlayerMarketEvents() {

            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportPlayerOpenedMarket(market);

            assertThat(tracker.getTrackedMarket())
                .containsSame(market);

            tracker.reportPlayerClosedMarket(market);

            assertThat(tracker.getTrackedMarket())
                .isEmpty();
        }

        @Test
        void doesNotClearDifferentTrackedMarket() {

            var tracked = buildMarket();
            var closed = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportPlayerOpenedMarket(tracked);
            tracker.reportPlayerClosedMarket(closed);

            assertThat(tracker.getTrackedMarket())
                .containsSame(tracked);
        }

        @Test
        void clearsClosedMarketWithSameId() {

            var tracked = buildMarket("same_market");
            var closed = buildMarket("same_market");
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportPlayerOpenedMarket(tracked);
            tracker.reportPlayerClosedMarket(closed);

            assertThat(tracker.getTrackedMarket())
                .isEmpty();
        }

        @Test
        void clearsOnACloseDeliveredAsTheListenerManagerDeliversIt() {
            // The listener manager hands a market event only to the listeners of the interface it
            // belongs to. A tracker answering the same method names off another type never hears the
            // close, and the editor goes on opening on a market the player has left.
            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            tracker.reportPlayerOpenedMarket(market);
            deliverAsTheListenerManagerDoes(tracker, listener -> listener.reportPlayerClosedMarket(market));

            assertThat(tracker.getTrackedMarket())
                .isEmpty();
        }
    }

    @Nested
    class ReportPlayerOpenedMarket {

        @Test
        void tracksAnOpenDeliveredAsTheListenerManagerDeliversIt() {
            // The open's half of the same delivery: a tracker the open never reaches has nothing to
            // hand the editor.
            var market = buildMarket();
            var tracker = new StarsectorMarketUiContextTracker();

            deliverAsTheListenerManagerDoes(tracker, listener -> listener.reportPlayerOpenedMarket(market));

            assertThat(tracker.getTrackedMarket())
                .containsSame(market);
        }
    }

    // Hands an event to the tracker the way the listener manager does: to each registered listener
    // that is a market-event listener, and to nothing else.
    private static void deliverAsTheListenerManagerDoes(
            Object registeredListener,
            Consumer<ColonyInteractionListener> event) {

        Stream
            .of(registeredListener)
            .filter(ColonyInteractionListener.class::isInstance)
            .map(ColonyInteractionListener.class::cast)
            .forEach(event);
    }

    private static SectorEntityToken buildEntity(MarketAPI market) {

        return proxy(SectorEntityToken.class, (proxy, method, args) -> {

            if (method.getName().equals("getMarket")) {
                return market;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketAPI buildMarket() {
        return proxy(MarketAPI.class, StarsectorMarketUiContextTrackerTests::handleObjectMethodOrThrow);
    }

    private static MarketAPI buildMarket(String id) {

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
