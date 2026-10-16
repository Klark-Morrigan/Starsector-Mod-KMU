package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.compatibility.CompatibilityFailures;
import kmlib.starsector.startup.ToggledFeature;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;
import kmlib.testfixtures.starsector.compatibility.CompatibilityFailureFixture;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.conditions.ui.context.MarketUiContextInstaller;
import kmu.diagnostics.ProfilingCaptureInstaller;
import kmu.diagnostics.ReflectionTracingInstaller;
import kmu.maplayers.MapLayers;
import kmu.maplayers.base.chrome.MapChromeInstaller;
import kmu.maplayers.base.layer.MapLayerStandings;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapSubstrateRefreshInstaller;
import kmu.maplayers.base.render.MapSurfaceInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarInstaller;
import kmu.maplayers.base.tooltip.MapHoverInstaller;
import kmu.maplayers.base.visibility.colonies.ColonySightingInstaller;
import kmu.mods.rat.RandomAssortmentOfThingsCompatibilityInstaller;
import kmu.mods.rat.RandomAssortmentOfThingsSettings;
import kmu.settings.KmuFeatureSettings;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapHoverSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.verification.VerificationMode;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs, and
 * the composition it names - which steps a launch is made of, which switches it applies, which
 * installers the map layers are made of and in what order, and that switching them off reaches every
 * one of them. A load and a settings change are pinned too, being the two places the switches are
 * asked: a load applies each whatever it read before, and a change acts only on the one that moved.
 *
 * <p>Every step a launch runs names a class this mod owns, which is what makes the list assertable
 * at all: LunaLib is on no test classpath, so a step reaching it directly would fail to link, and
 * the wiring guard catching that would leave a case passing over a step that never ran. The one
 * binding that must touch LunaLib is held behind {@code RandomAssortmentOfThingsSettings}, which is
 * stood in for here like the rest.
 *
 * <p>What each installer registers is pinned beside that installer, and when a switch is worth
 * acting on is pinned on {@link ToggledFeature}. Neither is re-asserted here: this suite is
 * about the list and which of the two asks each entry point makes, not about what the entries do.
 */
class KMU_ModPluginTests {

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

        // The session's record is the process's own and outlives a case, so a case reading it reads
        // its own failure alone only when it starts and leaves the record empty.
        @BeforeEach
        void drainTheSessionRecordBefore() {

            CompatibilityFailureFixture.drainSessionRecord();
        }

        @AfterEach
        void clearSettingsAndDrainTheSessionRecord() {

            StarsectorSettingsFake.clearSettings();
            CompatibilityFailureFixture.drainSessionRecord();
        }

        @Test
        void standsUpEveryStepALaunchIsMadeOf() {
            // The list is the whole subject: a step nobody named is a feature that silently never
            // runs, which is how a settings change came to leave a stale spotlight standing. Held
            // over every mocked step at once, so a step dropped from the wiring fails here rather
            // than in play.
            try (var lunaSettingsMock = mockStatic(KmuLunaSettings.class);
                    var mapLayersMock = mockStatic(MapLayers.class);
                    var profilingMock = mockStatic(ProfilingCaptureInstaller.class);
                    var reflectionTracingMock = mockStatic(ReflectionTracingInstaller.class);
                    var ratSettingsMock = mockStatic(RandomAssortmentOfThingsSettings.class)) {

                new KMU_ModPlugin().onApplicationLoad();

                // The profiling step is pinned as a call rather than as a binding, because what it
                // binds is a setting: the shipped level is off, so a launch that ran it leaves the
                // library exactly as silent as one that dropped it.
                profilingMock.verify(ProfilingCaptureInstaller::installAll);

                // Pinned as a call for the profiling step's reason: what it stands up is a listener
                // on a switch that ships off, so a launch that dropped it looks identical from
                // outside until the player turns the traces on and is met with silence.
                reflectionTracingMock.verify(ReflectionTracingInstaller::installAll);
                lunaSettingsMock.verify(KmuLunaSettings::installBindings);
                mapLayersMock.verify(MapLayers::registerAll);

                // The two settings listeners are pinned on the announcement each answers rather
                // than on the reaction it carries: which mod's settings a listener is bound to is
                // the part a call site can get wrong, and a method reference has no identity to
                // assert on anyway.
                lunaSettingsMock.verify(() -> KmuLunaSettings.runOnSettingsChange(any()));
                ratSettingsMock.verify(
                    () -> RandomAssortmentOfThingsSettings.runOnSettingsChange(any()));
            }
        }

