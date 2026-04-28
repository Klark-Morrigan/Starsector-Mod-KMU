package kmu.ui.chooser;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import kmu.conditions.KmuConditionRepository;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
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

class KmuConditionChooserEditorTest {
    @Test
    void opensDialogBuiltFromResolvedMarketContext() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("cold", "Cold", true)));
        AtomicReference<KmuConditionChooserDialogDelegate> openedDialog = new AtomicReference<>();
        KmuConditionChooserEditor editor = new KmuConditionChooserEditor(
                service,
                new KmuConditionChooserModelFactory(service),
                openedDialog::set);
        MarketAPI market = marketWithConditions("hot");
        KmuMarketUiContext context = KmuMarketUiContext.withoutPanel(
                market,
                KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET);

        editor.open(context);

        assertThat(openedDialog.get()).isNotNull();
        assertThat(openedDialog.get().getModel().getEntries())
                .extracting(KmuConditionChooserEntry::getConditionId)
                .containsExactly("hot", "cold");
        assertThat(openedDialog.get().getModel().getEntries())
                .extracting(KmuConditionChooserEntry::getState)
                .containsExactly(KmuConditionChooserEntryState.PRESENT, KmuConditionChooserEntryState.ABSENT);
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static MarketAPI marketWithConditions(String... conditionIds) {
        List<MarketConditionAPI> conditions = new ArrayList<>();
        for (String conditionId : conditionIds) {
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
        throw new UnsupportedOperationException(method.toString());
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
            for (KmuConditionSpec spec : specs) {
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
