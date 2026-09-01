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
 * untouched save resolves to, and the frozen keys each screen's pair of picks reads and writes. The generic
 * persisted-pick logic is {@link PersistedActiveLayerSelectionTest}'s, and the show-or-hide pick's is
 * {@link PersistedMapLayerVisibilityTest}'s; this pins only what the registry adds - the four frozen
 * per-screen keys, how {@link MapLayerRegistry#isActive} resolves which screen's pick is the live one,
 * and the show-or-hide answer it folds into that pick so a hidden screen resolves to nothing to draw.
 * The screens themselves are stand-in gates here: which concrete screens exist is the composition
 * root's business, and this pins only that the showing one wins.
 *
 * <p>A hide is acted on only for a screen that has a control able to reverse it, so every case posing
 * hidden layers poses that control too. The rule behind it, and what a screen without one reads, are
 * {@link ControlBackedMapLayerVisibilityTest}'s: these cases are about which screen an answer comes off.
 */
final class MapLayerRegistryTest {

    // The frozen sector-memory keys, pinned as literals so a rename - which would silently reset every
    // existing save - fails this test rather than shipping. One per screen, so the two picks stay
    // independent.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The same for each screen's show-or-hide pick, pinned for the same reason.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // A hide pace long enough that a reading taken straight after the flip is unmistakably part-way down
    // the ramp rather than past its end, which is the state the registry must still hand a renderer for.
    private static final float LONG_HIDE_FADE_SECONDS = 1_000f;

    // The two ends of that ramp, as a consumer reads them.
    private static final float FULLY_HIDDEN = 0f;
    private static final float FULLY_SHOWN = 1f;

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

        // The registry is static, so a screen left wired would outlive its test. Handing it a fresh
        // fake per test starts each from the intel screen closed rather than wherever a neighbour left
        // it - and keeps the live binding, which reaches into a running game, out of the suite.
        MapLayerRegistry.registerIntelScreen(intelScreenFake);

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
    class GetMapPicks {

        @Test
        void getMapPicksSelectsThroughTheFrozenMapKey() {

            MapLayerRegistry
                .getMapPicks()
                .layerSelection()
                .selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(MAP_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }

        @Test
        void getMapPicksHidesThroughTheFrozenMapKey() {
            // The pair's second key, pinned beside the first: both are this screen's, so a pair built
            // from one screen's tab and the other's hiding fails here rather than in play.
            MapLayerRegistry
                .getMapPicks()
                .layerVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(MAP_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }
    }

    @Nested
    class GetIntelPicks {

        @Test
        void getIntelPicksSelectsThroughTheFrozenIntelKey() {

            MapLayerRegistry
                .getIntelPicks()
                .layerSelection()
                .selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }

        @Test
        void getIntelPicksHidesThroughTheFrozenIntelKey() {

            MapLayerRegistry
                .getIntelPicks()
                .layerVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }
    }

    @Nested
    class AreLayersShownOnLiveScreen {

        @Test
        void areLayersShownOnLiveScreenAnswersFromTheShowingScreensPick() {
            // Per screen, so hiding on one leaves the other showing: the control sits on each screen's
            // own chrome, and a shared answer would empty a screen the player is not looking at.
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.areLayersShownOnLiveScreen())
                .isFalse();

            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.areLayersShownOnLiveScreen())
                .isTrue();
        }
    }

    @Nested
    class ResolveShownFadeOnLiveScreen {

        @Test
        void resolveShownFadeOnLiveScreenAnswersFromTheShowingScreensPick() {
            // What a pass multiplies into its alpha, and it follows the same screen the pick does - a
            // fade taken off the other screen would thin an overlay nobody asked to hide.
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.resolveShownFadeOnLiveScreen())
                .isEqualTo(FULLY_HIDDEN);

            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.resolveShownFadeOnLiveScreen())
                .isEqualTo(FULLY_SHOWN);
        }
    }

    @Nested
    class ResolveStoredLayerVisibilityOfLiveScreen {

        @Test
        void resolveStoredLayerVisibilityOfLiveScreenHandsBackTheShowingScreensOwnPick() {
            // The pick itself rather than a reading of it, since what asks is a control that both
            // shows it and moves it. Written through, it must move the screen that was up and leave
            // the other where it was - which is the whole of what a control on one screen's own
            // chrome may do.
            intelScreenFake.setIntelTabOpen(true);

            MapLayerRegistry
                .resolveStoredLayerVisibilityOfLiveScreen()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);

            assertThat(sectorMemoryFake.readStoredValue(MAP_LAYERS_SHOWN_KEY))
                .isNull();
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
    // The control that switched them off is posed with it, since the registry acts on a stored hide only
    // for a screen that has one - a case seeding the hide alone would be posing a screen whose layers are
    // still shown, and would pass for saying so.
    private void hideTheMapScreensLayers() {

        sectorMemoryFake.storeValue(MAP_LAYERS_SHOWN_KEY, false);
        recordAControlOnTheShowingScreen(false);
    }

    private void hideTheIntelScreensLayers() {

        sectorMemoryFake.storeValue(INTEL_LAYERS_SHOWN_KEY, false);
        recordAControlOnTheShowingScreen(true);
    }

    // Says a control stands on one of the two screens. The registry records against the screen that is
    // up, which is all a control on a screen's own chrome could ever mean, so the tab is put there for
    // the recording; every case that cares which screen is up sets it for itself afterwards.
    private void recordAControlOnTheShowingScreen(boolean isIntelTabOpen) {

        intelScreenFake.setIntelTabOpen(isIntelTabOpen);
        MapLayerRegistry.recordLayerControlAttachedOnLiveScreen();
    }

    // Puts the map screen part-way down its hide ramp. The flip is made while the pace still reads as
    // nothing, so the ramp is recorded as setting off from the settled shown end whatever an earlier case
    // left on the process-wide visibility; the pace is only then wound long, which leaves a reading taken
    // straight afterwards a long way short of the end.
    private void startHidingTheMapScreensLayers() {

        recordAControlOnTheShowingScreen(false);

        MapLayerRegistry
            .getMapPicks()
            .layerVisibility()
            .showLayers(false);

        mapLayerSettingsMock
            .when(KmuMapLayerSettings::getMapLayerHideFadeSeconds)
            .thenReturn(LONG_HIDE_FADE_SECONDS);
    }
}
