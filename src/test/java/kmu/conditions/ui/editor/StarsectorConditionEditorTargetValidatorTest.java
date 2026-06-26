package kmu.conditions.ui.editor;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextSource;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorConditionEditorTargetValidatorTest {
    private final StarsectorConditionEditorTargetValidator validator =
            new StarsectorConditionEditorTargetValidator();

    @Test
    void allowsCurrentlyOpenMarketRegardlessOfPlanet() {
        // CURRENTLY_OPEN_MARKET bypasses all planet checks — the market screen
        // is already open so no further validation is needed.
        var context = context(market(null, false),
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);

        assertThat(validator.getUnsupportedReason(context)).isEmpty();
    }

    @Test
    void allowsMarketWithPlanetEntity() {
        var context = context(market(planetProxy(), false),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);

        assertThat(validator.getUnsupportedReason(context)).isEmpty();
    }

    @Test
    void allowsMarketThatIsPlanetConditionMarketOnly() {
        var context = context(market(null, true),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);

        assertThat(validator.getUnsupportedReason(context)).isEmpty();
    }

    @Test
    void rejectsMarketWithNeitherPlanetNorConditionOnlyFlag() {
        var context = context(market(null, false),
                KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);

        assertThat(validator.getUnsupportedReason(context)).isPresent();
    }

    private static KmuMarketUiContext context(MarketAPI market, KmuMarketUiContextSource source) {
        return KmuMarketUiContext.withoutPanel(market, source);
    }

    private static MarketAPI market(PlanetAPI planet, boolean planetConditionMarketOnly) {
        return proxy(MarketAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getPlanetEntity": return planet;
                case "isPlanetConditionMarketOnly": return planetConditionMarketOnly;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static PlanetAPI planetProxy() {
        return proxy(PlanetAPI.class,
                (p, method, args) -> handleObjectMethodOrThrow(p, method, args));
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
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
