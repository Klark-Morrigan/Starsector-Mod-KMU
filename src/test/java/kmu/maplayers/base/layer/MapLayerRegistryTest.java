package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the framework registry's contract with fake layers: the tab order it hands back, the pick an
 * untouched save resolves to, and the one answer everything that paints hangs off - which layer is in
 * play on the screen showing this frame, and the show-or-hide state folded into it so a hidden screen
 * resolves to nothing to draw.
 *
 * <p>Where those picks live, and the keys they ride in the save under, are {@link MapLayerScreensTest}'s;
 * the generic persisted-pick logic is {@link PersistedActiveLayerSelectionTest}'s and
 * {@link PersistedMapLayerVisibilityTest}'s. What is left here is the composition: the roster, the fold,
 * and that both follow the screen the player is looking at.
 *
 * <p>A hide is acted on only for a screen that has a control able to reverse it, so every case posing
 * hidden layers poses that control too. The rule behind it is
 * {@link ControlBackedMapLayerVisibilityTest}'s.
 */
final class MapLayerRegistryTest {

    // The frozen sector-memory keys the cases pose picks through. Pinned as literals in
    // MapLayerScreensTest, which is what a rename fails against; named here to seed a state, not to
    // assert one.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // A hide pace long enough that a reading taken straight after the flip is unmistakably part-way down
    // the ramp rather than past its end, which is the state the registry must still hand a renderer for.
    private static final float LONG_HIDE_FADE_SECONDS = 1_000f;

