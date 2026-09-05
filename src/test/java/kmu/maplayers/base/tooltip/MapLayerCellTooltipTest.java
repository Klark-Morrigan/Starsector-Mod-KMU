package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.MapHoverPermissionFixture;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevelState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the decisions the dispatcher makes for itself, all separable from the in-engine draw and from
 * the injected tooltip's own content: which of its shared gates let a frame through, that a box shows
 * for a hovered cell and not for an unhovered one, and that which box shows is whatever the active
 * pick's renderer injects. The last is pinned with a stand-in layer, since which concrete layers exist
 * is the composition root's business and the dispatcher must not know - a layer with no renderer and
 * no registered pick at all resolve alike to nothing to draw.
 *
 * <p>That the shared detail level reaches the box is pinned here too, over the stand-in: the level is
 * read from the holder the key writes and handed straight on, which is observable without an engine
 * even though the box drawing at that depth is not.
 *
 * <p>The frame gate is pinned over permissions posed against fixed screen reads, open and closed,
 * which is the whole of what this level can say about it: what the live reads answer, and that the
 * installed one is the union covering Starscape, belong to the seams themselves and to the install
 * site's own test.
 */
final class MapLayerCellTooltipTest {

    // The system under the cursor, named once so the hover, the sector's roster and the assertions
    // cannot drift onto three different ids.
    private static final String SYSTEM_ID = "system";

    private final MapHoverTooltip tooltipMock = mock(MapHoverTooltip.class);
    private final MapLayer tooltipLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    @BeforeEach
    void registerALayerShowingATooltip() {

        when(tooltipLayerMock.getId())
            .thenReturn("tooltip_layer");
        when(tooltipLayerMock.resolveRenderer(any()))
            .thenReturn(layerRendererMock);

        when(layerRendererMock.resolveHoverTooltip())
            .thenReturn(Optional.of(tooltipMock));

        MapLayerRosters.replaceRosterWith(tooltipLayerMock);

        // The registry is static, so a screen left wired would outlive its test; a fresh fake starts
        // each test from the intel screen closed, which resolves reads to the map screen's pick.
        MapLayerScreens.registerIntelScreen(intelScreenFake);
    }

    @AfterEach
    void restoreARegisteredLayer() {
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class RenderInUICoordsAboveUIAndTooltips {

        // The sector the box would be drawn over, and the system under the cursor in it. Fields
        // rather than locals because the hover is published against this sector's machinery in the
        // group's fixture, and a case standing the sector up for itself could hover a system of one
        // sector while the dispatcher read another's.
        private final SectorAPI sectorMock = mock(SectorAPI.class);
        private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

        @BeforeEach
        void hoverACell() {
            // Every case here is about what the gates do around a live hover, so the hover is the
            // group's fixture rather than each test's opening lines.
            when(systemMock.getId())
                .thenReturn(SYSTEM_ID);
            when(sectorMock.getStarSystems())
                .thenReturn(List.of(systemMock));

            MapHoverFixtures.hoverASystemOnAnInstalledSector(sectorMock, SYSTEM_ID);
        }

        @AfterEach
        void discardTheInstalledMachinery() {
            // The index is process-wide, so a sector left installed would reach the next test as a
            // hover - and a drawing - it never asked for.
            MapLayerInstallations.disposeEveryInstallation();
        }

        @AfterEach
        void dropTheDetailLevelBackToFactions() {
            // The level holder is a process-wide singleton for the same reason the hover is, so an
            // advance left standing would reach the next test as a detail level it never asked for.
            HoverTooltipDetailLevelState.getInstance().discardLevelFromPreviousSave();
        }

        @Test
        void standsAsideWhileTheVanillaMapIsDrawingItsOwnTooltip() {
            // The cursor is over a star, so the map is already naming it. Both boxes would otherwise
            // stack over one icon. Every gate above this one is open, so a dispatcher that skipped
            // the step-aside would reach the injected tooltip and draw.
            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);

            when(vanillaMapTooltipProbeMock.isTooltipShowing())
                .thenReturn(true);

            runWithHoverTooltipSwitchOn(() -> {
                new MapLayerCellTooltip(
                        vanillaMapTooltipProbeMock,
                        MapHoverPermissionFixture.buildPermissionOnAVanillaHost())
                    .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                verifyNoInteractions(tooltipMock);
            });
        }

        @Test
        void drawsNothingWhileNoMapIsOnScreen() {
            // The gate that lets the box onto the intel screen is also what keeps it off every screen
            // showing no map at all: this listener is called for the whole campaign UI, so with the
            // gate open and no map up the box would float over whatever screen the player is on.
            // Every other gate is open, and the vanilla probe is never even asked.
            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);

            runWithHoverTooltipSwitchOn(() -> {
                new MapLayerCellTooltip(
                        vanillaMapTooltipProbeMock,
                        MapHoverPermissionFixture.buildPermissionOffEveryMap())
                    .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                verifyNoInteractions(vanillaMapTooltipProbeMock);
                verifyNoInteractions(tooltipMock);
            });
        }

        @Test
        void drawsWhileAMapIsOnScreen() {
            // The open side of the same gate, all the way through to the injected box - the only
            // case that proves the dispatcher reaches its tooltip rather than that it declines to.
            // Which map states open the gate is not this test's to say: the permission is posed over
            // fixed screen reads, so "a vanilla host is up" is all this level can express. That the
            // live read behind it is the union covering Starscape is pinned against the real
            // composition in MapLayerCellTooltipGateIntegrationTest.
            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);

            runWithHoverTooltipSwitchOn(() -> {
                try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                    globalMock
                        .when(Global::getSector)
                        .thenReturn(sectorMock);

                    new MapLayerCellTooltip(
                            vanillaMapTooltipProbeMock,
                            MapHoverPermissionFixture.buildPermissionOnAVanillaHost())
                        .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                    verify(tooltipMock)
                        .renderFor(sectorMock, systemMock, HoverTooltipDetailLevel.FACTIONS);
                }
            });
        }

        @Test
        void drawsTheInjectedBoxAtTheLevelTheLastPressLeftBehind() {
            // The dispatcher's one read of the shared level, and the only case that can fail on it:
            // every other here draws at the resting level, so a dispatcher that handed over a
            // constant depth would pass all of them. The level is set on the shared holder rather
            // than injected, since the holder is what the live key writes and the dispatcher reads
            // it the same way it reads the hover.
            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);

            HoverTooltipDetailLevelState.getInstance()
                .moveToLevel(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            runWithHoverTooltipSwitchOn(() -> {
                try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                    globalMock
                        .when(Global::getSector)
                        .thenReturn(sectorMock);

                    new MapLayerCellTooltip(
                            vanillaMapTooltipProbeMock,
                            MapHoverPermissionFixture.buildPermissionOnAVanillaHost())
                        .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                    // The injected box itself, drawn to the advanced depth: a level never selects a
                    // box, so the one the layer supplied is the one that draws at every level.
                    verify(tooltipMock)
                        .renderFor(
                            sectorMock,
                            systemMock,
                            HoverTooltipDetailLevel.SYSTEM_COMPOSITION);
                }
            });
        }
    }
}
