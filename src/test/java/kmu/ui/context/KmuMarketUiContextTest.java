package kmu.ui.context;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuMarketUiContextTest {
    @Test
    void createsContextWithoutPanel() {
        var market = market();

        var context = KmuMarketUiContext.withoutPanel(
                market,
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);

        assertThat(context.getMarket()).isSameAs(market);
        assertThat(context.getPanel()).isEmpty();
        assertThat(context.getSource()).isEqualTo(KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
    }

    @Test
    void createsContextWithPanel() {
        var market = market();
        var panel = panel();

        var context = KmuMarketUiContext.withPanel(
                market,
                panel,
                KmuMarketUiContextSource.REFLECTED_CORE_PANEL);

        assertThat(context.getMarket()).isSameAs(market);
        assertThat(context.getPanel()).contains(panel);
        assertThat(context.getSource()).isEqualTo(KmuMarketUiContextSource.REFLECTED_CORE_PANEL);
    }

    @Test
    void rejectsMissingRequiredValues() {
        var market = market();
        var panel = panel();

        assertThatThrownBy(() -> KmuMarketUiContext.withoutPanel(
                null,
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("market");
        assertThatThrownBy(() -> KmuMarketUiContext.withoutPanel(market, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("source");
        assertThatThrownBy(() -> KmuMarketUiContext.withPanel(
                market,
                null,
                KmuMarketUiContextSource.REFLECTED_CORE_PANEL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("panel");
        assertThatThrownBy(() -> KmuMarketUiContext.withPanel(market, panel, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("source");
    }

    private static MarketAPI market() {
        return proxy(MarketAPI.class, KmuMarketUiContextTest::handleObjectMethodOrThrow);
    }

    private static UIPanelAPI panel() {
        return proxy(UIPanelAPI.class, KmuMarketUiContextTest::handleObjectMethodOrThrow);
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
