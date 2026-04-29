package kmu.ui.chooser.model;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import kmu.conditions.KmuConditionRepository;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
import kmu.conditions.KmuEditableMarket;
import kmu.conditions.StarsectorEditableMarket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuConditionChooserModelFactoryTest {
    @Test
    void buildsEntriesForPlanetarySpecsInServiceOrder() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("population_3", "Population 3", false),
                spec("farmland_rich", "Farmland: Rich", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserModel model = factory.create(new FakeEditableMarket("hot"));

        assertThat(model.getEntries())
                .extracting(KmuConditionChooserEntry::getConditionId)
                .containsExactly("hot", "farmland_rich");
        assertThat(model.getEntries())
                .extracting(KmuConditionChooserEntry::getState)
                .containsExactly(KmuConditionChooserEntryState.PRESENT, KmuConditionChooserEntryState.ABSENT);
        assertThat(model.getEntryCount()).isEqualTo(2);
        assertThat(model.getPresentCount()).isEqualTo(1);
        assertThat(model.getAbsentCount()).isEqualTo(1);
    }

    @Test
    void includesSpecDataAndSpecDescriptionTooltipText() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                new KmuConditionSpec("cold", "Cold", null, "Cold condition description.", "Vanilla", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserEntry entry = factory.create(new FakeEditableMarket())
                .getEntries()
                .get(0);

        assertThat(entry.getConditionId()).isEqualTo("cold");
        assertThat(entry.getName()).isEqualTo("Cold");
        assertThat(entry.getIcon()).isEmpty();
        assertThat(entry.descriptionLine()).isEqualTo("cold - Absent");
        assertThat(entry.getTooltipText()).isEqualTo("Cold condition description.");
        assertThat(entry.getSourceModName()).contains("Vanilla");
    }

    @Test
    void marksPresentSuppressedEntries() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("no_atmosphere", "No Atmosphere", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserEntry entry = factory.create(new FakeEditableMarket(
                Set.of("no_atmosphere"),
                Set.of("no_atmosphere")))
                .getEntries()
                .get(0);

        assertThat(entry.isPresent()).isTrue();
        assertThat(entry.isSuppressed()).isTrue();
        assertThat(entry.isHidden()).isFalse();
    }

    @Test
    void marksPresentHiddenEntriesFromLiveConditionPlugin() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("no_atmosphere", "No Atmosphere", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);
        MarketConditionAPI condition = condition("no_atmosphere", plugin(false, "graphics/icons/live.png"));

        KmuConditionChooserEntry entry = factory.create(new StarsectorEditableMarket(
                market(List.of(condition), Map.of("no_atmosphere", condition), Set.of())))
                .getEntries()
                .get(0);

        assertThat(entry.isPresent()).isTrue();
        assertThat(entry.isSuppressed()).isFalse();
        assertThat(entry.isHidden()).isTrue();
        assertThat(entry.getIcon()).contains("graphics/icons/live.png");
        assertThat(entry.getTooltipRenderer()).isPresent();
    }

    @Test
    void returnsImmutableEntryList() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserModel model = factory.create(new FakeEditableMarket());

        assertThatThrownBy(() -> model.getEntries().add(new KmuConditionChooserEntry(
                "cold",
                "Cold",
                null,
                KmuConditionChooserEntryState.ABSENT,
                "tooltip")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
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

    private static final class FakeEditableMarket implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> suppressedConditionIds = new LinkedHashSet<>();

        private FakeEditableMarket(String... conditionIds) {
            for (String conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        private FakeEditableMarket(Set<String> conditionIds, Set<String> suppressedConditionIds) {
            this.conditionIds.addAll(conditionIds);
            this.suppressedConditionIds.addAll(suppressedConditionIds);
        }

        @Override
        public Set<String> getConditionIds() {
            return new LinkedHashSet<>(conditionIds);
        }

        @Override
        public boolean hasCondition(String conditionId) {
            return conditionIds.contains(conditionId);
        }

        @Override
        public boolean isConditionSuppressed(String conditionId) {
            return suppressedConditionIds.contains(conditionId);
        }

        @Override
        public void addCondition(String conditionId) {
            conditionIds.add(conditionId);
        }

        @Override
        public void markConditionSurveyed(String conditionId) {
        }

        @Override
        public void reapplyConditions() {
        }
    }

    private static MarketAPI market(
            List<MarketConditionAPI> conditions,
            Map<String, MarketConditionAPI> conditionsById,
            Set<String> suppressedConditionIds) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getConditions":
                    return conditions;
                case "getFirstCondition":
                    return conditionsById.get((String) args[0]);
                case "isConditionSuppressed":
                    return suppressedConditionIds.contains((String) args[0]);
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static MarketConditionAPI condition(String id, MarketConditionPlugin plugin) {
        return proxy(MarketConditionAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getId":
                    return id;
                case "getPlugin":
                    return plugin;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static MarketConditionPlugin plugin(boolean showIcon, String iconName) {
        return proxy(MarketConditionPlugin.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "showIcon":
                    return showIcon;
                case "getIconName":
                    return iconName;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
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
