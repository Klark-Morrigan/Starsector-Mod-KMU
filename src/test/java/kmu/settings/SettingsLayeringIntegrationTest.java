package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one rule the settings split exists to buy: the map-layer framework names no settings
 * class but its own. Splitting {@link KmuLunaSettings} by which package reads a knob only
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
        @ArgumentsSource(FeatureSettingsClassesProvider.class)
        void mapLayerFrameworkSourcesNameNoFeatureSettingsClass(String featureSettingsClass) {
            assertThat(findFrameworkSourcesNaming(featureSettingsClass))
                .as(
                    "%s is one feature's settings; the map-layer framework reads only its own"
                        + " settings classes, so a file under %s naming it has reached across"
                        + " the split",
                    featureSettingsClass,
                    MAP_LAYER_FRAMEWORK_ROOT)
                .isEmpty();
        }
    }

    /**
     * The settings classes the framework may not name: those belonging to one feature apiece, the
     * political map's seven sections among them - the split into sections is the feature's own
     * housekeeping and buys nothing here, so each is listed and none stands in for the rest - plus
     * {@link KmuProfilingSettings}, which belongs to no feature but is read by the composition root
     * for the same reason a feature toggle is. What it decides is whether the framework is being
     * measured, and a framework that could read that could also act on it.
     *
     * <p>{@link KmuLunaSettings} is absent on purpose: the framework reads the settings revision
     * off it, which is mod-wide wiring rather than a feature's knob.
     *
     * <p>Named through the classes themselves rather than as text, so a rename that misses this list
     * breaks it at compile time. A literal would still read as a class name after the rename and
     * match no source at all, leaving the walk green while guarding nothing - which is the drift
     * this file exists to catch.
     *
     * <p>A class rather than a factory method for the same reason: a method is reached by a
     * fully-qualified string that no rename follows either.
     */
    static final class FeatureSettingsClassesProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return Stream
                .of(
                    KmuPoliticalMapTerritorySettings.class,
                    KmuPoliticalMapRibbonSettings.class,
                    KmuPoliticalMapHighlightSettings.class,
                    KmuPoliticalMapDrawOrderSettings.class,
                    KmuPoliticalMapDominanceSettings.class,
                    KmuPoliticalMapGeometrySettings.class,
                    KmuPoliticalMapDiagnosticsSettings.class,
                    KmuMarketConditionSettings.class,
                    KmuProfilingSettings.class)
                .map(Class::getSimpleName)
                .map(Arguments::of);
        }
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
