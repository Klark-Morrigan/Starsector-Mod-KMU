package kmu.conditions.domain;

import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;

import kmlib.testfixtures.logging.LogAppenderFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

class StarsectorConditionRepositoryTest {

    @Nested
    class GetAllConditionSpecs {

        @Test
        void mapsAllConditionSpecsAndSkipsNullSpecs() {
            var settings = buildSettings(
                    Arrays.asList(
                            buildSpec("hot", "Hot", "graphics/icons/hot.png", true),
                            null,
                            buildSpec("population_3", "Population 3", "graphics/icons/population.png", false)),
                    Map.of(),
                    new ArrayList<>());
            var repository = new StarsectorConditionRepository(settings);

            var specs = repository.getAllConditionSpecs();

            assertThat(specs)
                    .extracting(KmuConditionSpec::getId)
                    .containsExactly("hot", "population_3");
            assertThat(specs.get(0).getName()).isEqualTo("Hot");
            assertThat(specs.get(0).getIcon()).isEqualTo("graphics/icons/hot.png");
            assertThat(specs.get(0).getSourceModName()).isEqualTo("Starsector");
            assertThat(specs.get(0).isPlanetary()).isTrue();
        }

        @Test
        void returnsEmptyListWhenSettingsReturnsNullSpecs() {
            var repository = new StarsectorConditionRepository(
                    buildSettings(null, Map.of(), new ArrayList<>()));

            assertThat(repository.getAllConditionSpecs()).isEmpty();
        }

        @Test
        void propagatesSettingsListFailuresForServiceBoundaryToHandle() {
            var exception = new IllegalStateException("settings list failed");
            var repository = new StarsectorConditionRepository(
                    buildThrowingSettings("getAllMarketConditionSpecs", exception));

            assertThatThrownBy(repository::getAllConditionSpecs)
                    .isSameAs(exception);
        }

        @Test
        void skipsInvalidSpecsInsteadOfCrashing() {
            var settings = buildSettings(
                    List.of(
                            buildSpec("hot", "Hot", "graphics/icons/hot.png", true),
                            buildSpec("   ", "Blank", "graphics/icons/blank.png", true)),
                    Map.of(),
                    new ArrayList<>());
            var repository = new StarsectorConditionRepository(settings);

            assertThat(repository.getAllConditionSpecs())
                    .extracting(KmuConditionSpec::getId)
                    .containsExactly("hot");
        }

        @Test
        void namesTheSpecItDroppedInTheLog() {
            // A condition missing from the list with nothing said about it looks exactly like one
            // the game never had, which leaves the mod that shipped the bad spec unfindable.
            var settings = buildSettings(
                    List.of(buildSpec("   ", "Blank", "graphics/icons/blank.png", true)),
                    Map.of(),
                    new ArrayList<>());
            var repository = new StarsectorConditionRepository(settings);

            var log = LogAppenderFake.captureLogOf(
                    StarsectorConditionRepository.class,
                    repository::getAllConditionSpecs);

            assertThat(log.getMessages())
                    .singleElement(as(STRING))
                    .contains("market condition spec");
        }

        @Test
        void mapsSourceModNameWhenSpecDeclaresOne() {
            var hot = buildSpec(
                    "hot",
                    "Hot",
                    "graphics/icons/hot.png",
                    true,
                    buildSourceMod("Utility Pack"));
            var repository = new StarsectorConditionRepository(
                    buildSettings(List.of(hot), Map.of(), new ArrayList<>()));

            assertThat(repository.getAllConditionSpecs())
                    .singleElement()
                    .extracting(KmuConditionSpec::getSourceModName)
                    .isEqualTo("Utility Pack");
        }
    }

    @Nested
    class FindConditionSpec {

        @Test
        void findsConditionSpecByTrimmedId() {
            var lookups = new ArrayList<String>();
            var hot = buildSpec("hot", "Hot", "graphics/icons/hot.png", true);
            var repository = new StarsectorConditionRepository(
                    buildSettings(List.of(), Map.of("hot", hot), lookups));

            assertThat(repository.findConditionSpec("  hot  "))
                    .hasValueSatisfying(spec -> {
                        assertThat(spec.getId()).isEqualTo("hot");
                        assertThat(spec.getName()).isEqualTo("Hot");
                        assertThat(spec.isPlanetary()).isTrue();
                    });
            assertThat(lookups).containsExactly("hot");
        }

        @Test
        void returnsEmptyForBlankOrMissingLookup() {
            var repository = new StarsectorConditionRepository(
                    buildSettings(List.of(), Map.of(), new ArrayList<>()));

            assertThat(repository.findConditionSpec("   ")).isEmpty();
            assertThat(repository.findConditionSpec(null)).isEmpty();
            assertThat(repository.findConditionSpec("missing")).isEmpty();
        }

        @Test
        void propagatesSettingsLookupFailuresForServiceBoundaryToHandle() {
            var exception = new IllegalStateException("settings lookup failed");
            var repository = new StarsectorConditionRepository(
                    buildThrowingSettings("getMarketConditionSpec", exception));

            assertThatThrownBy(() -> repository.findConditionSpec("hot"))
                    .isSameAs(exception);
        }
    }

    private static SettingsAPI buildSettings(
            List<MarketConditionSpecAPI> allSpecs,
            Map<String, MarketConditionSpecAPI> lookupSpecs,
            List<String> lookupCalls) {
        return proxy(SettingsAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getAllMarketConditionSpecs":
                    return allSpecs;
                case "getMarketConditionSpec":
                    var id = (String) args[0];
                    lookupCalls.add(id);
                    return lookupSpecs.get(id);
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static SettingsAPI buildThrowingSettings(String methodName, RuntimeException exception) {
        return proxy(SettingsAPI.class, (proxy, method, args) -> {
            if (method.getName().equals(methodName)) {
                throw exception;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static MarketConditionSpecAPI buildSpec(String id, String name, String icon, boolean planetary) {
        return buildSpec(id, name, icon, planetary, null);
    }

    private static MarketConditionSpecAPI buildSpec(
            String id,
            String name,
            String icon,
            boolean planetary,
            ModSpecAPI sourceMod) {
        return proxy(MarketConditionSpecAPI.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getId":
                    return id;
                case "getName":
                    return name;
                case "getIcon":
                    return icon;
                case "getDesc":
                    return name + " description";
                case "getSourceMod":
                    return sourceMod;
                case "isPlanetary":
                    return planetary;
                default:
                    return handleObjectMethodOrThrow(proxy, method, args);
            }
        });
    }

    private static ModSpecAPI buildSourceMod(String name) {
        return proxy(ModSpecAPI.class, (proxy, method, args) -> {
            if ("getName".equals(method.getName())) {
                return name;
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
