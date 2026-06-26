package kmu.conditions.domain;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StarsectorEditableMarketTest {
    @Test
    void extractsConditionIdsInMarketOrderAndSkipsNulls() {
        var market = new StarsectorEditableMarket(market(
                Arrays.asList(condition("hot", new ArrayList<>()), null, condition(null, new ArrayList<>()),
                        condition("ore_sparse", new ArrayList<>())),
                Map.of(),
                new ArrayList<>()));

        var ids = market.getConditionIds();

        assertThat(ids).containsExactly("hot", "ore_sparse");
    }

    @Test
    void exposesWrappedStarsectorMarketForUiMetadata() {
        var starsectorMarket = market(List.of(), Map.of(), new ArrayList<>());
        var market = new StarsectorEditableMarket(starsectorMarket);

        assertThat(market.getMarket()).isSameAs(starsectorMarket);
    }

    @Test
    void returnsEmptyConditionIdsWhenMarketConditionsAreNull() {
        var market = new StarsectorEditableMarket(
                market(null, Map.of(), new ArrayList<>()));

        assertThat(market.getConditionIds()).isEmpty();
    }

    @Test
    void propagatesGetConditionsFailuresForServiceBoundaryToHandle() {
        var exception = new IllegalStateException("get conditions failed");
        var market = new StarsectorEditableMarket(
                throwingMarket("getConditions", exception));

        assertThatThrownBy(market::getConditionIds)
                .isSameAs(exception);
    }

    @Test
    void delegatesHasConditionAndAddCondition() {
        var calls = new ArrayList<String>();
        var conditionsById = new LinkedHashMap<String, MarketConditionAPI>();
        conditionsById.put("hot", condition("hot", calls));
        var market = new StarsectorEditableMarket(
                market(List.of(), conditionsById, calls));

        assertThat(market.hasCondition("hot")).isTrue();
        assertThat(market.hasCondition("cold")).isFalse();

        market.addCondition("cold");

        assertThat(calls).containsExactly("add:cold");
    }

    @Test
    void delegatesConditionSuppressionLookup() {
        var market = new StarsectorEditableMarket(
                market(List.of(), Map.of(), new ArrayList<>(), Set.of("hot")));

        assertThat(market.isConditionSuppressed("hot")).isTrue();
        assertThat(market.isConditionSuppressed("cold")).isFalse();
    }

    @Test
    void propagatesAddConditionFailuresForServiceBoundaryToHandle() {
        var exception = new IllegalStateException("add condition failed");
        var market = new StarsectorEditableMarket(
                throwingMarket("addCondition", exception));

        assertThatThrownBy(() -> market.addCondition("hot"))
                .isSameAs(exception);
    }

    @Test
    void marksExistingConditionSurveyed() {
        var calls = new ArrayList<String>();
        var conditionsById = new LinkedHashMap<String, MarketConditionAPI>();
        conditionsById.put("hot", condition("hot", calls));
        var market = new StarsectorEditableMarket(
                market(List.of(), conditionsById, calls));

        market.markConditionSurveyed("hot");

        assertThat(calls).containsExactly("surveyed:hot:true");
    }

    @Test
    void propagatesConditionLookupFailuresForServiceBoundaryToHandle() {
        var exception = new IllegalStateException("condition lookup failed");
        var market = new StarsectorEditableMarket(
                throwingMarket("getFirstCondition", exception));

        assertThatThrownBy(() -> market.markConditionSurveyed("hot"))
                .isSameAs(exception);
    }

    @Test
    void propagatesSurveyMarkerFailuresForServiceBoundaryToHandle() {
        var exception = new IllegalStateException("survey marker failed");
        var condition = throwingCondition("setSurveyed", exception);
        var market = new StarsectorEditableMarket(
                market(List.of(), Map.of("hot", condition), new ArrayList<>()));

        assertThatThrownBy(() -> market.markConditionSurveyed("hot"))
                .isSameAs(exception);
    }

    @Test
    void ignoresSurveyedMarkerWhenConditionIsMissing() {
        var calls = new ArrayList<String>();
        var market = new StarsectorEditableMarket(
                market(List.of(), Map.of(), calls));

        market.markConditionSurveyed("missing");

        assertThat(calls).isEmpty();
    }

    @Test
    void delegatesReapplyConditions() {
        var calls = new ArrayList<String>();
        var market = new StarsectorEditableMarket(
                market(List.of(), Map.of(), calls));

        market.reapplyConditions();

        assertThat(calls).containsExactly("reapply");
    }

    @Test
    void propagatesReapplyFailuresForServiceBoundaryToHandle() {
        var exception = new IllegalStateException("reapply failed");
        var market = new StarsectorEditableMarket(
                throwingMarket("reapplyConditions", exception));

        assertThatThrownBy(market::reapplyConditions)
                .isSameAs(exception);
    }

    private static MarketAPI market(
            List<MarketConditionAPI> conditions,
            Map<String, MarketConditionAPI> conditionsById,
            List<String> calls) {
        return market(conditions, conditionsById, calls, Set.of());
    }

    private static MarketAPI market(
            List<MarketConditionAPI> conditions,
            Map<String, MarketConditionAPI> conditionsById,
            List<String> calls,
            Set<String> suppressedConditionIds) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getConditions":
                    return conditions;
                case "hasCondition":
                    return conditionsById.containsKey((String) args[0]);
                case "isConditionSuppressed":
                    return suppressedConditionIds.contains((String) args[0]);
                case "addCondition":
                    calls.add("add:" + args[0]);
                    return args[0];
                case "getFirstCondition":
                    return conditionsById.get((String) args[0]);
                case "reapplyConditions":
                    calls.add("reapply");
                    return null;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static MarketAPI throwingMarket(String methodName, RuntimeException exception) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            if (method.getName().equals(methodName)) {
                throw exception;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketConditionAPI condition(String id, List<String> calls) {
        return proxy(MarketConditionAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getId":
                    return id;
                case "setSurveyed":
                    calls.add("surveyed:" + id + ":" + args[0]);
                    return null;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static MarketConditionAPI throwingCondition(String methodName, RuntimeException exception) {
        return proxy(MarketConditionAPI.class, (proxy, method, args) -> {
            if (method.getName().equals(methodName)) {
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
