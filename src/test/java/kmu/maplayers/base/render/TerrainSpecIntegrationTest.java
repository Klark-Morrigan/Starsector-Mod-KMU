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
 * Pins the shipped terrain spec against the classes it names and the ids it declares. The spec
 * reaches its plugin by fully-qualified class name in a data file, which no compiler ever checks, so
 * a class that moves package or changes name leaves the row pointing at nothing - and the mod still
 * builds green. Its row ids are unchecked from the other side for the same reason: the mod names
 * them as strings to install under. Either failure surfaces only in play, as a terrain that never
 * installs and therefore a map that draws no layer at all. This reads the real file for that reason
 * rather than a fixture.
 */
final class TerrainSpecIntegrationTest {

    private static final Path TERRAIN_SPEC = Path.of("data", "campaign", "terrain.json");

    // The spec is Starsector's JSON dialect - hash comments and trailing commas - which a strict
    // parser rejects, so the one field this test is about is read out directly rather than the file
    // being parsed as JSON.
    private static final Pattern PLUGIN_FIELD = Pattern.compile("\"plugin\"\\s*:\\s*\"([^\"]+)\"");

    // A row id: the terrain type the mod constructs its entity with and the game resolves the spec
    // from. Matched by the object that opens after it, so a quoted id inside a comment or a field
    // value does not count as a declared row.
    private static final Pattern ROW_ID = Pattern.compile("\"(kmu_[A-Za-z0-9_]+)\"\\s*:\\s*\\{");

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
        void thePluginRowsNameExactlyTheTerrainSurfaces() {
            // Named through the classes themselves, so a rename that misses the spec file breaks
            // this test at compile time on one side and at assertion time on the other. Pinned as a
            // set rather than a minimum: a surface added without a row is a band that never paints,
            // and a row left behind by a surface that was removed instantiates a class that no
            // longer exists at game load.
            assertThat(readPluginClassNames())
                .containsExactlyInAnyOrder(
                    SectorMapLayerTerrainPlugin.class.getName(),
                    SectorMapLayerStarscapeTerrainPlugin.class.getName(),
                    SectorMapLayerAboveStarscapeNebulaeTerrainPlugin.class.getName());
        }
    }

    @Nested
    class RowIds {

        @Test
        void theDeclaredRowIdsAreTheOnesTheModInstallsUnder() {
            // Literals rather than a read of the mod's install constants, which sit in another
            // package and would agree with themselves after a rename anyway. The constants are pinned
            // to these same strings from the other side in MapLayerTerrainInstallerTest, which is
            // what makes the data file and the installer agree without either reading the other.
            //
            // The two Starscape rows are the ones that cannot be repaired after the fact: their
            // entities report the engine's whitelisted map type in place of the id they were built
            // with, so an id that drifts from this file resolves to no spec at game load and leaves
            // behind an entity no later sweep can even recognise.
            assertThat(readRowIds())
                .containsExactlyInAnyOrder(
                    "kmu_sector_map_layer_terrain",
                    "kmu_sector_map_layer_starscape_terrain",
                    "kmu_sector_map_layer_above_starscape_nebulae_terrain");
        }
    }

    private static List<String> providePluginClassNames() {
        return readPluginClassNames();
    }

    private static List<String> readRowIds() {
        return ROW_ID.matcher(readTerrainSpec())
            .results()
            .map(match -> match.group(1))
            .toList();
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
