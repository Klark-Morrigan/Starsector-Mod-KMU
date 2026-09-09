package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.diagnostics.ProfilingCaptureInstaller;
import kmu.maplayers.MapLayers;
import kmu.maplayers.base.chrome.MapChromeInstaller;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.refresh.MapSubstrateRefreshInstaller;
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs, and
 * the composition it names - which steps a launch is made of, which switches it applies, which
 * installers the map layers are made of, and that switching them off reaches every one of them.
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

    // The field holding the switches both asks walk. Named as text because it is private, and the
    // lookup fails loudly when it is renamed rather than quietly finding nothing.
    private static final String APPLIED_SWITCHES_FIELD = "switchedFeatures";

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
            // over all four mocked steps at once, so a step dropped from the wiring fails here
            // rather than in play.
            try (var lunaSettingsMock = mockStatic(KmuLunaSettings.class);
                    var mapLayersMock = mockStatic(MapLayers.class);
                    var filterHealMock = mockStatic(FilterSelectionHeal.class);
                    var profilingMock = mockStatic(ProfilingCaptureInstaller.class);
                    var ratSettingsMock = mockStatic(RandomAssortmentOfThingsSettings.class)) {

                new KMU_ModPlugin().onApplicationLoad();

                // The profiling step is pinned as a call rather than as a binding, because what it
                // binds is a setting: the shipped level is off, so a launch that ran it leaves the
                // library exactly as silent as one that dropped it.
                profilingMock.verify(ProfilingCaptureInstaller::installAll);
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
    class SwitchedFeatures {

        @Test
        void appliesEverySwitchTheEntryPointDeclares() {
            // A load and a settings change both walk the one list, so a switch declared beside it
            // and left out of it is a feature the player can flip and see nothing happen until they
            // reload - and nothing else says so, the field still compiling and the setting still
            // reading. Gathered by type rather than by name, so a switch added later joins this
            // case by existing rather than by being remembered here.
            assertThat(readAppliedSwitches())
                .as(
                    "the switches %s applies against the ones it declares",
                    KMU_ModPlugin.class.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(readDeclaredSwitches());
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

    // The switches the entry point declares, read off its fields by type. Reflection rather than a
    // list written out here, which would be a third copy of the same set and could be the one that
    // is right while the wiring is wrong.
    private static List<Object> readDeclaredSwitches() {
        return Arrays
            .stream(KMU_ModPlugin.class.getDeclaredFields())
            .filter(field -> field.getType() == KmuToggledFeature.class)
            .map(KMU_ModPluginTest::readStaticFieldValue)
            .toList();
    }

    // The switches it applies, as the field both asks walk holds them. Their identity is what is
    // compared, so a list holding some other instance of the same feature fails as loudly as one
    // missing it.
    @SuppressWarnings("unchecked")
    private static List<Object> readAppliedSwitches() {
        return (List<Object>) readStaticFieldValue(findField(APPLIED_SWITCHES_FIELD));
    }

    private static Field findField(String name) {
        try {
            return KMU_ModPlugin.class.getDeclaredField(name);
        } catch (NoSuchFieldException failure) {
            // Surfaced rather than swallowed: the field being gone means the walk is looking for a
            // list that no longer exists, which would otherwise read as a plugin applying nothing.
            throw new IllegalStateException(
                "No " + APPLIED_SWITCHES_FIELD + " field on " + KMU_ModPlugin.class.getName(),
                failure);
        }
    }

    private static Object readStaticFieldValue(Field field) {
        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not read " + field.getName(), failure);
        }
    }

    // Everything the overlay is composed of, mocked together because every case here is about which
    // of them were called and which were not - a case holding only the ones it asserts on would let
    // an unmocked installer reach a real sector mock and answer for itself.
    private static final class MapLayerInstallerMocks implements AutoCloseable {

        private final MockedStatic<MapLayerInstallations> installationsMock =
            mockStatic(MapLayerInstallations.class);
        private final MockedStatic<MapSubstrateRefreshInstaller> substrateRefreshMock =
            mockStatic(MapSubstrateRefreshInstaller.class);
        private final MockedStatic<PoliticalMapInstaller> politicalMapMock =
            mockStatic(PoliticalMapInstaller.class);
        private final MockedStatic<MapSurfaceInstaller> surfaceMock =
            mockStatic(MapSurfaceInstaller.class);
        private final MockedStatic<SidebarInstaller> sidebarMock =
            mockStatic(SidebarInstaller.class);
        private final MockedStatic<MapHoverInstaller> hoverMock =
            mockStatic(MapHoverInstaller.class);
        private final MockedStatic<MapChromeInstaller> chromeMock =
            mockStatic(MapChromeInstaller.class);

        // Each half of the composition is listed once, with how often it was expected handed in.
        // A second copy of either list per expectation is how an entry comes to be asserted on in
        // one direction and forgotten in the other - which is the half that catches a stand-up
        // reached by a take-back.
        private void verifyEveryStandUpFor(SectorAPI sector, VerificationMode howOften) {

            installationsMock.verify(
                () -> MapLayerInstallations.installMachineryOn(sector), howOften);
            substrateRefreshMock.verify(
                () -> MapSubstrateRefreshInstaller.installAll(sector), howOften);
            politicalMapMock.verify(() -> PoliticalMapInstaller.installAll(sector), howOften);
            surfaceMock.verify(() -> MapSurfaceInstaller.installAll(sector), howOften);
            sidebarMock.verify(() -> SidebarInstaller.installAll(sector), howOften);
            hoverMock.verify(() -> MapHoverInstaller.installAll(sector), howOften);
            chromeMock.verify(() -> MapChromeInstaller.installAll(sector), howOften);
        }

        private void verifyEveryTakeBackFor(SectorAPI sector, VerificationMode howOften) {

            installationsMock.verify(
                () -> MapLayerInstallations.uninstallMachineryFrom(sector), howOften);
            substrateRefreshMock.verify(
                () -> MapSubstrateRefreshInstaller.uninstallAll(sector), howOften);
            politicalMapMock.verify(() -> PoliticalMapInstaller.uninstallAll(sector), howOften);
            surfaceMock.verify(() -> MapSurfaceInstaller.uninstallAll(sector), howOften);
            sidebarMock.verify(() -> SidebarInstaller.uninstallAll(sector), howOften);
            hoverMock.verify(() -> MapHoverInstaller.uninstallAll(sector), howOften);
            chromeMock.verify(() -> MapChromeInstaller.uninstallAll(sector), howOften);
        }

        @Override
        public void close() {

            chromeMock.close();
            hoverMock.close();
            sidebarMock.close();
            surfaceMock.close();
            politicalMapMock.close();
            substrateRefreshMock.close();
            installationsMock.close();
        }
    }
}
