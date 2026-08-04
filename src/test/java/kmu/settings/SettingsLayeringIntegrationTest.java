package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one rule the settings split exists to buy: the map-layer framework does not name a
 * layer's settings class. Splitting {@link KmuLunaSettings} by which package reads a knob only
 * holds while nothing under {@code kmu.maplayers.base} reaches back across the line, and an import
 * added in passing would put a feature's vocabulary back into the framework with nothing to say so
 * - the class would still compile and the knob would still read.
 *
 * <p>Read off the source text rather than off compiled classes because the guard is about what a
 * file names, not about what survives inlining: a constant folded at compile time leaves no trace
 * in the bytecode while the coupling it came from is still in the source a reader has to trust.
 */
final class SettingsLayeringIntegrationTest {

    private static final Path MAP_LAYER_FRAMEWORK_ROOT =
        Path.of("src", "main", "java", "kmu", "maplayers", "base");
        
    private static final String JAVA_SOURCE_SUFFIX = ".java";

    @Nested
    class MapLayerFrameworkSources {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.settings.SettingsLayeringIntegrationTest#provideFeatureSettingsClasses")
        void mapLayerFrameworkSourcesNameNoFeatureSettingsClass(String featureSettingsClass) {
            assertThat(findFrameworkSourcesNaming(featureSettingsClass))
                .as(
                    "%s is one feature's settings; the map-layer framework reads only"
                        + " KmuMapLayerSettings, so a file under %s naming it has reached across"
                        + " the split",
                    featureSettingsClass,
                    MAP_LAYER_FRAMEWORK_ROOT)
                .isEmpty();
        }
    }

    // The settings classes that belong to one feature apiece. KmuLunaSettings is absent on purpose:
    // the framework reads the settings revision off it, which is mod-wide wiring rather than a
    // feature's knob.
    private static Stream<String> provideFeatureSettingsClasses() {
        return Stream.of("KmuPoliticalMapSettings", "KmuMarketConditionSettings");
    }

    private static List<Path> findFrameworkSourcesNaming(String settingsClass) {
        try (var sources = Files.walk(MAP_LAYER_FRAMEWORK_ROOT)) {
            return sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .filter(source -> readSource(source).contains(settingsClass))
                .toList();
        } catch (IOException failure) {
            // Surfaced rather than swallowed: an unreadable source tree means the walk is looking in
            // the wrong place, which would otherwise read as a framework that names nothing.
            throw new UncheckedIOException(
                "Could not walk " + MAP_LAYER_FRAMEWORK_ROOT.toAbsolutePath(),
                failure);
        }
    }

    private static String readSource(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read " + source.toAbsolutePath(), failure);
        }
    }
}
