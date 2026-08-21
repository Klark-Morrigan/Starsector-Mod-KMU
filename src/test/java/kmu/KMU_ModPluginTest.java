package kmu;

import com.fs.starfarer.api.BaseModPlugin;

import kmu.maplayers.base.render.MapLayerTerrainInstaller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs, and
 * handing the engine's XStream to the alias lineage that keeps older saves loading.
 *
 * <p>What each installer registers is pinned beside that installer, not here. The entry point only
 * names them in order, and a suite that re-asserted their registrations would be a second copy of
 * every installer's own contract - one that passes for as long as nobody moves a listener.
 */
class KMU_ModPluginTest {

    @Nested
    class ModIdentity {

        @Test
        void extendsStarsectorBaseModPlugin() {
            assertThat(new KMU_ModPlugin())
                .isInstanceOf(BaseModPlugin.class);
        }
    }

    @Nested
    class ConfigureXStream {

        @Test
        void handsTheEngineXStreamToTheTerrainPluginsAliasLineage() {
            // What the aliases are is pinned where they live; what is pinned here is that the mod
            // plugin still hands them the instance the engine supplies. Drop this call and every
            // save written under a former plugin name stops loading, with no other test failing.
            //
            // XStream is fully qualified for the reason the production call site fully qualifies it:
            // com.thoughtworks belongs to no import group the checkstyle order recognises. The
            // instance is a stand-in because constructing a real one fails outright on a modern JVM,
            // its TreeMapConverter reflecting into java.util internals that are no longer open.
            try (var installerStaticMock = mockStatic(MapLayerTerrainInstaller.class)) {

                var xstreamMock = mock(com.thoughtworks.xstream.XStream.class);

                new KMU_ModPlugin().configureXStream(xstreamMock);

                installerStaticMock.verify(
                    () -> MapLayerTerrainInstaller.registerSaveAliases(xstreamMock));
            }
        }
    }
}
