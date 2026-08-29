package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.MapLayers;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.render.MapSurfaceInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarInstaller;
import kmu.maplayers.base.tooltip.MapHoverInstaller;
import kmu.maplayers.politicalmap.base.FilterSelectionHeal;
import kmu.maplayers.politicalmap.base.PoliticalMapInstaller;
import kmu.settings.KmuLunaSettings;
import kmu.starsector.rat.RandomAssortmentOfThingsSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.verification.VerificationMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs, and
 * the composition it names - which steps a launch is made of, which installers the map layers are
 * made of, and that switching them off reaches every one of them.
 *
 * <p>Every step a launch runs names a class this mod owns, which is what makes the list assertable
 * at all: LunaLib is on no test classpath, so a step reaching it directly would fail to initialise
 * and throw an {@code Error} the wiring guard deliberately does not catch. The one binding that
 * must touch LunaLib is held behind {@code RandomAssortmentOfThingsSettings}, which is stood in for
 * here like the rest.
 *
 * <p>What each installer registers is pinned beside that installer, and when a switch is worth
 * acting on is pinned on {@link KmuToggledFeature}. Neither is re-asserted here: this suite is
 * about the list, not about what the entries do or when they are asked.
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
    class OnApplicationLoad {

        @Test
        void standsUpEveryStepALaunchIsMadeOf() {
            // The list is the whole subject: a step nobody named is a feature that silently never
            // runs, which is how a settings change came to leave a stale spotlight standing. Held
            // over all four at once, so a step dropped from the wiring fails here rather than in
            // play.
            try (var lunaSettingsMock = mockStatic(KmuLunaSettings.class);
                    var mapLayersMock = mockStatic(MapLayers.class);
                    var filterHealMock = mockStatic(FilterSelectionHeal.class);
                    var ratSettingsMock = mockStatic(RandomAssortmentOfThingsSettings.class)) {

                new KMU_ModPlugin().onApplicationLoad();

                lunaSettingsMock.verify(KmuLunaSettings::installBindings);
                mapLayersMock.verify(MapLayers::registerAll);
                filterHealMock.verify(FilterSelectionHeal::installHealOnSettingsChange);

                // The two settings listeners are pinned on the announcement each answers rather
                // than on the reaction it carries: which mod's settings a listener is bound to is
                // the part a call site can get wrong, and a method reference has no identity to
                // assert on anyway.
                lunaSettingsMock.verify(() -> KmuLunaSettings.runOnSettingsChange(any()));
                ratSettingsMock.verify(
                    () -> RandomAssortmentOfThingsSettings.runOnSettingsChange(any()));
            }
        }
    }

    @Nested
    class InstallMapLayers {

        @Test
        void standsUpEveryInstallerTheOverlayIsMadeOf() {

            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                KMU_ModPlugin.installMapLayers(sectorMock);

                installers.verifyEveryStandUpFor(sectorMock, times(1));
                installers.verifyEveryTakeBackFor(sectorMock, never());
            }
        }
    }

    @Nested
    class UninstallMapLayers {

        @Test
        void takesEveryOneOfThemBack() {
            // Switching the overlay off has to reach every one of them and not only the surfaces.
            // Across a load the listeners and scripts would be gone by themselves, being transient
            // - but a player who switched it off and went on playing is still running every one.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                KMU_ModPlugin.uninstallMapLayers(sectorMock);

                installers.verifyEveryTakeBackFor(sectorMock, times(1));
                installers.verifyEveryStandUpFor(sectorMock, never());
            }
        }
    }

    // Everything the overlay is composed of, mocked together because every case here is about which
    // of them were called and which were not - a case holding only the ones it asserts on would let
    // an unmocked installer reach a real sector mock and answer for itself.
    private static final class MapLayerInstallerMocks implements AutoCloseable {

        private final MockedStatic<MapLayerInstallations> installationsMock =
            mockStatic(MapLayerInstallations.class);
        private final MockedStatic<PoliticalMapInstaller> politicalMapMock =
            mockStatic(PoliticalMapInstaller.class);
        private final MockedStatic<MapSurfaceInstaller> surfaceMock =
            mockStatic(MapSurfaceInstaller.class);
        private final MockedStatic<SidebarInstaller> sidebarMock =
            mockStatic(SidebarInstaller.class);
        private final MockedStatic<MapHoverInstaller> hoverMock =
            mockStatic(MapHoverInstaller.class);

        // Each half of the composition is listed once, with how often it was expected handed in.
        // A second copy of either list per expectation is how an entry comes to be asserted on in
        // one direction and forgotten in the other - which is the half that catches a stand-up
        // reached by a take-back.
        private void verifyEveryStandUpFor(SectorAPI sector, VerificationMode howOften) {

            installationsMock.verify(
                () -> MapLayerInstallations.installMachineryOn(sector), howOften);
            politicalMapMock.verify(() -> PoliticalMapInstaller.installAll(sector), howOften);
            surfaceMock.verify(() -> MapSurfaceInstaller.installAll(sector), howOften);
            sidebarMock.verify(() -> SidebarInstaller.installAll(sector), howOften);
            hoverMock.verify(() -> MapHoverInstaller.installAll(sector), howOften);
        }

        private void verifyEveryTakeBackFor(SectorAPI sector, VerificationMode howOften) {

            installationsMock.verify(
                () -> MapLayerInstallations.uninstallMachineryFrom(sector), howOften);
            politicalMapMock.verify(() -> PoliticalMapInstaller.uninstallAll(sector), howOften);
            surfaceMock.verify(() -> MapSurfaceInstaller.uninstallAll(sector), howOften);
            sidebarMock.verify(() -> SidebarInstaller.uninstallAll(sector), howOften);
            hoverMock.verify(() -> MapHoverInstaller.uninstallAll(sector), howOften);
        }

        @Override
        public void close() {

            hoverMock.close();
            sidebarMock.close();
            surfaceMock.close();
            politicalMapMock.close();
            installationsMock.close();
        }
    }
}
