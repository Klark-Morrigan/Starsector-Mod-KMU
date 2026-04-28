package kmu.ui;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionEditorEntryPointTest {
    @Test
    void opensResolvedMarketContext() {
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                market(),
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
        AtomicReference<KmuMarketUiContext> opened = new AtomicReference<>();
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                opened::set);

        assertThat(entryPoint.openForCurrentMarket()).isTrue();
        assertThat(opened).hasValue(context);
    }

    @Test
    void returnsFalseWhenNoMarketContextExists() {
        AtomicReference<KmuMarketUiContext> opened = new AtomicReference<>();
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                Optional::empty,
                opened::set);

        assertThat(entryPoint.openForCurrentMarket()).isFalse();
        assertThat(opened).hasValue(null);
    }

    @Test
    void returnsFalseAndReportsWhenResolverFails() {
        RuntimeException exception = new IllegalStateException("resolver failed");
        List<String> reports = new ArrayList<>();
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> {
                    throw exception;
                },
                context -> {
                    throw new AssertionError("editor should not open");
                },
                (message, cause) -> reports.add(message + " / " + cause.getMessage()));

        assertThat(entryPoint.openForCurrentMarket()).isFalse();
        assertThat(reports).containsExactly("Failed to resolve current market context. / resolver failed");
    }

    @Test
    void returnsFalseAndReportsWhenEditorFails() {
        RuntimeException exception = new IllegalStateException("editor failed");
        List<String> reports = new ArrayList<>();
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                market(),
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                ignored -> {
                    throw exception;
                },
                (message, cause) -> reports.add(message + " / " + cause.getMessage()));

        assertThat(entryPoint.openForCurrentMarket()).isFalse();
        assertThat(reports).containsExactly("Failed to open planetary condition editor. / editor failed");
    }

    private static MarketAPI market() {
        return proxy(MarketAPI.class, KmuConditionEditorEntryPointTest::handleObjectMethodOrThrow);
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
