package kmu.conditions.ui.editor;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextSource;
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
    void returnsDetailedOpenedResult() {
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                market(),
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                ignored -> {
                });

        KmuConditionEditorOpenResult result = entryPoint.openForCurrentMarketDetailed();

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.OPENED);
        assertThat(result.getMessage()).isEqualTo("Opened planetary condition picker.");
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
    void rejectsUnsupportedMarketTarget() {
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                marketWithoutPlanetSupport(),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
        AtomicReference<KmuMarketUiContext> opened = new AtomicReference<>();
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                opened::set);

        KmuConditionEditorOpenResult result = entryPoint.openForCurrentMarketDetailed();

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET);
        assertThat(result.getMessage()).isEqualTo("Current market does not support planetary condition editing.");
        assertThat(opened).hasValue(null);
    }

    @Test
    void allowsPlanetMarketTarget() {
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                planetMarket(),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
        AtomicReference<KmuMarketUiContext> opened = new AtomicReference<>();
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                opened::set);

        assertThat(entryPoint.openForCurrentMarket()).isTrue();
        assertThat(opened).hasValue(context);
    }

    @Test
    void allowsPlanetConditionOnlyMarketTarget() {
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                planetConditionOnlyMarket(),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
        AtomicReference<KmuMarketUiContext> opened = new AtomicReference<>();
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                opened::set);

        assertThat(entryPoint.openForCurrentMarket()).isTrue();
        assertThat(opened).hasValue(context);
    }

    @Test
    void returnsFalseAndReportsWhenTargetValidationFails() {
        RuntimeException exception = new IllegalStateException("target check failed");
        List<String> reports = new ArrayList<>();
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                marketThrowingOnPlanetLookup(exception),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
        KmuConditionEditorEntryPoint entryPoint = new KmuConditionEditorEntryPoint(
                () -> Optional.of(context),
                ignored -> {
                    throw new AssertionError("editor should not open");
                },
                (message, cause) -> reports.add(message + " / " + cause.getMessage()));

        KmuConditionEditorOpenResult result = entryPoint.openForCurrentMarketDetailed();

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.FAILED);
        assertThat(result.getMessage()).isEqualTo("Failed to validate planetary condition picker target.");
        assertThat(reports).containsExactly("Failed to validate planetary condition picker target. / target check failed");
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
        assertThat(reports).containsExactly("Failed to open planetary condition picker. / editor failed");
    }

    private static MarketAPI market() {
        return proxy(MarketAPI.class, KmuConditionEditorEntryPointTest::handleObjectMethodOrThrow);
    }

    private static MarketAPI marketWithoutPlanetSupport() {
        return marketWithPlanetSupport(null, false);
    }

    private static MarketAPI planetMarket() {
        return marketWithPlanetSupport(proxy(PlanetAPI.class, KmuConditionEditorEntryPointTest::handleObjectMethodOrThrow), false);
    }

    private static MarketAPI planetConditionOnlyMarket() {
        return marketWithPlanetSupport(null, true);
    }

    private static MarketAPI marketWithPlanetSupport(PlanetAPI planet, boolean planetConditionMarketOnly) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getPlanetEntity")) {
                return planet;
            }
            if (method.getName().equals("isPlanetConditionMarketOnly")) {
                return planetConditionMarketOnly;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketAPI marketThrowingOnPlanetLookup(RuntimeException exception) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getPlanetEntity")) {
                throw exception;
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
