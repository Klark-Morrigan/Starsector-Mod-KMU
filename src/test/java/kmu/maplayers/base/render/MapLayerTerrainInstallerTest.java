package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapLayerTerrainInstallerTest {

    // The live terrain type id, pinned as a literal: it is written into every save, so a rename
    // must break this test rather than ship and quietly strand the entity existing saves hold.
    private static final String CURRENT_TERRAIN_TYPE = "kmu_sector_map_layer_terrain";

    // The id shipped saves were written under before the rename. Pinned for the same reason from
    // the other side: this is the string the load-time sweep has to recognise as stale.
    private static final String LEGACY_TERRAIN_TYPE = "kmu_political_terrain";

    // The live type id of the starscape variant, pinned as a literal for the reason the schematic
    // one is, and read back off the production constant below because that variant's install cannot
    // be driven far enough to observe the id it adds under.
    private static final String CURRENT_STARSCAPE_TERRAIN_TYPE =
        "kmu_sector_map_layer_starscape_terrain";

    // What the starscape variant's entity reports in place of the id it was installed under - the
    // one terrain type the map widget draws over the starfield. A literal here rather than a read of
    // the production constant because it is the engine's string, not the mod's: the mod cannot
    // rename it and a test agreeing with a changed copy of it would be agreeing with a broken
    // feature.
    private static final String WHITELISTED_MAP_TYPE = "slipstream";

    // Every fully-qualified name the terrain plugin has been saved under. Restated here as literals
    // rather than read from the production list: a constant read from the class under test would be
    // renamed alongside it and go on agreeing with itself, while these are what a save file on disk
    // actually holds and so cannot be allowed to move.
    private static final List<String> FORMER_TERRAIN_PLUGIN_CLASSES = List.of(
        "kmu.politicalmap.render.PoliticalMapTerrainPlugin",
        "kmu.politicalmap.render.FactionsPoliticalMapTerrainPlugin",
        "kmu.maplayers.politicalmap.factions.render.FactionsPoliticalMapTerrainPlugin",
        "kmu.maplayers.politicalmap.base.render.PoliticalMapTerrainPlugin");

    // What the installer registers on the engine's XStream is checked against a stand-in rather than
    // a real one: constructing an XStream fails outright on a modern JVM, its TreeMapConverter
    // reflecting into java.util internals that are no longer open, and the game supplies the
    // instance anyway - the mod only ever configures one it is handed. XStream is fully qualified
    // for the reason the production call site fully qualifies it: com.thoughtworks belongs to no
    // import group the checkstyle order recognises.
    @Nested
    class RegisterSaveAliases {

        @Test
        void aliasesEveryFormerTerrainPluginNameToTheLiveClass() {
            // Each of these is a class name a shipped save may still hold. Without its alias the
            // save does not load at all - XStream fails the whole read with
            // CannotResolveClassException - so an alias dropped by a later edit is a save-breaking
            // regression that nothing else would catch until a player reported it.
            var xstreamMock = mock(com.thoughtworks.xstream.XStream.class);

            MapLayerTerrainInstaller.registerSaveAliases(xstreamMock);

            for (var formerClass : FORMER_TERRAIN_PLUGIN_CLASSES) {
                verify(xstreamMock)
                    .alias(formerClass, SectorMapLayerTerrainPlugin.class);
            }
        }

        @Test
        void aliasesTheLiveClassNameLastSoResavedGamesShedTheFormerNames() {
            // Order is the whole contract here: XStream keeps one name per class for writing, so
            // whichever alias is registered last decides what a re-saved game is written under.
            // Registered after the former names, the self-alias means a save sheds them; registered
            // before, every re-save would silently pin a dead class name back into the file.
            var xstreamMock = mock(com.thoughtworks.xstream.XStream.class);

            MapLayerTerrainInstaller.registerSaveAliases(xstreamMock);

            var aliasOrder = inOrder(xstreamMock);
            aliasOrder.verify(xstreamMock)
                .alias(
                    FORMER_TERRAIN_PLUGIN_CLASSES.get(FORMER_TERRAIN_PLUGIN_CLASSES.size() - 1),
                    SectorMapLayerTerrainPlugin.class);
            aliasOrder.verify(xstreamMock)
                .alias(
                    SectorMapLayerTerrainPlugin.class.getName(), SectorMapLayerTerrainPlugin.class);
        }
    }

    @Nested
    class InstallSchematicTerrain {

        @Test
        void installsTheTerrainWhenHyperspaceCarriesNone() {

            var hyperspaceMock = buildHyperspaceCarrying();

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock)
                .addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void doesNotStackASecondTerrainOnAReloadedSave() {
            // Terrain persists, so a reloaded save already carries it; adding another would paint
            // the same overlay twice and double the alpha of every fill.
            var hyperspaceMock = buildHyperspaceCarrying(
                buildTerrainMock(CURRENT_TERRAIN_TYPE, new SectorMapLayerTerrainPlugin()));

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .addTerrain(any(), any());
            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void retiresTerrainLeftUnderAFormerTypeIdAndInstallsTheCurrentOne() {
            // The type id is serialised, so a save written before the rename holds an entity under
            // the old id whose spec no longer resolves. It has to go, or the save ends up with the
            // stale entity plus the freshly added one.
            var staleTerrainMock =
                buildTerrainMock(LEGACY_TERRAIN_TYPE, new SectorMapLayerTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(staleTerrainMock);

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock)
                .removeEntity(staleTerrainMock);
            verify(hyperspaceMock)
                .addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void leavesTerrainBelongingToAnotherModAlone() {
            // The sweep identifies its own by plugin class, so a third-party terrain is neither
            // retired nor counted as the map layer already being present.
            var otherModTerrainMock =
                buildTerrainMock("some_other_terrain", mock(CampaignTerrainPlugin.class));

            var hyperspaceMock = buildHyperspaceCarrying(otherModTerrainMock);

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .removeEntity(any());
            verify(hyperspaceMock)
                .addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void retiresTheStaleTerrainWithoutTouchingTheStarscapeVariant() {
            // The starscape plugin subclasses the schematic one, so an instanceof match here would
            // let this sweep retire the other variant's entity - which reports a type id this one
            // never installs, and so looks stale to any test that is not exact about the class.
            var starscapeTerrainMock =
                buildTerrainMock(WHITELISTED_MAP_TYPE, new SectorMapLayerStarscapeTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(starscapeTerrainMock);

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void toleratesANullSector() {

            var schematicInstallOnNullSector = (Runnable) () ->
                MapLayerTerrainInstaller.installSchematicTerrain(null);

            assertThatCode(schematicInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallStarscapeTerrain {

        @Test
        void doesNotStackASecondStarscapeTerrainOnAReloadedSave() {
            // Terrain persists, so a reloaded save already carries this variant too; a second one
            // would paint the starscape overlay twice and double the alpha of every fill. Only the
            // already-present path is driven here - the absent path builds the entity, whose
            // obfuscated supertype chain a verifying JVM refuses to load, which is why the decision
            // itself is pinned through hasMapLayerTerrain below.
            var hyperspaceMock = buildHyperspaceCarrying(
                buildTerrainMock(WHITELISTED_MAP_TYPE, new SectorMapLayerStarscapeTerrainPlugin()));

            MapLayerTerrainInstaller.installStarscapeTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .addEntity(any());
            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void neverRetiresTheStarscapeVariantOverWhatItReports() {
            // Nothing this variant reports can date it: its entity answers with the engine's
            // whitelisted map type rather than with the id it was installed under, so a reported
            // type the sweep does not recognise is the ordinary case and not a former id. Sweeping
            // on it would retire the live entity on every load and leave the starfield painting
            // nothing.
            var starscapeTerrainMock = buildTerrainMock(
                "some_other_reported_type", new SectorMapLayerStarscapeTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(starscapeTerrainMock);

            MapLayerTerrainInstaller.installStarscapeTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void installsUnderTheTypeIdTheShippedTerrainSpecDeclares() {
            // Pinned on the constant rather than through the add, which cannot be driven from a
            // test: building the entity loads a chain of obfuscated core classes a verifying JVM
            // refuses. The id is a terrain.json row key, and a constant that drifts from it resolves
            // to no spec at game load - with no way back, since this variant's entity cannot report
            // the id it was built with and so cannot be recognised as stale on a later load.
            assertThat(MapLayerTerrainInstaller.SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE)
                .isEqualTo(CURRENT_STARSCAPE_TERRAIN_TYPE);
        }

        @Test
        void toleratesANullSector() {

            var starscapeInstallOnNullSector = (Runnable) () ->
                MapLayerTerrainInstaller.installStarscapeTerrain(null);

            assertThatCode(starscapeInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    // Exercised with the starscape variant's wiring: it is the variant whose install cannot be
    // driven end to end from a test, so this is where its presence decision is pinned. The
    // schematic one's is covered through its own install above.
    @Nested
    class HasMapLayerTerrain {

        @Test
        void findsTheStarscapeVariantByItsPluginClass() {
            
            var starscapeTerrainMock =
                buildTerrainMock(WHITELISTED_MAP_TYPE, new SectorMapLayerStarscapeTerrainPlugin());

            assertThat(MapLayerTerrainInstaller.hasMapLayerTerrain(
                    List.of(starscapeTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isTrue();
        }

        @Test
        void doesNotMistakeTheSchematicVariantForTheStarscapeOne() {
            // The exact-class compare is the whole guard: the starscape plugin subclasses the
            // schematic one, so an instanceof would answer true here and the starscape variant would
            // never install.
            var schematicTerrainMock =
                buildTerrainMock(CURRENT_TERRAIN_TYPE, new SectorMapLayerTerrainPlugin());

            assertThat(MapLayerTerrainInstaller.hasMapLayerTerrain(
                    List.of(schematicTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isFalse();
        }

        @Test
        void doesNotMistakeARealSlipstreamForTheStarscapeVariant() {
            // Hyperspace's own slipstreams report the very type this variant's entity reports, so
            // the reported type cannot take part in the decision at all - only the plugin class can.
            var slipstreamTerrainMock =
                buildTerrainMock(WHITELISTED_MAP_TYPE, mock(CampaignTerrainPlugin.class));

            assertThat(MapLayerTerrainInstaller.hasMapLayerTerrain(
                    List.of(slipstreamTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isFalse();
        }

        @Test
        void toleratesTerrainCarryingNoPlugin() {
            // A terrain whose spec failed to resolve has no plugin to compare, and reading through
            // the null would fail the whole load-time install rather than skip one entity.
            var pluginlessTerrainMock = buildTerrainMock(WHITELISTED_MAP_TYPE, null);

            assertThat(MapLayerTerrainInstaller.hasMapLayerTerrain(
                    List.of(pluginlessTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isFalse();
        }

        @Test
        void reportsAbsentForALocationCarryingNoTerrain() {
            assertThat(MapLayerTerrainInstaller.hasMapLayerTerrain(
                    List.of(),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isFalse();
        }
    }

    private static LocationAPI buildHyperspaceCarrying(CampaignTerrainAPI... terrain) {

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getTerrainCopy())
            .thenReturn(List.of(terrain));

        return hyperspaceMock;
    }

    private static CampaignTerrainAPI buildTerrainMock(String type, CampaignTerrainPlugin plugin) {

        var terrainMock = mock(CampaignTerrainAPI.class);

        when(terrainMock.getType())
            .thenReturn(type);
        when(terrainMock.getPlugin())
            .thenReturn(plugin);

        return terrainMock;
    }

    private static SectorAPI buildSectorWithHyperspace(LocationAPI hyperspace) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getHyperspace())
            .thenReturn(hyperspace);

        return sectorMock;
    }
}
