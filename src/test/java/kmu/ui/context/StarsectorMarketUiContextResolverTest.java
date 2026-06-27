package kmu.ui.context;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorMarketUiContextResolverTest {

    @Nested
    class FindCurrentMarketContext {

        @Test
        void resolvesCurrentlyOpenMarketFirst() {
            var currentMarket = market();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(currentMarket, throwingCampaignUi(), throwingPlayerFleet()));

            var context = resolver.findCurrentMarketContext();

            assertThat(context).hasValueSatisfying(value -> {
                assertThat(value.getMarket()).isSameAs(currentMarket);
                assertThat(value.getPanel()).isEmpty();
                assertThat(value.getSource()).isEqualTo(KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
            });
        }

        @Test
        void resolvesInteractionDialogTargetMarket() {
            var dialogMarket = market();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(dialog(entity(dialogMarket))),
                            throwingPlayerFleet()));

            var context = resolver.findCurrentMarketContext();

            assertThat(context).hasValueSatisfying(value -> {
                assertThat(value.getMarket()).isSameAs(dialogMarket);
                assertThat(value.getPanel()).isEmpty();
                assertThat(value.getSource()).isEqualTo(KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
            });
        }

        @Test
        void resolvesPlayerFleetInteractionTargetMarket() {
            var targetMarket = market();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(null),
                            playerFleet(entity(targetMarket))));

            var context = resolver.findCurrentMarketContext();

            assertThat(context).hasValueSatisfying(value -> {
                assertThat(value.getMarket()).isSameAs(targetMarket);
                assertThat(value.getPanel()).isEmpty();
                assertThat(value.getSource()).isEqualTo(KmuMarketUiContextSource.PLAYER_FLEET_INTERACTION_TARGET);
            });
        }

        @Test
        void resolvesTrackedCoreUiMarketAfterDirectContextSources() {
            var trackedMarket = market();
            var tracker = new StarsectorMarketUiContextTracker();
            tracker.reportAboutToOpenCoreTab(CoreUITabId.OUTPOSTS, trackedMarket);
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(null),
                            playerFleet(entity(null)),
                            listenerManager(tracker)));

            var context = resolver.findCurrentMarketContext();

            assertThat(context).hasValueSatisfying(value -> {
                assertThat(value.getMarket()).isSameAs(trackedMarket);
                assertThat(value.getPanel()).isEmpty();
                assertThat(value.getSource()).isEqualTo(KmuMarketUiContextSource.TRACKED_CORE_UI_MARKET);
            });
        }

        @Test
        void continuesAfterCurrentlyOpenMarketFailure() {
            var exception = new IllegalStateException("open market failed");
            var reports = new ArrayList<String>();
            var dialogMarket = market();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            exception,
                            campaignUi(dialog(entity(dialogMarket))),
                            throwingPlayerFleet()),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            var context = resolver.findCurrentMarketContext();

            assertThat(context).hasValueSatisfying(value -> {
                assertThat(value.getMarket()).isSameAs(dialogMarket);
                assertThat(value.getSource()).isEqualTo(KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
            });
            assertThat(reports).containsExactly("Failed to resolve currently open market. / open market failed");
        }

        @Test
        void continuesAfterInteractionDialogFailure() {
            var exception = new IllegalStateException("dialog failed");
            var reports = new ArrayList<String>();
            var targetMarket = market();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            throwingCampaignUi(exception),
                            playerFleet(entity(targetMarket))),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            var context = resolver.findCurrentMarketContext();

            assertThat(context).hasValueSatisfying(value -> {
                assertThat(value.getMarket()).isSameAs(targetMarket);
                assertThat(value.getSource()).isEqualTo(KmuMarketUiContextSource.PLAYER_FLEET_INTERACTION_TARGET);
            });
            assertThat(reports).containsExactly("Failed to resolve interaction dialog market. / dialog failed");
        }

        @Test
        void returnsEmptyAndReportsWhenPlayerFleetLookupFails() {
            var exception = new IllegalStateException("player fleet failed");
            var reports = new ArrayList<String>();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(null),
                            throwingPlayerFleet(exception)),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            assertThat(resolver.findCurrentMarketContext()).isEmpty();
            assertThat(reports)
                    .containsExactly("Failed to resolve player fleet interaction target market. / player fleet failed");
        }

        @Test
        void returnsEmptyAndReportsWhenTrackedCoreUiLookupFails() {
            var exception = new IllegalStateException("listener manager failed");
            var reports = new ArrayList<String>();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(null),
                            playerFleet(entity(null)),
                            throwingListenerManager(exception)),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            assertThat(resolver.findCurrentMarketContext()).isEmpty();
            assertThat(reports)
                    .containsExactly("Failed to resolve tracked core UI market. / listener manager failed");
        }

        @Test
        void returnsEmptyAndReportsWhenDialogEntityMarketLookupFails() {
            var exception = new IllegalStateException("dialog entity market failed");
            var reports = new ArrayList<String>();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(dialog(throwingEntity(exception))),
                            playerFleet(entity(null))),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            assertThat(resolver.findCurrentMarketContext()).isEmpty();
            assertThat(reports)
                    .containsExactly("Failed to resolve interaction dialog market. / dialog entity market failed");
        }

        @Test
        void returnsEmptyAndReportsWhenPlayerFleetEntityMarketLookupFails() {
            var exception = new IllegalStateException("player target market failed");
            var reports = new ArrayList<String>();
            var resolver = new StarsectorMarketUiContextResolver(
                    sector(
                            (MarketAPI) null,
                            campaignUi(null),
                            playerFleet(throwingEntity(exception))),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            assertThat(resolver.findCurrentMarketContext()).isEmpty();
            assertThat(reports)
                    .containsExactly(
                            "Failed to resolve player fleet interaction target market. / player target market failed");
        }

        @Test
        void returnsEmptyWhenNoMarketContextExists() {
            var resolver = new StarsectorMarketUiContextResolver(
                    sector((MarketAPI) null, campaignUi(dialog(entity(null))), playerFleet(entity(null))));

            assertThat(resolver.findCurrentMarketContext()).isEmpty();
        }
    }

    private static SectorAPI sector(
            MarketAPI currentlyOpenMarket,
            CampaignUIAPI campaignUI,
            CampaignFleetAPI playerFleet) {
        return sector((Object) currentlyOpenMarket, campaignUI, playerFleet);
    }

    private static SectorAPI sector(
            RuntimeException currentlyOpenMarketException,
            CampaignUIAPI campaignUI,
            CampaignFleetAPI playerFleet) {
        return sector((Object) currentlyOpenMarketException, campaignUI, playerFleet);
    }

    private static SectorAPI sector(
            Object currentlyOpenMarketOrException,
            CampaignUIAPI campaignUI,
            CampaignFleetAPI playerFleet) {
        return sector(currentlyOpenMarketOrException, campaignUI, playerFleet, listenerManager());
    }

    private static SectorAPI sector(
            Object currentlyOpenMarketOrException,
            CampaignUIAPI campaignUI,
            CampaignFleetAPI playerFleet,
            ListenerManagerAPI listenerManager) {
        return proxy(SectorAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getCurrentlyOpenMarket":
                    if (currentlyOpenMarketOrException instanceof RuntimeException) {
                        throw (RuntimeException) currentlyOpenMarketOrException;
                    }
                    return currentlyOpenMarketOrException;
                case "getCampaignUI":
                    return campaignUI;
                case "getPlayerFleet":
                    return playerFleet;
                case "getListenerManager":
                    return listenerManager;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static ListenerManagerAPI listenerManager(StarsectorMarketUiContextTracker... trackers) {
        return proxy(ListenerManagerAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getListeners")
                    && args[0].equals(StarsectorMarketUiContextTracker.class)) {
                return List.of(trackers);
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static ListenerManagerAPI throwingListenerManager(RuntimeException exception) {
        return proxy(ListenerManagerAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getListeners")) {
                throw exception;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static CampaignUIAPI campaignUi(InteractionDialogAPI dialog) {
        return proxy(CampaignUIAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getCurrentInteractionDialog")) {
                return dialog;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static CampaignUIAPI throwingCampaignUi() {
        return throwingCampaignUi(new IllegalStateException("campaign UI should not be queried"));
    }

    private static CampaignUIAPI throwingCampaignUi(RuntimeException exception) {
        return proxy(CampaignUIAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getCurrentInteractionDialog")) {
                throw exception;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static CampaignFleetAPI playerFleet(SectorEntityToken target) {
        return proxy(CampaignFleetAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getInteractionTarget")) {
                return target;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static CampaignFleetAPI throwingPlayerFleet() {
        return throwingPlayerFleet(new IllegalStateException("player fleet should not be queried"));
    }

    private static CampaignFleetAPI throwingPlayerFleet(RuntimeException exception) {
        return proxy(CampaignFleetAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getInteractionTarget")) {
                throw exception;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static InteractionDialogAPI dialog(SectorEntityToken target) {
        return proxy(InteractionDialogAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getInteractionTarget")) {
                return target;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static SectorEntityToken entity(MarketAPI market) {
        return proxy(SectorEntityToken.class, (proxy, method, args) -> {
            if (method.getName().equals("getMarket")) {
                return market;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static SectorEntityToken throwingEntity(RuntimeException exception) {
        return proxy(SectorEntityToken.class, (proxy, method, args) -> {
            if (method.getName().equals("getMarket")) {
                throw exception;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketAPI market() {
        return proxy(MarketAPI.class, StarsectorMarketUiContextResolverTest::handleObjectMethodOrThrow);
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
