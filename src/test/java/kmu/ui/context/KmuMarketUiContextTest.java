package kmu.ui.context;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuMarketUiContextTest {

    @Nested
    class WithoutPanel {

        @Test
        void createsContextWithoutPanel() {
            var market = buildMarket();

            var context = KmuMarketUiContext.withoutPanel(
                    market,
                    KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);

            assertThat(context.getMarket()).isSameAs(market);
            assertThat(context.getPanel()).isEmpty();
            assertThat(context.getSource()).isEqualTo(KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
        }
    }

    @Nested
    class WithPanel {

        @Test
        void createsContextWithPanel() {
            var market = buildMarket();
            var panel = buildPanel();

            var context = KmuMarketUiContext.withPanel(
                    market,
                    panel,
                    KmuMarketUiContextSource.REFLECTED_CORE_PANEL);

            assertThat(context.getMarket()).isSameAs(market);
            assertThat(context.getPanel()).contains(panel);
            assertThat(context.getSource()).isEqualTo(KmuMarketUiContextSource.REFLECTED_CORE_PANEL);
        }
    }

    @Nested
    class RequiredValueValidation {

        @Test
        void rejectsMissingRequiredValues() {
            var market = buildMarket();
            var panel = buildPanel();

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
    }

    private static MarketAPI buildMarket() {
        return proxy(MarketAPI.class, KmuMarketUiContextTest::handleObjectMethodOrThrow);
    }

    private static UIPanelAPI buildPanel() {
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
