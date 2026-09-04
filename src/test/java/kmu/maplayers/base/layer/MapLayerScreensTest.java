package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the two screens' state: the four frozen save keys each pair reads and writes, that neither
 * screen's pick moves the other's, and that every reading answers from the screen the player is
 * looking at. The generic persisted-pick logic is {@link PersistedActiveLayerSelectionTest}'s and
 * {@link PersistedMapLayerVisibilityTest}'s; what is added here is the keys and the live-screen rule.
 *
 * <p>The screens themselves are stand-in gates: which concrete screens exist is the composition root's
 * business, and this pins only that the showing one wins.
 *
 * <p>A hide is acted on only for a screen that has a control able to reverse it, so every case posing
 * hidden layers poses that control too. The rule behind it, and what a screen without one reads, are
 * {@link ControlBackedMapLayerVisibilityTest}'s.
 */
final class MapLayerScreensTest {

    // The frozen sector-memory keys, pinned as literals so a rename - which would silently reset every
    // existing save - fails this test rather than shipping. One per screen, so the two picks stay
    // independent.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The same for each screen's show-or-hide pick, pinned for the same reason.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // The two ends of the hide ramp, as a consumer reads them.
    private static final float FULLY_HIDDEN = 0f;
    private static final float FULLY_SHOWN = 1f;

    private final MapLayer layerMock = mock(MapLayer.class);

    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    // The hide ramp is paced by a shipped knob, and that read reaches LunaLib, which no test has. Stood
    // in for the class so no case can reach it: Mockito's own default answers a pace of nothing, which
    // makes each fade a cut and leaves these cases describing the settled answers they are about. The
    // ramp between them is PersistedMapLayerVisibilityTest's subject.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void wireAScreenGateAndASectorMemory() {

        when(layerMock.getId())
            .thenReturn("first");

        // The holder is static, so a screen left wired would outlive its test. Handing it a fresh fake
        // per test starts each from the intel screen closed rather than wherever a neighbour left it -
        // and keeps the live binding, which reaches into a running game, out of the suite.
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
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
    }

    @AfterEach
    void forgetTheControlsThisCaseStood() {
        // The two screens are held for the process and the latch saying one has a control never clears
        // itself, so a case that stood one would leave every later case acting on stored hides where a
        // run with no control reads shown - a difference that shows up as whichever case ran next.
        MapLayerScreenControls.forgetControlsAttached();
    }

    @Nested
    class GetMapPicks {

        @Test
        void getMapPicksSelectsThroughTheFrozenMapKey() {

            MapLayerScreens
                .getMapPicks()
                .layerSelection()
                .selectLayer(layerMock);

            assertThat(sectorMemoryFake.readStoredValue(MAP_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }

        @Test
        void getMapPicksHidesThroughTheFrozenMapKey() {
            // The pair's second key, pinned beside the first: both are this screen's, so a pair built
            // from one screen's tab and the other's hiding fails here rather than in play.
            MapLayerScreens
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

            MapLayerScreens
                .getIntelPicks()
                .layerSelection()
                .selectLayer(layerMock);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }

        @Test
        void getIntelPicksHidesThroughTheFrozenIntelKey() {

            MapLayerScreens
                .getIntelPicks()
                .layerVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }
    }

    @Nested
    class ResolveLivePicks {

        @Test
        void resolveLivePicksHandsBackTheShowingScreensPair() {
            // The pair rather than either half, and one resolution for both: everything composed on top
            // of this reads a tab and a show-or-hide state together, and two resolutions could answer
            // them for different screens.
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerScreens.resolveLivePicks())
                .isSameAs(MapLayerScreens.getIntelPicks());

            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerScreens.resolveLivePicks())
                .isSameAs(MapLayerScreens.getMapPicks());
        }

        @Test
        void resolveLivePicksCarriesTheShowingScreensOwnStoredPick() {
            // The pair is also what a control on a screen's own chrome is stood through, so it has to
            // carry the stored pick such a control shows and moves - and writing through it must move
            // the screen that was up and leave the other where it was, which is the whole of what a
            // control on one screen's chrome may do.
            intelScreenFake.setIntelTabOpen(true);

            MapLayerScreens
                .resolveLivePicks()
                .layerVisibility()
                .getStoredVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);

            assertThat(sectorMemoryFake.readStoredValue(MAP_LAYERS_SHOWN_KEY))
                .isNull();
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

            assertThat(MapLayerScreens.areLayersShownOnLiveScreen())
                .isFalse();

            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerScreens.areLayersShownOnLiveScreen())
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

            assertThat(MapLayerScreens.resolveShownFadeOnLiveScreen())
                .isEqualTo(FULLY_HIDDEN);

            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerScreens.resolveShownFadeOnLiveScreen())
                .isEqualTo(FULLY_SHOWN);
        }
    }

    // Poses a save in which the intel screen's layers were switched off and their ramp has long since
    // run out, together with the control that switched them off - a stored hide is acted on only for a
    // screen that has one, so a case seeding the hide alone would be posing a screen whose layers are
    // still shown, and would pass for saying so. Recorded against the screen that is up, which is all a
    // control on a screen's own chrome could mean, so the tab is put there for the recording; every
    // case that cares which screen is up sets it for itself afterwards.
    private void hideTheIntelScreensLayers() {

        sectorMemoryFake.storeValue(INTEL_LAYERS_SHOWN_KEY, false);
        intelScreenFake.setIntelTabOpen(true);

        MapLayerScreens
            .resolveLivePicks()
            .layerVisibility()
            .recordControlAttached();
    }
}