        @Test
        void reportsALunaLibBindingThatRefusedToInstallToThePlayer() {
            // The LunaLib steps take the guard that reports, where every other step only logs.
            StarsectorSettingsFake.installSettings((category, key) -> "the sentence for " + key);

            try (var lunaSettingsMock = mockStatic(KmuLunaSettings.class);
                    var mapLayersMock = mockStatic(MapLayers.class);
                    var profilingMock = mockStatic(ProfilingCaptureInstaller.class);
                    var reflectionTracingMock = mockStatic(ReflectionTracingInstaller.class);
                    var ratSettingsMock = mockStatic(RandomAssortmentOfThingsSettings.class)) {

                lunaSettingsMock.when(KmuLunaSettings::installBindings)
                    .thenThrow(new NoSuchMethodError("LunaLib moved what the bindings call"));
                // The report is composed from the bindings' own description, which a class stood in
                // for whole would answer with nothing.
                lunaSettingsMock.when(KmuLunaSettings::describeLunaLibIntegration)
                    .thenCallRealMethod();

                new KMU_ModPlugin().onApplicationLoad();
            }

            var failure = CompatibilityFailureFixture.takeNextBindingFailure(CompatibilityFailures.SESSION_RECORD);

            assertThat(failure.subject().name())
                .isEqualTo("LunaLib");
            assertThat(failure.consumer().consumerKey())
                .isEqualTo("kmu:lunalib-settings");
        }
    }

    @Nested
    class OnGameLoad {

        private final SectorAPI loadedSectorMock = mock(SectorAPI.class);

        @Test
        void discardsThePreviousSavesMachineryBeforeStandingTheLayersUp() {
            // No other seam is told that a sector went away, so a machinery left standing past this
            // point would go on drawing the previous save over the loaded one.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(true);

                new KMU_ModPlugin().onGameLoad(false);

                var machineryOrder = inOrder(SectorMapMachineryIndex.class);

                machineryOrder.verify(
                    installers.machineryIndexMock,
                    SectorMapMachineryIndex::disposeAllMachinery);
                machineryOrder.verify(
                    installers.machineryIndexMock,
                    () -> SectorMapMachineryIndex.installMachineryOn(loadedSectorMock));
            }
        }

        @Test
        void discardsThePreviousSavesMachineryWithTheLayersSwitchedOff() {
            // A load that finds the overlay off has as much of the previous sector's to discard as
            // one that finds it on, which is why the discard runs ahead of the switch.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(false);

                new KMU_ModPlugin().onGameLoad(false);

                installers.machineryIndexMock.verify(SectorMapMachineryIndex::disposeAllMachinery);
                installers.verifyEveryStandUpFor(loadedSectorMock, never());
            }
        }

        @Test
        void keepsRecordingColonySightingsWithTheLayersSwitchedOff() {
            // A register left unwritten while the layers are off would have every gated colony
            // unobserved again once they are switched back on.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(false);

                new KMU_ModPlugin().onGameLoad(false);

                switches.colonySightingsMock.verify(() -> ColonySightingInstaller.installAll(loadedSectorMock));
            }
        }

        @Test
        void bringsEveryFeatureIntoLineWithItsSwitchOnTheLoadedSector() {
            // The compatibility mode reads as off throughout: the minimap it adapts to belongs to a
            // mod no test classpath carries.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(true);
                switches.switchMarketConditionManager(true);

                new KMU_ModPlugin().onGameLoad(false);

                installers.verifyEveryStandUpFor(loadedSectorMock, times(1));
                switches.marketUiContextMock.verify(() -> MarketUiContextInstaller.installAll(loadedSectorMock));
                switches.compatibilityMock.verify(
                    () -> RandomAssortmentOfThingsCompatibilityInstaller.uninstallAll(loadedSectorMock));
            }
        }

        @Test
        void standsTheLayersUpAgainWhereTheSwitchHasNotMovedSinceTheLastLoad() {
            // The loaded sector carries none of the wiring the previous one had, so a switch reading
            // as it did before is no reason to leave it unwired.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(true);

                new KMU_ModPlugin().onGameLoad(false);
                new KMU_ModPlugin().onGameLoad(false);

                installers.verifyEveryStandUpFor(loadedSectorMock, times(2));
            }
        }
    }

    @Nested
    class ApplySwitchedFeatures {

        private final SectorAPI loadedSectorMock = mock(SectorAPI.class);

        @Test
        void standsUpOnlyTheFeatureWhoseSwitchMoved() {
            // LunaLib announces that the settings changed rather than which one did, so a feature
            // acting on every announcement would be torn down and rebuilt for an unrelated slider.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(true);
                switches.switchMarketConditionManager(false);

                new KMU_ModPlugin().onGameLoad(false);

                installers.forgetRecordedCalls();
                switches.forgetRecordedCalls();
                switches.switchMarketConditionManager(true);

                KMU_ModPlugin.applySwitchedFeatures();

                switches.marketUiContextMock.verify(() -> MarketUiContextInstaller.installAll(loadedSectorMock));
                installers.verifyEveryStandUpFor(loadedSectorMock, never());
                installers.verifyEveryTakeBackFor(loadedSectorMock, never());
                switches.compatibilityMock.verifyNoInteractions();
            }
        }

        @Test
        void takesBackAFeatureSwitchedOffSinceTheLoad() {
            // Where the player flips it, not at the next load: the switch is answered on the sector
            // already wired.
            try (var installers = new MapLayerInstallerMocks();
                    var switches = new FeatureSwitchSeams(loadedSectorMock)) {

                switches.switchMapLayers(true);

                new KMU_ModPlugin().onGameLoad(false);

                installers.forgetRecordedCalls();
                switches.switchMapLayers(false);

                KMU_ModPlugin.applySwitchedFeatures();

                installers.verifyEveryTakeBackFor(loadedSectorMock, times(1));
                installers.verifyEveryStandUpFor(loadedSectorMock, never());
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

        @Test
        void standsTheInstallersUpInTheOrderEachNeedsThem() {
            // Everything derives from the machinery, and the political map's save heal runs inside
            // the standings and repairs what the render surfaces then read.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                KMU_ModPlugin.installMapLayers(sectorMock);

                installers.verifyStandUpOrderFor(sectorMock);
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

        @Test
        void takesThemBackInTheReverseOfTheirStandUpOrder() {
            // The machinery goes last: every other installer is taken back through the state it
            // holds, so releasing it first would leave them undoing their work against nothing.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                KMU_ModPlugin.uninstallMapLayers(sectorMock);

                installers.verifyTakeBackOrderFor(sectorMock);
            }
        }
    }

    // The switches the entry point declares, read off its fields by type. Reflection rather than a
    // list written out here, which would be a third copy of the same set and could be the one that
    // is right while the wiring is wrong.
    private static List<Object> readDeclaredSwitches() {

        return Arrays
            .stream(KMU_ModPlugin.class.getDeclaredFields())
            .filter(field -> field.getType() == ToggledFeature.class)
            .map(KMU_ModPluginTests::readStaticFieldValue)
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

        } catch (NoSuchFieldException noSuchFieldException) {

            // Surfaced rather than swallowed: the field being gone means the walk is looking for a
            // list that no longer exists, which would otherwise read as a plugin applying nothing.
            throw new IllegalStateException(
                "No " + APPLIED_SWITCHES_FIELD + " field on " + KMU_ModPlugin.class.getName(),
                noSuchFieldException);
        }
    }

    private static Object readStaticFieldValue(Field field) {

        try {

            field.setAccessible(true);
            return field.get(null);

        } catch (IllegalAccessException illegalAccessException) {

            throw new IllegalStateException(
                "Could not read " + field.getName(),
                illegalAccessException);
        }
    }

    // The game and the switches a load and a settings change read, stood in together with the features
    // they decide on. The switches are live LunaLib reads, which no test classpath can reach.
    private static final class FeatureSwitchSeams implements AutoCloseable {

        // First, since every seam below is opened into it.
        private final StaticSeams staticSeams = new StaticSeams();

        private final MockedStatic<ColonySightingInstaller> colonySightingsMock =
            staticSeams.openSeam(ColonySightingInstaller.class);
        private final MockedStatic<RandomAssortmentOfThingsCompatibilityInstaller> compatibilityMock =
            staticSeams.openSeam(RandomAssortmentOfThingsCompatibilityInstaller.class);
        private final MockedStatic<KmuFeatureSettings> featureSettingsMock =
            staticSeams.openSeam(KmuFeatureSettings.class);
        private final MockedStatic<Global> globalMock =
            staticSeams.holdSeam(StubbedGlobalLogger.openGlobalAnsweringLoggers());
        private final MockedStatic<MarketUiContextInstaller> marketUiContextMock =
            staticSeams.openSeam(MarketUiContextInstaller.class);

        private FeatureSwitchSeams(SectorAPI loadedSector) {

            // Left answering false, so the compatibility mode reads as off and never asks for the
            // minimap of a mod that is not installed.
            staticSeams.openSeam(KmuMapHoverSettings.class);

            globalMock.when(Global::getSector)
                .thenReturn(loadedSector);
        }

        @Override
        public void close() {

            staticSeams.closeEverySeam();
        }

        private void forgetRecordedCalls() {

            colonySightingsMock.clearInvocations();
            marketUiContextMock.clearInvocations();
            compatibilityMock.clearInvocations();
        }

        private void switchMapLayers(boolean isSwitchedOn) {

            featureSettingsMock.when(KmuFeatureSettings::areMapLayersEnabled)
                .thenReturn(isSwitchedOn);
        }

        private void switchMarketConditionManager(boolean isSwitchedOn) {

            featureSettingsMock.when(KmuFeatureSettings::isMarketConditionManagerEnabled)
                .thenReturn(isSwitchedOn);
        }
    }

    // Everything the overlay is composed of, mocked together because every case here is about which
    // of them were called and which were not - a case holding only the ones it asserts on would let
    // an unmocked installer reach a real sector mock and answer for itself.
    private static final class MapLayerInstallerMocks implements AutoCloseable {

        private final MockedStatic<SectorMapMachineryIndex> machineryIndexMock =
            mockStatic(SectorMapMachineryIndex.class);
        private final MockedStatic<MapSubstrateRefreshInstaller> substrateRefreshMock =
            mockStatic(MapSubstrateRefreshInstaller.class);
        private final MockedStatic<MapLayerStandings> standingsMock =
            mockStatic(MapLayerStandings.class);
        private final MockedStatic<MapSurfaceInstaller> surfaceMock =
            mockStatic(MapSurfaceInstaller.class);
        private final MockedStatic<SidebarInstaller> sidebarMock =
            mockStatic(SidebarInstaller.class);
        private final MockedStatic<MapHoverInstaller> hoverMock =
            mockStatic(MapHoverInstaller.class);
        private final MockedStatic<MapChromeInstaller> chromeMock =
            mockStatic(MapChromeInstaller.class);

        // Drops what the installers recorded so far, for a case asking what a later call did on its own.
        private void forgetRecordedCalls() {

            machineryIndexMock.clearInvocations();
            substrateRefreshMock.clearInvocations();
            standingsMock.clearInvocations();
            surfaceMock.clearInvocations();
            sidebarMock.clearInvocations();
            hoverMock.clearInvocations();
            chromeMock.clearInvocations();
        }

        // Opened over the stood-in classes rather than the handles, since a static stand-in records its
        // calls against the class itself.
        private InOrder openCallOrder() {

            return inOrder(
                SectorMapMachineryIndex.class,
                MapSubstrateRefreshInstaller.class,
                MapLayerStandings.class,
                MapSurfaceInstaller.class,
                SidebarInstaller.class,
                MapHoverInstaller.class,
                MapChromeInstaller.class);
        }

        // Each half of the composition is listed once, with how often it was expected handed in.
        // A second copy of either list per expectation is how an entry comes to be asserted on in
        // one direction and forgotten in the other - which is the half that catches a stand-up
        // reached by a take-back.
        private void verifyEveryStandUpFor(SectorAPI sector, VerificationMode howOften) {

            machineryIndexMock.verify(
                () -> SectorMapMachineryIndex.installMachineryOn(sector), howOften);

            substrateRefreshMock.verify(
                () -> MapSubstrateRefreshInstaller.installAll(sector), howOften);

            standingsMock.verify(() -> MapLayerStandings.applyArrangementTo(sector), howOften);
            surfaceMock.verify(() -> MapSurfaceInstaller.installAll(sector), howOften);
            sidebarMock.verify(() -> SidebarInstaller.installAll(sector), howOften);
            hoverMock.verify(() -> MapHoverInstaller.installAll(sector), howOften);
            chromeMock.verify(() -> MapChromeInstaller.installAll(sector), howOften);
        }

        private void verifyEveryTakeBackFor(SectorAPI sector, VerificationMode howOften) {

            machineryIndexMock.verify(
                () -> SectorMapMachineryIndex.uninstallMachineryFrom(sector), howOften);

            substrateRefreshMock.verify(
                () -> MapSubstrateRefreshInstaller.uninstallAll(sector), howOften);

            standingsMock.verify(
                () -> MapLayerStandings.standEveryLayerDownFrom(sector), howOften);

            surfaceMock.verify(() -> MapSurfaceInstaller.uninstallAll(sector), howOften);
            sidebarMock.verify(() -> SidebarInstaller.uninstallAll(sector), howOften);
            hoverMock.verify(() -> MapHoverInstaller.uninstallAll(sector), howOften);
            chromeMock.verify(() -> MapChromeInstaller.uninstallAll(sector), howOften);
        }

        private void verifyStandUpOrderFor(SectorAPI sector) {

            var standUpOrder = openCallOrder();

            standUpOrder.verify(machineryIndexMock, () -> SectorMapMachineryIndex.installMachineryOn(sector));
            standUpOrder.verify(substrateRefreshMock, () -> MapSubstrateRefreshInstaller.installAll(sector));
            standUpOrder.verify(standingsMock, () -> MapLayerStandings.applyArrangementTo(sector));
            standUpOrder.verify(surfaceMock, () -> MapSurfaceInstaller.installAll(sector));
            standUpOrder.verify(sidebarMock, () -> SidebarInstaller.installAll(sector));
            standUpOrder.verify(hoverMock, () -> MapHoverInstaller.installAll(sector));
            standUpOrder.verify(chromeMock, () -> MapChromeInstaller.installAll(sector));
        }

        private void verifyTakeBackOrderFor(SectorAPI sector) {

            var takeBackOrder = openCallOrder();

            takeBackOrder.verify(chromeMock, () -> MapChromeInstaller.uninstallAll(sector));
            takeBackOrder.verify(hoverMock, () -> MapHoverInstaller.uninstallAll(sector));
            takeBackOrder.verify(sidebarMock, () -> SidebarInstaller.uninstallAll(sector));
            takeBackOrder.verify(surfaceMock, () -> MapSurfaceInstaller.uninstallAll(sector));
            takeBackOrder.verify(standingsMock, () -> MapLayerStandings.standEveryLayerDownFrom(sector));
            takeBackOrder.verify(substrateRefreshMock, () -> MapSubstrateRefreshInstaller.uninstallAll(sector));
            takeBackOrder.verify(machineryIndexMock, () -> SectorMapMachineryIndex.uninstallMachineryFrom(sector));
        }

        @Override
        public void close() {

            chromeMock.close();
            hoverMock.close();
            sidebarMock.close();
            surfaceMock.close();
            standingsMock.close();
            substrateRefreshMock.close();
            machineryIndexMock.close();
        }
    }
}
