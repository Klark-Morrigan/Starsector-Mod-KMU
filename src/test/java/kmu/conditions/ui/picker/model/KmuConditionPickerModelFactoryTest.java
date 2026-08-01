package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;

import kmu.conditions.domain.KmuConditionRepository;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuConditionSpec;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
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

class KmuConditionPickerModelFactoryTest {
    private static final Color FACTION_COLOUR = new Color(90, 150, 240);
    private static final Color RELATIONSHIP_COLOUR = new Color(240, 80, 80);

    @Nested
    class Create {

        @Test
        void buildsEntriesForPlanetarySpecsInServiceOrder() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true),
                    spec("population_3", "Population 3", false),
                    spec("farmland_rich", "Farmland: Rich", true)));
            var factory = new KmuConditionPickerModelFactory(service);

            var model = factory.create(new EditableMarketFake("hot"));

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
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    new KmuConditionSpec("cold", "Cold", null, "Cold condition description.", "Vanilla", true)));
            var factory = new KmuConditionPickerModelFactory(service);

            var entry = factory.create(new EditableMarketFake())
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
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("no_atmosphere", "No Atmosphere", true)));
            var factory = new KmuConditionPickerModelFactory(service);

            var entry = factory.create(new EditableMarketFake(
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
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var constellation = new Constellation(
                    Constellation.ConstellationType.NORMAL,
                    StarAge.AVERAGE);
            constellation.setNameOverride("Corvus");
            var system = starSystem("Corvus Star System", constellation);

            var model = factory.create(new StarsectorEditableMarket(
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
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var star = planet("Corvus", "yellow star", null);
            var planet = planet("Valis", "terran world", star);

            var model = factory.create(new StarsectorEditableMarket(
                    market(
                            List.of(),
                            Map.of(),
                            Set.of(),
                            "Valis Outpost",
                            starSystem("Corvus Star System", null),
                            planet,
                            planet,
                            faction("Hegemony"))));

            var location = model.getLocation();
            assertThat(location.getPlanetName()).contains("Valis");
            assertThat(location.getPlanetType()).contains("terran world");
            assertThat(location.getFaction()).isPresent();
            assertThat(location.getFaction().get().getName()).isEqualTo("Hegemony");
            assertThat(location.getFaction().get().getColour()).contains(FACTION_COLOUR);
            assertThat(location.getFaction().get().getRelationshipDescription()).contains("Vengeful (-100 / 100)");
            assertThat(location.getFaction().get().getRelationshipColour()).contains(RELATIONSHIP_COLOUR);
            assertThat(location.getGravityWellTypeName()).contains("yellow star");
            assertThat(location.getGravityWellName()).contains("Corvus");
        }

        @Test
        void omitsFactionWhenFactionNameIsUnreadable() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var planet = planet("Valis", "terran world", null);

            var model = factory.create(new StarsectorEditableMarket(
                    market(
                            List.of(),
                            Map.of(),
                            Set.of(),
                            "Valis Outpost",
                            starSystem("Corvus Star System", null),
                            planet,
                            planet,
                            faction(null))));

            assertThat(model.getLocation().getFaction()).isEmpty();
        }

        @Test
        void tracesGravityWellThroughNestedOrbitFocusChain() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var barycenter = entity("Kumari barycenter");
            var gasGiant = planet("Kumari", "gas giant", barycenter);
            var moon = planet("Valis", "barren moon", gasGiant);

            var model = factory.create(new StarsectorEditableMarket(
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
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("no_atmosphere", "No Atmosphere", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var condition = condition("no_atmosphere", plugin(false, "graphics/icons/live.png"));

            var entry = factory.create(new StarsectorEditableMarket(
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
        void defaultsToNotSuppressedWhenSuppressedCheckThrows() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var market = new KmuEditableMarket() {
                @Override public Set<String> getConditionIds() { return Set.of("hot"); }
                @Override public boolean hasCondition(String id) { return "hot".equals(id); }
                @Override public boolean isConditionSuppressed(String id) {
                    throw new RuntimeException("suppressed check failed");
                }
                @Override public void addCondition(String id) {}
                @Override public void markConditionSurveyed(String id) {}
                @Override public void reapplyConditions() {}
            };

            var entry = factory.create(market).getEntries().get(0);

            assertThat(entry.isPresent()).isTrue();
            assertThat(entry.isSuppressed()).isFalse();
        }

        @Test
        void treatsLiveConditionAsAbsentWhenConditionLookupThrows() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var hotCondition = condition("hot", null);
            var throwingMarket = proxy(MarketAPI.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getConditions": return List.of(hotCondition);
                    case "getFirstCondition": throw new RuntimeException("lookup failed");
                    case "isConditionSuppressed": return false;
                    default: return handleObjectMethodOrThrow(p, method, args);
                }
            });

            var entry = factory.create(new StarsectorEditableMarket(throwingMarket))
                    .getEntries().get(0);

            assertThat(entry.isPresent()).isTrue();
            assertThat(entry.isHidden()).isFalse();
            assertThat(entry.getTooltipRenderer()).isEmpty();
            assertThat(entry.getIcon()).contains("graphics/icons/hot.png");
        }

        @Test
        void usesSpecIconWhenLiveConditionHasNullPlugin() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var conditionWithNullPlugin = condition("hot", null);

            var entry = factory.create(new StarsectorEditableMarket(
                    market(List.of(conditionWithNullPlugin),
                            Map.of("hot", conditionWithNullPlugin), Set.of())))
                    .getEntries().get(0);

            assertThat(entry.getIcon()).contains("graphics/icons/hot.png");
            assertThat(entry.isHidden()).isFalse();
            assertThat(entry.getTooltipRenderer()).isPresent();
        }

        @Test
        void usesSpecIconWhenLiveIconNameIsBlank() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var conditionWithBlankIcon = condition("hot", plugin(true, "  "));

            var entry = factory.create(new StarsectorEditableMarket(
                    market(List.of(conditionWithBlankIcon),
                            Map.of("hot", conditionWithBlankIcon), Set.of())))
                    .getEntries().get(0);

            assertThat(entry.getIcon()).contains("graphics/icons/hot.png");
        }

        @Test
        void defaultsToNotHiddenAndUsesSpecIconWhenPluginThrows() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);
            var throwingPluginCondition = proxy(MarketConditionAPI.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getId": return "hot";
                    case "getPlugin": throw new RuntimeException("plugin unavailable");
                    default: return handleObjectMethodOrThrow(p, method, args);
                }
            });

            var entry = factory.create(new StarsectorEditableMarket(
                    market(List.of(throwingPluginCondition),
                            Map.of("hot", throwingPluginCondition), Set.of())))
                    .getEntries().get(0);

            assertThat(entry.isHidden()).isFalse();
            assertThat(entry.getIcon()).contains("graphics/icons/hot.png");
            assertThat(entry.getTooltipRenderer()).isPresent();
        }

        @Test
        void returnsImmutableEntryList() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    spec("hot", "Hot", true)));
            var factory = new KmuConditionPickerModelFactory(service);

            var model = factory.create(new EditableMarketFake());

            assertThatThrownBy(() -> model.getEntries().add(new KmuConditionPickerEntry(
                    "cold",
                    "Cold",
                    null,
                    KmuConditionPickerEntryState.ABSENT,
                    "tooltip")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static final class ConditionRepositoryFake implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private ConditionRepositoryFake(KmuConditionSpec... specs) {
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

    private static final class EditableMarketFake implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> suppressedConditionIds = new LinkedHashSet<>();

        private EditableMarketFake(String... conditionIds) {
            for (var conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        private EditableMarketFake(Set<String> conditionIds, Set<String> suppressedConditionIds) {
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
                    return FACTION_COLOUR;
                case "getRelToPlayer":
                    return createRelationship();
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static RelationshipAPI createRelationship() {
        return proxy(RelationshipAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLevel":
                    return RepLevel.VENGEFUL;
                case "getRepInt":
                    return -100;
                case "getRelColor":
                    return RELATIONSHIP_COLOUR;
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
        // Default to Mockito-style defaults (null for objects, 0/false for
        // primitives) for unstubbed Starsector API methods so the now-bare
        // production reads do not crash. Tests that need a specific method
        // to throw still stub it explicitly in their switch case.
        return defaultForReturnType(method.getReturnType());
    }

    private static Object defaultForReturnType(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == void.class) return null;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0.0;
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }
}
