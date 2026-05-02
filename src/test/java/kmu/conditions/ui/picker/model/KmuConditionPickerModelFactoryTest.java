package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import kmu.conditions.domain.KmuConditionRepository;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuConditionSpec;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuConditionPickerModelFactoryTest {
    private static final Color FACTION_COLOR = new Color(90, 150, 240);
    private static final Color RELATIONSHIP_COLOR = new Color(240, 80, 80);

    @Test
    void buildsEntriesForPlanetarySpecsInServiceOrder() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("population_3", "Population 3", false),
                spec("farmland_rich", "Farmland: Rich", true)));
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);

        KmuConditionPickerModel model = factory.create(new FakeEditableMarket("hot"));

        assertThat(model.getEntries())
                .extracting(KmuConditionPickerEntry::getConditionId)
                .containsExactly("hot", "farmland_rich");
        assertThat(model.getEntries())
                .extracting(KmuConditionPickerEntry::getState)
                .containsExactly(KmuConditionPickerEntryState.PRESENT, KmuConditionPickerEntryState.ABSENT);
        assertThat(model.getEntryCount()).isEqualTo(2);
        assertThat(model.getPresentCount()).isEqualTo(1);
        assertThat(model.getAbsentCount()).isEqualTo(1);
        assertThat(model.getHiddenCount()).isZero();
        assertThat(model.getSuppressedCount()).isZero();
    }

    @Test
    void includesSpecDataAndSpecDescriptionTooltipText() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                new KmuConditionSpec("cold", "Cold", null, "Cold condition description.", "Vanilla", true)));
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);

        KmuConditionPickerEntry entry = factory.create(new FakeEditableMarket())
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
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);

        KmuConditionPickerEntry entry = factory.create(new FakeEditableMarket(
                Set.of("no_atmosphere"),
                Set.of("no_atmosphere")))
                .getEntries()
                .get(0);

        assertThat(entry.isPresent()).isTrue();
        assertThat(entry.isSuppressed()).isTrue();
        assertThat(entry.isHidden()).isFalse();
    }

    @Test
    void extractsLocationNamesFromStarsectorMarket() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);
        Constellation constellation = new Constellation(
                Constellation.ConstellationType.NORMAL,
                StarAge.AVERAGE);
        constellation.setNameOverride("Corvus");
        StarSystemAPI system = starSystem("Corvus Star System", constellation);

        KmuConditionPickerModel model = factory.create(new StarsectorEditableMarket(
                market(
                        List.of(),
                        Map.of(),
                        Set.of(),
                        "Valis Outpost",
                        system)));

        assertThat(model.getLocation().getPlanetName()).contains("Valis Outpost");
        assertThat(model.getLocation().getStarSystemName()).contains("Corvus Star System");
        assertThat(model.getLocation().getConstellationName()).contains("Corvus");
    }

    @Test
    void extractsPlanetOwnerRelationshipAndGravityWellDetailsFromStarsectorMarket() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);
        PlanetAPI star = planet("Corvus", "yellow star", null);
        PlanetAPI planet = planet("Valis", "terran world", star);

        KmuConditionPickerModel model = factory.create(new StarsectorEditableMarket(
                market(
                        List.of(),
                        Map.of(),
                        Set.of(),
                        "Valis Outpost",
                        starSystem("Corvus Star System", null),
                        planet,
                        planet,
                        faction("Hegemony"))));

        KmuConditionPickerLocation location = model.getLocation();
        assertThat(location.getPlanetName()).contains("Valis");
        assertThat(location.getPlanetType()).contains("terran world");
        assertThat(location.getFactionName()).contains("Hegemony");
        assertThat(location.getFactionColor()).contains(FACTION_COLOR);
        assertThat(location.getRelationshipDescription()).contains("Vengeful (-100 / 100)");
        assertThat(location.getRelationshipColor()).contains(RELATIONSHIP_COLOR);
        assertThat(location.getGravityWellTypeName()).contains("yellow star");
        assertThat(location.getGravityWellName()).contains("Corvus");
    }

    @Test
    void tracesGravityWellThroughNestedOrbitFocusChain() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);
        SectorEntityToken barycenter = entity("Kumari barycenter");
        PlanetAPI gasGiant = planet("Kumari", "gas giant", barycenter);
        PlanetAPI moon = planet("Valis", "barren moon", gasGiant);

        KmuConditionPickerModel model = factory.create(new StarsectorEditableMarket(
                market(
                        List.of(),
                        Map.of(),
                        Set.of(),
                        "Valis Outpost",
                        starSystem("Kumari Star System", null),
                        moon,
                        moon,
                        null)));

        assertThat(model.getLocation().getGravityWellTypeName()).contains("Kumari barycenter");
        assertThat(model.getLocation().getGravityWellName()).contains("Kumari barycenter");
    }

    @Test
    void marksPresentHiddenEntriesFromLiveConditionPlugin() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("no_atmosphere", "No Atmosphere", true)));
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);
        MarketConditionAPI condition = condition("no_atmosphere", plugin(false, "graphics/icons/live.png"));

        KmuConditionPickerEntry entry = factory.create(new StarsectorEditableMarket(
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
        KmuConditionPickerModelFactory factory = new KmuConditionPickerModelFactory(service);

        KmuConditionPickerModel model = factory.create(new FakeEditableMarket());

        assertThatThrownBy(() -> model.getEntries().add(new KmuConditionPickerEntry(
                "cold",
                "Cold",
                null,
                KmuConditionPickerEntryState.ABSENT,
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
        return market(conditions, conditionsById, suppressedConditionIds, null, null);
    }

    private static MarketAPI market(
            List<MarketConditionAPI> conditions,
            Map<String, MarketConditionAPI> conditionsById,
            Set<String> suppressedConditionIds,
            String marketName,
            StarSystemAPI starSystem) {
        return market(conditions, conditionsById, suppressedConditionIds, marketName, starSystem, null, null, null);
    }

    private static MarketAPI market(
            List<MarketConditionAPI> conditions,
            Map<String, MarketConditionAPI> conditionsById,
            Set<String> suppressedConditionIds,
            String marketName,
            StarSystemAPI starSystem,
            PlanetAPI planet,
            SectorEntityToken primaryEntity,
            FactionAPI faction) {
        return proxy(MarketAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getConditions":
                    return conditions;
                case "getFirstCondition":
                    return conditionsById.get((String) args[0]);
                case "isConditionSuppressed":
                    return suppressedConditionIds.contains((String) args[0]);
                case "getName":
                    return marketName;
                case "getStarSystem":
                    return starSystem;
                case "getContainingLocation":
                    return starSystem;
                case "getPlanetEntity":
                    return planet;
                case "getPrimaryEntity":
                    return primaryEntity;
                case "getFaction":
                    return faction;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static StarSystemAPI starSystem(String nameWithTypeShort, Constellation constellation) {
        return proxy(StarSystemAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getNameWithTypeShort":
                    return nameWithTypeShort;
                case "getName":
                    return nameWithTypeShort;
                case "getConstellation":
                    return constellation;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static PlanetAPI planet(String name, String typeNameWithWorld, SectorEntityToken orbitFocus) {
        return proxy(PlanetAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
                case "getTypeNameWithWorld":
                case "getTypeNameWithWorldLowerCase":
                    return typeNameWithWorld;
                case "getOrbitFocus":
                    return orbitFocus;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static SectorEntityToken entity(String name) {
        return proxy(SectorEntityToken.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
                case "getOrbitFocus":
                    return null;
                case "isSystemCenter":
                    return true;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static FactionAPI faction(String name) {
        return proxy(FactionAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDisplayNameLong":
                case "getDisplayName":
                    return name;
                case "getBaseUIColor":
                    return FACTION_COLOR;
                case "getRelToPlayer":
                    return relationship();
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static RelationshipAPI relationship() {
        return proxy(RelationshipAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLevel":
                    return RepLevel.VENGEFUL;
                case "getRepInt":
                    return -100;
                case "getRelColor":
                    return RELATIONSHIP_COLOR;
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
