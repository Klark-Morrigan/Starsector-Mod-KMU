package kmu.conditions.ui.editor;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextSource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionEditorEntryPointTest {

    @Nested
    class OpenForCurrentMarket {

        @Test
        void opensResolvedMarketContext() {
            var context = KmuMarketUiContext.withoutPanel(
                    buildMarket(),
                    KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
            var opened = new AtomicReference<KmuMarketUiContext>();
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    opened::set);

            assertThat(entryPoint.openForCurrentMarket()).isTrue();
            assertThat(opened).hasValue(context);
        }

        @Test
        void returnsFalseWhenNoMarketContextExists() {
            var opened = new AtomicReference<KmuMarketUiContext>();
            var entryPoint = new KmuConditionEditorEntryPoint(
                    Optional::empty,
                    opened::set);

            assertThat(entryPoint.openForCurrentMarket()).isFalse();
            assertThat(opened).hasValue(null);
        }

        @Test
        void allowsPlanetMarketTarget() {
            var context = KmuMarketUiContext.withoutPanel(
                    buildPlanetMarket(),
                    KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
            var opened = new AtomicReference<KmuMarketUiContext>();
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    opened::set);

            assertThat(entryPoint.openForCurrentMarket()).isTrue();
            assertThat(opened).hasValue(context);
        }

        @Test
        void allowsPlanetConditionOnlyMarketTarget() {
            var context = KmuMarketUiContext.withoutPanel(
                    buildPlanetConditionOnlyMarket(),
                    KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
            var opened = new AtomicReference<KmuMarketUiContext>();
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    opened::set);

            assertThat(entryPoint.openForCurrentMarket()).isTrue();
            assertThat(opened).hasValue(context);
        }

        @Test
        void returnsFalseAndReportsWhenResolverFails() {
            var exception = new IllegalStateException("resolver failed");
            var reports = new ArrayList<String>();
            var entryPoint = new KmuConditionEditorEntryPoint(
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
            var exception = new IllegalStateException("editor failed");
            var reports = new ArrayList<String>();
            var context = KmuMarketUiContext.withoutPanel(
                    buildMarket(),
                    KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    ignored -> {
                        throw exception;
                    },
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            assertThat(entryPoint.openForCurrentMarket()).isFalse();
            assertThat(reports).containsExactly("Failed to open Market Condition Manager. / editor failed");
        }
    }

    @Nested
    class OpenForCurrentMarketDetailed {

        @Test
        void returnsDetailedOpenedResult() {
            var context = KmuMarketUiContext.withoutPanel(
                    buildMarket(),
                    KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    ignored -> {
                    });

            var result = entryPoint.openForCurrentMarketDetailed();

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.OPENED);
            assertThat(result.getMessage()).isEqualTo("Opened Market Condition Manager.");
        }

        @Test
        void rejectsUnsupportedMarketTarget() {
            var context = KmuMarketUiContext.withoutPanel(
                    buildMarketWithoutPlanetSupport(),
                    KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
            var opened = new AtomicReference<KmuMarketUiContext>();
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    opened::set);

            var result = entryPoint.openForCurrentMarketDetailed();

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET);
            assertThat(result.getMessage()).isEqualTo("Current market does not support market condition editing.");
            assertThat(opened).hasValue(null);
        }

        @Test
        void returnsFalseAndReportsWhenTargetValidationFails() {
            var exception = new IllegalStateException("target check failed");
            var reports = new ArrayList<String>();
            var context = KmuMarketUiContext.withoutPanel(
                    buildMarketThrowingOnPlanetLookup(exception),
                    KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
            var entryPoint = new KmuConditionEditorEntryPoint(
                    () -> Optional.of(context),
                    ignored -> {
                        throw new AssertionError("editor should not open");
                    },
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));

            var result = entryPoint.openForCurrentMarketDetailed();

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.FAILED);
            assertThat(result.getMessage()).isEqualTo("Failed to validate MCM target.");
            assertThat(reports).containsExactly("Failed to validate MCM target. / target check failed");
        }
    }

    private static MarketAPI buildMarket() {
        return proxy(MarketAPI.class, KmuConditionEditorEntryPointTest::handleObjectMethodOrThrow);
    }

    private static MarketAPI buildMarketWithoutPlanetSupport() {
        return buildMarketWithPlanetSupport(null, false);
    }

    private static MarketAPI buildPlanetMarket() {
        return buildMarketWithPlanetSupport(proxy(PlanetAPI.class, KmuConditionEditorEntryPointTest::handleObjectMethodOrThrow), false);
    }

    private static MarketAPI buildPlanetConditionOnlyMarket() {
        return buildMarketWithPlanetSupport(null, true);
    }

    private static MarketAPI buildMarketWithPlanetSupport(PlanetAPI planet, boolean planetConditionMarketOnly) {
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

    private static MarketAPI buildMarketThrowingOnPlanetLookup(RuntimeException exception) {
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
