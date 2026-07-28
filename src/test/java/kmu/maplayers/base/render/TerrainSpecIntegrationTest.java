package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped terrain spec against the classes it names. The spec reaches its plugin by
 * fully-qualified class name in a data file, which no compiler ever checks, so a class that moves
 * package or changes name leaves the row pointing at nothing - and the mod still builds green. The
 * failure surfaces only in play, as a terrain that never installs and therefore a map that draws no
 * layer at all. This reads the real file for that reason rather than a fixture.
 */
final class TerrainSpecIntegrationTest {
    private static final Path TERRAIN_SPEC = Path.of("data", "campaign", "terrain.json");

    // The spec is Starsector's JSON dialect - hash comments and trailing commas - which a strict
    // parser rejects, so the one field this test is about is read out directly rather than the file
    // being parsed as JSON.
    private static final Pattern PLUGIN_FIELD = Pattern.compile("\"plugin\"\\s*:\\s*\"([^\"]+)\"");

    @Nested
    class PluginRows {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.maplayers.base.render.TerrainSpecIntegrationTest#providePluginClassNames")
        void everyPluginRowNamesALoadableCampaignTerrainPlugin(String pluginClassName) {
            // The engine instantiates whatever the row names and casts it to this interface, so a
            // row naming a real class of the wrong type fails just as hard as one naming nothing.
            assertThat(CampaignTerrainPlugin.class)
                    .as("supertype of %s", pluginClassName)
                    .isAssignableFrom(loadClass(pluginClassName));
        }

        @Test
        void thePluginRowsNameExactlyTheTerrainSurfacePair() {
            // Named through the classes themselves, so a rename that misses the spec file breaks
            // this test at compile time on one side and at assertion time on the other.
            assertThat(readPluginClassNames()).containsExactlyInAnyOrder(
                    SectorMapLayerTerrainPlugin.class.getName(),
                    SectorMapLayerStarscapeTerrainPlugin.class.getName());
        }
    }

    private static List<String> providePluginClassNames() {
        return readPluginClassNames();
    }

    // Fails the test rather than erroring out: a name the classloader cannot find is exactly the
    // regression this file exists to catch, so it reads as an assertion failure naming the row.
    private static Class<?> loadClass(String className) {
        try {
            // Not initialised: this only asks whether the engine could resolve the name, and running
            // a plugin's static setup to answer that would be a side effect the question does not need.
            return Class.forName(className, false, TerrainSpecIntegrationTest.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(TERRAIN_SPEC + " names a class that does not exist: " + className,
                    missing);
        }
    }

    private static List<String> readPluginClassNames() {
        return PLUGIN_FIELD.matcher(readTerrainSpec())
                .results()
                .map(match -> match.group(1))
                .toList();
    }

    private static String readTerrainSpec() {
        try {
            return Files.readString(TERRAIN_SPEC, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            // Surfaced rather than swallowed: the file is shipped data, so a read failure means the
            // test is looking in the wrong place, not that the spec is fine.
            throw new UncheckedIOException("Could not read " + TERRAIN_SPEC.toAbsolutePath(), failure);
        }
    }
}
