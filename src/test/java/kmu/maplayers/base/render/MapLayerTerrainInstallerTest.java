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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapLayerTerrainInstallerTest {

    // The live terrain type id, pinned as a literal: it is written into every save, so a rename
    // must break this test rather than ship and quietly strand the entity existing saves hold.
    private static final String CURRENT_TERRAIN_TYPE = "kmu_sector_map_layer_terrain";

    // The live type id of the Starscape variant, pinned as a literal for the reason the schematic
    // one is, and read back off the production constant below because that variant's install cannot
    // be driven far enough to observe the id it adds under.
    private static final String CURRENT_STARSCAPE_TERRAIN_TYPE =
        "kmu_sector_map_layer_starscape_terrain";

    // The live type id of the variant that paints above the map's nebulae, pinned as a literal
    // for the reason the two above it are.
    private static final String CURRENT_ABOVE_STARSCAPE_NEBULAE_TERRAIN_TYPE =
        "kmu_sector_map_layer_above_starscape_nebulae_terrain";

    // What the Starscape variant's entity reports in place of the id it was installed under - the
    // one terrain type the map widget draws in Starscape mode. A literal here rather than a read of
    // the production constant because it is the engine's string, not the mod's: the mod cannot
    // rename it and a test agreeing with a changed copy of it would be agreeing with a broken
    // feature.
    private static final String WHITELISTED_MAP_TYPE = "slipstream";

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
            var hyperspaceMock = buildHyperspaceCarrying(buildTerrainMock(
                CURRENT_TERRAIN_TYPE,
                new SectorMapLayerTerrainPlugin()));

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .addTerrain(any(), any());
            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void leavesTerrainBelongingToAnotherModAlone() {
            // The presence check identifies its own by plugin class, so a third-party terrain is
            // never counted as the map layer already being present.
            var otherModTerrainMock = buildTerrainMock(
                "some_other_terrain",
                mock(CampaignTerrainPlugin.class));

            var hyperspaceMock = buildHyperspaceCarrying(otherModTerrainMock);

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock)
                .addTerrain(CURRENT_TERRAIN_TYPE, null);
        }

        @Test
        void installsItsOwnTerrainBesideTheStarscapeVariant() {
            // The Starscape plugin subclasses the schematic one, so an instanceof match here would
            // have the other variant's entity answer for this one's and leave the schematic surface
            // uninstalled - a map drawing nothing outside Starscape mode.
            var starscapeTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerStarscapeTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(starscapeTerrainMock);

            MapLayerTerrainInstaller.installSchematicTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock)
                .addTerrain(CURRENT_TERRAIN_TYPE, null);
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
            // would paint the Starscape overlay twice and double the alpha of every fill. Only the
            // already-present path is driven here - the absent path builds the entity, whose
            // obfuscated supertype chain a verifying JVM refuses to load, which is why the decision
            // itself is pinned through findMapLayerTerrain below.
            var hyperspaceMock = buildHyperspaceCarrying(buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerStarscapeTerrainPlugin()));

            MapLayerTerrainInstaller.installStarscapeTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .addEntity(any());
            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void recognisesTheStarscapeVariantWhateverItReports() {
            // Nothing this variant reports identifies it: its entity answers with the engine's
            // whitelisted map type rather than with the id it was installed under, so the presence
            // check has only the plugin class to go on. One that consulted the reported type would
            // miss the live entity on every load and stack a second Starscape surface on it.
            var starscapeTerrainMock = buildTerrainMock(
                "some_other_reported_type",
                new SectorMapLayerStarscapeTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(starscapeTerrainMock);

            MapLayerTerrainInstaller.installStarscapeTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .addEntity(any());
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

    @Nested
    class InstallAboveStarscapeNebulaeTerrain {

        @Test
        void doesNotStackASecondAboveNebulaeTerrainOnAReloadedSave() {
            // Terrain persists, so a reloaded save already carries this variant too. A second one
            // would draw the faction names twice, at doubled alpha and over the same cluster
            // anchors - and would leave a spare entity behind for the reseat to pick between.
            var hyperspaceMock = buildHyperspaceCarrying(buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin()));

            MapLayerTerrainInstaller.installAboveStarscapeNebulaeTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .addEntity(any());
            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void neverRetiresTheAboveNebulaeVariantOverWhatItReports() {
            // Nothing this variant reports can date it either: it shares the entity class of the
            // variant below, so it reports the engine's whitelisted map type rather than the id it
            // was installed under.
            var aboveNebulaeTerrainMock = buildTerrainMock(
                "some_other_reported_type",
                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(aboveNebulaeTerrainMock);

            MapLayerTerrainInstaller.installAboveStarscapeNebulaeTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void installsUnderTheTypeIdTheShippedTerrainSpecDeclares() {
            // Pinned on the constant for the reason the variant below it is: the add builds an
            // entity whose obfuscated supertype chain a verifying JVM refuses to load, and this id
            // is a terrain.json row key with no way back once a save is written under a wrong one.
            assertThat(MapLayerTerrainInstaller.SECTOR_MAP_LAYER_ABOVE_STARSCAPE_NEBULAE_TERRAIN_TYPE)
                .isEqualTo(CURRENT_ABOVE_STARSCAPE_NEBULAE_TERRAIN_TYPE);
        }

        @Test
        void installsUnderAnIdOfItsOwn() {
            // The two Starscape variants share an entity class and a reported type, so the row id
            // handed to that class at construction is the only thing that decides which plugin an
            // entity resolves to - and a shared id would give both surfaces the same one.
            assertThat(MapLayerTerrainInstaller.SECTOR_MAP_LAYER_ABOVE_STARSCAPE_NEBULAE_TERRAIN_TYPE)
                .isNotEqualTo(MapLayerTerrainInstaller.SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE);
        }

        @Test
        void toleratesANullSector() {

            var aboveNebulaeInstallOnNullSector = (Runnable) () ->
                MapLayerTerrainInstaller.installAboveStarscapeNebulaeTerrain(null);

            assertThatCode(aboveNebulaeInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    // The published read, as opposed to the walk below it. What is pinned here is the path down to
    // that walk and the variant it is aimed at, since the caller is a per-frame script that asks on
    // every advance it might act on - so every way a sector can decline to answer is an ordinary
    // frame rather than an error.
    @Nested
    class FindAboveStarscapeNebulaeTerrain {

        @Test
        void findsTheAboveNebulaeTerrainHyperspaceIsCarrying() {

            var aboveNebulaeTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin());

            var sectorMock =
                buildSectorWithHyperspace(buildHyperspaceCarrying(aboveNebulaeTerrainMock));

            assertThat(MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(sectorMock))
                .isSameAs(aboveNebulaeTerrainMock);
        }

        @Test
        void doesNotAnswerWithTheSurfaceBeneathTheNebulae() {
            // Every variant is installed side by side, so a loaded save always carries all of them
            // and this walk always has a wrong answer available. Handing back the lower Starscape
            // half would lift the fills clear of the fog they are meant to read through, and leave
            // the text buried under it - the exact inversion of the split.
            var starscapeTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerStarscapeTerrainPlugin());

            var sectorMock =
                buildSectorWithHyperspace(buildHyperspaceCarrying(starscapeTerrainMock));

            assertThat(MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(sectorMock))
                .isNull();
        }

        @Test
        void doesNotAnswerWithTheSchematicHalf() {
            // Moving that one would move a surface the engine is not drawing in this mode at all,
            // which looks like nothing happening rather than like a mix-up.
            var schematicTerrainMock = buildTerrainMock(
                CURRENT_TERRAIN_TYPE,
                new SectorMapLayerTerrainPlugin());

            var sectorMock =
                buildSectorWithHyperspace(buildHyperspaceCarrying(schematicTerrainMock));

            assertThat(MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(sectorMock))
                .isNull();
        }

        @Test
        void answersWithNothingWhileHyperspaceCarriesNoTerrain() {
            // The state every load passes through before the install runs, and the state the reseat
            // itself creates for one advance, so absent has to be an answer rather than a fault.
            var sectorMock = buildSectorWithHyperspace(buildHyperspaceCarrying());

            assertThat(MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(sectorMock))
                .isNull();
        }

        @Test
        void answersWithNothingWhenThereIsNoHyperspace() {

            assertThat(MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(
                    buildSectorWithHyperspace(null)))
                .isNull();
        }

        @Test
        void answersWithNothingWhenThereIsNoSector() {

            assertThat(MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(null))
                .isNull();
        }
    }

    // Exercised with the Starscape variant's wiring: it is the variant whose install cannot be
    // driven end to end from a test, so this is where its presence decision is pinned. The
    // schematic one's is covered through its own install above. The reseat reads through this same
    // walk to get hold of the entity it moves, so a match loosened here would move a stranger's.
    @Nested
    class FindMapLayerTerrain {

        @Test
        void findsTheStarscapeVariantByItsPluginClass() {

            var starscapeTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerStarscapeTerrainPlugin());

            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(starscapeTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isSameAs(starscapeTerrainMock);
        }

        @Test
        void doesNotMistakeTheSchematicVariantForTheStarscapeOne() {
            // The exact-class compare is the whole guard: the Starscape plugin subclasses the
            // schematic one, so an instanceof would answer true here and the Starscape variant would
            // never install.
            var schematicTerrainMock = buildTerrainMock(
                CURRENT_TERRAIN_TYPE,
                new SectorMapLayerTerrainPlugin());

            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(schematicTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isNull();
        }

        @Test
        void doesNotMistakeTheAboveNebulaeVariantForTheOneBeneathTheNebulae() {
            // The two Starscape variants are indistinguishable by anything they report - same entity
            // class, same whitelisted type - so the exact-class compare on the plugin is the only
            // thing separating them. An instanceof would have the lower variant answer for the
            // upper one, so the upper would never install and the names would stay under the fog.
            var aboveNebulaeTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin());

            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(aboveNebulaeTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isNull();
        }

        @Test
        void findsTheAboveNebulaeVariantByItsPluginClass() {

            var aboveNebulaeTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin());

            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(aboveNebulaeTerrainMock),
                    MapLayerTerrainInstaller.ABOVE_STARSCAPE_NEBULAE_TERRAIN))
                .isSameAs(aboveNebulaeTerrainMock);
        }

        @Test
        void doesNotMistakeARealSlipstreamForTheStarscapeVariant() {
            // Hyperspace's own slipstreams report the very type this variant's entity reports, so
            // the reported type cannot take part in the decision at all - only the plugin class can.
            var slipstreamTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                mock(CampaignTerrainPlugin.class));

            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(slipstreamTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isNull();
        }

        @Test
        void toleratesTerrainCarryingNoPlugin() {
            // A terrain whose spec failed to resolve has no plugin to compare, and reading through
            // the null would fail the whole load-time install rather than skip one entity.
            var pluginlessTerrainMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                null);

            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(pluginlessTerrainMock),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isNull();
        }

        @Test
        void reportsAbsentForALocationCarryingNoTerrain() {
            assertThat(MapLayerTerrainInstaller.findMapLayerTerrain(
                    List.of(),
                    MapLayerTerrainInstaller.STARSCAPE_TERRAIN))
                .isNull();
        }
    }

    @Nested
    class RemoveMapLayerTerrain {

        @Test
        void removesEverySurfaceThisModInstalls() {
            // What switching the map layers off has to take back. Terrain is an entity and entities
            // persist, so one left behind would sit in the save for as long as it exists - where
            // every listener and script the surfaces install is transient and simply never
            // registered again.
            var schematicMock = buildTerrainMock(
                CURRENT_TERRAIN_TYPE,
                new SectorMapLayerTerrainPlugin());
            var starscapeMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerStarscapeTerrainPlugin());
            var aboveNebulaeMock = buildTerrainMock(
                WHITELISTED_MAP_TYPE,
                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin());

            var hyperspaceMock = buildHyperspaceCarrying(
                schematicMock, starscapeMock, aboveNebulaeMock);

            MapLayerTerrainInstaller.removeMapLayerTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock)
                .removeEntity(schematicMock);
            verify(hyperspaceMock)
                .removeEntity(starscapeMock);
            verify(hyperspaceMock)
                .removeEntity(aboveNebulaeMock);
        }

        @Test
        void leavesTerrainBelongingToAnotherModAlone() {
            // A player switching this mod's overlays off is not asking for anybody else's terrain to
            // go with them, and the walk has only the plugin class to tell the difference by.
            var otherModTerrainMock = buildTerrainMock(
                "some_other_terrain",
                mock(CampaignTerrainPlugin.class));

            var hyperspaceMock = buildHyperspaceCarrying(otherModTerrainMock);

            MapLayerTerrainInstaller.removeMapLayerTerrain(
                buildSectorWithHyperspace(hyperspaceMock));

            verify(hyperspaceMock, never())
                .removeEntity(any());
        }

        @Test
        void toleratesANullSector() {

            var removeOnNullSector = (Runnable) () ->
                MapLayerTerrainInstaller.removeMapLayerTerrain(null);

            assertThatCode(removeOnNullSector::run)
                .doesNotThrowAnyException();
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
