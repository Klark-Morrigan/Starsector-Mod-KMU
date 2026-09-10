package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.settings.KmuMapSidebarSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the one frame where taking a tab off the bar and dissolving its picture disagree, and has to.
 *
 * <p>Hiding the last tab that paints does two things at once: the layer is stood down on every sector,
 * so its listeners and its poll stop, and the screen switches off, so what was on it goes on being
 * drawn until the dissolve is over. Between those, the map surface asks a layer whose sector wiring
 * has already been taken back for a renderer - and it must still get one, or the picture the player
 * is watching thin would blink out instead.
 *
 * <p>It holds because a renderer lives on the sector's machinery and a layer's standing does not,
 * so standing down takes back what keeps a picture <em>current</em> and leaves what draws the one
 * already cut. Nothing about that is visible from either side alone: {@link MapLayerStandingsTest}
 * pins the standing and {@link ScreenDrawnLayerTest} the dissolve, and each is right while the pair
 * is wrong. So the two are driven together here, over the real registry, screens and machinery.
 */
final class HiddenLayerDissolveIntegrationTest {

    // The id the stored arrangement names the painting layer by, the store holding ids rather than
    // layers.
    private static final String PAINTING_LAYER_ID = "painting";

    // A hide pace long enough that a reading taken straight after the switch-off is unmistakably
    // part-way down the ramp rather than past its end, which is the frame this suite is about.
    private static final float LONG_HIDE_FADE_SECONDS = 1_000f;

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final MapLayerStanding standingMock = mock(MapLayerStanding.class);
    private final MapLayer paintingLayerMock = mock(MapLayer.class);

    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    // The hide ramp is paced by a shipped knob, and that read reaches LunaLib, which no test has.
    // Stood in for the class: its default answers a pace of nothing, which makes a dissolve a cut -
    // the settled state one case here is about - and the case that wants a reading from part-way down
    // winds the pace up for itself.
    private MockedStatic<KmuMapSidebarSettings> mapLayerSettingsMock;

    private SectorMapMachinery machinery;
    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void standTheOneLayerThatPaintsUpOnASector() {

        when(paintingLayerMock.getId()).thenReturn(PAINTING_LAYER_ID);
        when(paintingLayerMock.resolveStanding()).thenReturn(standingMock);

        // Offered as the default pick, so a save that has stored none opens on it and the screen has
        // a picture to catch without any case spelling a memory key.
        when(paintingLayerMock.isOfferedAsDefaultPick()).thenReturn(true);

        MapLayerRosters.replaceRosterWith(paintingLayerMock);
        MapLayerArrangements.arrangeBarWith(List.of(), List.of());
        MapLayerScreens.registerIntelScreen(intelScreenFake);

        sectorMemoryFake = new SectorMemoryFake();
        mapLayerSettingsMock = mockStatic(KmuMapSidebarSettings.class);

        when(paintingLayerMock.resolveRenderer(any())).thenReturn(layerRendererMock);

        machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);

        MapLayerStandings.applyArrangementTo(sectorMock);
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
    void restoreTheProcessWideState() {

        SectorMapMachineryIndex.disposeAllMachinery();
        MapLayerScreenControls.forgetDrawnLayers();
        MapLayerScreenControls.forgetControlsAttached();
        MapLayerArrangements.forgetTheArrangement();
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class TakingTheLastPaintingTabOffTheBar {

        @Test
        void standsTheLayerDownOnTheSectorItWasStandingOn() {

            drawAFrame();
            takeThePaintingTabOffTheBar();

            verify(standingMock).standLayerDownFrom(sectorMock);
        }

        @Test
        void goesOnDrawingTheStoodDownLayerUntilItsDissolveIsOver() {
            // The frame the two halves disagree on. The layer has no wiring left - nothing marks its
            // picture stale any more - and the surface still has to be handed the renderer that draws
            // what is already cut, or the map cuts out from under a sidebar still thinning beside it.
            drawAFrame();
            takeThePaintingTabOffTheBar();

            assertThat(MapLayerRegistry.resolveDrawnMapRenderer(machinery))
                .isSameAs(layerRendererMock);
        }

        @Test
        void drawsNothingOnceTheDissolveHasRunOut() {
            // The other end of the same reading: with no pace to run, the switch-off settles in the
            // frame it is made, and a stood-down layer is then asked for nothing at all.
            drawAFrame();
            hideThePaintingTabInTheArrangement();
            switchTheScreenOff();

            MapLayerStandings.applyArrangementWhereverInstalled();

            assertThat(MapLayerRegistry.resolveDrawnMapRenderer(machinery))
                .isNull();
        }

        @Test
        void standsTheLayerDownOnceRatherThanOnEveryFrameOfTheDissolve() {
            // The dissolve is drawn frame after frame while the layer stays hidden, and each of those
            // frames is a chance for a walk with no memory to take a layer down again - which on a
            // real pair means clearing listeners a later stand-up would have to rebuild.
            drawAFrame();
            takeThePaintingTabOffTheBar();

            MapLayerRegistry.resolveDrawnMapRenderer(machinery);
            MapLayerStandings.applyArrangementWhereverInstalled();

            verify(standingMock, times(1)).standLayerDownFrom(sectorMock);
        }
    }

    // One frame of the screen showing the layer, which is what leaves it a picture to dissolve. Every
    // control able to switch a screen off stands on that screen, so a switch-off in play always has
    // drawn frames behind it.
    private void drawAFrame() {

        standAControlOnTheMapScreen();

        MapLayerRegistry.getDrawnLayer();
    }

    // What the player's press does, in the order the running game does it: the arrangement is written
    // and the layers stood against it, and the screen carrying the last painting tab switches off.
    // Wound to a long pace afterwards, so the reading that follows is part-way down the ramp.
    private void takeThePaintingTabOffTheBar() {

        hideThePaintingTabInTheArrangement();
        switchTheScreenOff();

        MapLayerStandings.applyArrangementWhereverInstalled();

        mapLayerSettingsMock
            .when(KmuMapSidebarSettings::getMapLayerHideFadeSeconds)
            .thenReturn(LONG_HIDE_FADE_SECONDS);
    }

    private void hideThePaintingTabInTheArrangement() {
        MapLayerArrangements.arrangeBarWith(List.of(), List.of(PAINTING_LAYER_ID));
    }

    // What the standing heal owes a bar whose last painting tab has gone. Posed rather than driven,
    // the pass that writes it being the map chrome's.
    private void switchTheScreenOff() {

        MapLayerScreens
            .getMapPicks()
            .layerVisibility()
            .showLayers(false);
    }

    // A stored hide is acted on only for a screen that has a control able to reverse it, so the
    // control stands before anything is hidden. On the map screen, which is the one showing while the
    // intel tab is closed.
    private void standAControlOnTheMapScreen() {

        intelScreenFake.setIntelTabOpen(false);

        MapLayerScreenControls.standAControlOnTheShownScreen();
    }
}
