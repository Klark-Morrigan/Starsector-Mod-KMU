package kmu.conditions.ui.picker;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

import kmu.conditions.domain.KmuConditionRepository;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuConditionSpec;
import kmu.conditions.ui.picker.dialog.KmuConditionPickerDialogDelegate;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextSource;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerEditorTest {
    @Test
    void opensDialogBuiltFromResolvedMarketContext() {
        var service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("cold", "Cold", true)));
        var openedDialog = new AtomicReference<KmuConditionPickerDialogDelegate>();
        var editor = new KmuConditionPickerEditor(
                service,
                new KmuConditionPickerModelFactory(service),
                openedDialog::set);
        var market = marketWithConditions("hot");
        var context = KmuMarketUiContext.withoutPanel(
                market,
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);

        editor.open(context);

        assertThat(openedDialog.get()).isNotNull();
        assertThat(openedDialog.get().getModel().getEntries())
                .extracting(KmuConditionPickerEntry::getConditionId)
                .containsExactly("hot", "cold");
        assertThat(openedDialog.get().getModel().getEntries())
                .extracting(KmuConditionPickerEntry::getState)
                .containsExactly(KmuConditionPickerEntryState.PRESENT, KmuConditionPickerEntryState.ABSENT);
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static MarketAPI marketWithConditions(String... conditionIds) {
        var conditions = new ArrayList<MarketConditionAPI>();
        for (var conditionId : conditionIds) {
            conditions.add(condition(conditionId));
        }

        return proxy(MarketAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getConditions")) {
                return conditions;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketConditionAPI condition(String conditionId) {
        return proxy(MarketConditionAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getId")) {
                return conditionId;
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
        // Default to Mockito-style defaults (null for objects, 0/false for
        // primitives) for unstubbed Starsector API methods so the now-bare
        // production reads do not crash. Tests that need a specific method
        // to throw still stub it explicitly in their switch case.
        Class<?> r = method.getReturnType();
        if (!r.isPrimitive()) return null;
        if (r == boolean.class) return false;
        if (r == void.class) return null;
        if (r == long.class) return 0L;
        if (r == float.class) return 0f;
        if (r == double.class) return 0.0;
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }

    private static final class FakeConditionRepository implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private FakeConditionRepository(KmuConditionSpec... specs) {
            for (var spec : specs) {
                specsById.put(spec.getId(), spec);
            }
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            return new ArrayList<>(specsById.values());
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            return Optional.ofNullable(specsById.get(conditionId));
        }
    }
}