    // The machinery of the sector being drawn, which the registry passes through rather than
    // resolves. One for the class, so a case asserting it reached the layer is comparing against
    // the very object it handed in.
    private final MapLayerInstallation installation = new MapLayerInstallation(null);

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    // The hide ramp is paced by a shipped knob, and that read reaches LunaLib, which no test has. Stood
    // in for the class so every case names a pace: Mockito's own default answers a pace of nothing, which
    // makes each fade a cut and leaves these cases describing the settled answers they are about. The
    // ramp between them is PersistedMapLayerVisibilityTest's subject, and the one case here that wants a
    // reading from part-way down winds the pace up for itself.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void registerTwoFakeLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second layer is the default, so an untouched save resolves to it - the same shape the real
        // composition root uses (a non-leading default pick).
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), secondLayerMock);

        // The screens are static, so one left wired would outlive its test. Handing a fresh fake per
        // test starts each from the intel screen closed rather than wherever a neighbour left it - and
        // keeps the live binding, which reaches into a running game, out of the suite.
        MapLayerScreens.registerIntelScreen(intelScreenFake);

        sectorMemoryFake = new SectorMemoryFake();
    }

    @BeforeEach
    void stubTheHidePace() {
        mapLayerSettingsMock = mockStatic(KmuMapLayerSettings.class);
    }

    @AfterEach
    void releaseTheHidePace() {
        mapLayerSettingsMock.close();
    }

    @AfterEach
    void restoreARegisteredRoster() {
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @AfterEach
    void forgetTheControlsThisCaseStood() {
        // The screens are process-wide and the latch saying one of them has a control never clears
        // itself, so a case that stood one would leave every later case acting on stored hides where a
        // run with no control reads shown - a difference that shows up as whichever case happened to
        // run next.
        MapLayerScreenControls.forgetControlsAttached();
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
    }

    @Nested
    class GetLayers {

        @Test
        void getLayersReturnsTheRegisteredLayersInOrder() {

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);
        }
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerAnswersFromTheShowingScreensPick() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.getActiveLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void getActiveLayerIsNullOnceTheShowingScreensLayersHaveFadedOff() {
            // The one read hiding hangs off: every pass driven by the active pick already draws nothing
            // for a null, so this takes the overlay, the labels and the hover box off together.
            hideTheMapScreensLayers();

            assertThat(MapLayerRegistry.getActiveLayer())
                .isNull();
        }

        @Test
        void getActiveLayerAnswersThePickWhileOnlyTheOtherScreensLayersAreHidden() {
            // The per-screen half of the same gate: the intel screen hidden must leave the sector map
            // painting its own pick, which is the whole reason the two picks are kept apart.
            storeADifferentPickOnEachScreen();
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.getActiveLayer())
                .isSameAs(secondLayerMock);
        }

        @Test
        void getActiveLayerAnswersAShownScreenWithoutReadingTheHidePace() {
            // A screen with its layers on is answered off the stored pick alone. Worth pinning rather
            // than left as an accident of the order two conditions are written in: the fade is derived
            // from a clock and a settings read, and asking for it on every frame the layers are simply
            // on would put both on the map's hottest path to say what the pick has already said.
            assertThat(MapLayerRegistry.getActiveLayer())
                .isSameAs(secondLayerMock);

            mapLayerSettingsMock
                .verifyNoInteractions();
        }

        @Test
        void getActiveLayerIsNullBeforeAnyLayerIsRegistered() {
            // The map surface can be asked for a frame before the composition root has run, so the
            // registry has to answer "no pick" rather than leave a caller to find out by throwing.
            MapLayerRegistry.registerLayers(List.of(), null);
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.getActiveLayer())
                .isNull();
        }
    }

    @Nested
    class ResolveActiveMapRenderer {

        @Test
        void resolveActiveMapRendererAsksTheActiveLayerAboutTheInstallationItWasHanded() {
            // The roster is the process's while a renderer is one sector's, so the registry must
            // pass the installation through rather than resolve one of its own - a registry that
            // picked the running sector's would hand every surface the same renderer however many
            // sectors were being drawn. Pinned by stubbing that one installation and no other, so a
            // registry substituting its own would find nothing stubbed for it.
            var layerRendererMock = mock(MapLayerRenderer.class);

            when(secondLayerMock.resolveRenderer(installation))
                .thenReturn(layerRendererMock);

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isSameAs(layerRendererMock);
        }

        @Test
        void resolveActiveMapRendererIsNullWhenTheActivePickDrawsNothing() {
            // The switch-only tab, whose whole expression is a null renderer - and the pre-
            // registration frame below it, answered the same way so no pass driven by the active
            // pick needs a case for either. Stated rather than left to the stub's own default, or
            // the case would pass on a layer that was never asked at all.
            when(secondLayerMock.resolveRenderer(installation))
                .thenReturn(null);

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isNull();
        }

        @Test
        void resolveActiveMapRendererIsNullBeforeAnyLayerIsRegistered() {

            MapLayerRegistry.registerLayers(List.of(), null);
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isNull();
        }

        @Test
        void resolveActiveMapRendererStillDrawsPartWayThroughTheHide() {
            // What the fade is for: the pick already reads hidden, and the overlay must go on being
            // handed a renderer until the dissolve is over, or the picture would blink out from under
            // the sidebar that is still thinning beside it.
            var layerRendererMock = mock(MapLayerRenderer.class);

            when(secondLayerMock.resolveRenderer(installation))
                .thenReturn(layerRendererMock);

            startHidingTheMapScreensLayers();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isSameAs(layerRendererMock);
        }
    }

    @Nested
    class IsActive {

        @Test
        void isActiveIsTrueOnlyForTheResolvedActivePick() {

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isFalse();
        }

        @Test
        void isActiveAnswersFromTheIntelPickWhileTheIntelScreenIsUp() {
            // The bug this guards: each screen keeps its own tab, so an overlay reading one fixed
            // screen's pick painted the sector map's choice onto the intel screen - No Layer on the
            // intel tab could not turn it off there.
            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isFalse();
        }

        @Test
        void isActiveAnswersFromTheMapPickWhileTheIntelScreenIsNotUp() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isFalse();
        }

        @Test
        void isActiveIsFalseForEveryLayerOnceTheScreensLayersHaveFadedOff() {
            // A hidden screen has no layer in play at all, which is what stands each layer's own state
            // down without a read of its own - the default pick included, since it is the one a layer
            // would otherwise go on thinking it held.
            hideTheMapScreensLayers();

            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isFalse();
            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isFalse();
        }
    }

    // Puts the two screens on different tabs, which is what lets a case tell which key an answer came
    // off: with one pick on both keys, a registry reading the wrong one would still answer correctly.
    private void storeADifferentPickOnEachScreen() {

        sectorMemoryFake.storeValue(MAP_ACTIVE_LAYER_KEY, "second");
        sectorMemoryFake.storeValue(INTEL_ACTIVE_LAYER_KEY, "first");
    }

    // Poses a save in which one screen's layers were switched off and their ramp has long since run out.
    // Seeded rather than flipped, so the reading is the settled one whatever the pace reads.
    //
    // The control that switched them off is posed with it, since a stored hide is acted on only for a
    // screen that has one - a case seeding the hide alone would be posing a screen whose layers are
    // still shown, and would pass for saying so.
    private void hideTheMapScreensLayers() {

        sectorMemoryFake.storeValue(MAP_LAYERS_SHOWN_KEY, false);
        recordAControlOnTheShowingScreen(false);
    }

    private void hideTheIntelScreensLayers() {

        sectorMemoryFake.storeValue(INTEL_LAYERS_SHOWN_KEY, false);
        recordAControlOnTheShowingScreen(true);
    }

    // Says a control stands on one of the two screens. It is recorded against the screen that is up,
    // which is all a control on a screen's own chrome could ever mean, so the tab is put there for the
    // recording; every case that cares which screen is up sets it for itself afterwards.
    private void recordAControlOnTheShowingScreen(boolean isIntelTabOpen) {

        intelScreenFake.setIntelTabOpen(isIntelTabOpen);

        MapLayerScreens
            .resolveLayerControlOfLiveScreen()
            .recordControlAttached();
    }

    // Puts the map screen part-way down its hide ramp. The flip is made while the pace still reads as
    // nothing, so the ramp is recorded as setting off from the settled shown end whatever an earlier case
    // left on the process-wide visibility; the pace is only then wound long, which leaves a reading taken
    // straight afterwards a long way short of the end.
    private void startHidingTheMapScreensLayers() {

        recordAControlOnTheShowingScreen(false);

        MapLayerScreens
            .getMapPicks()
            .layerVisibility()
            .showLayers(false);

        mapLayerSettingsMock
            .when(KmuMapLayerSettings::getMapLayerHideFadeSeconds)
            .thenReturn(LONG_HIDE_FADE_SECONDS);
    }
}
