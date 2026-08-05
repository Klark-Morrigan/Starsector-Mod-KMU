package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.ui.map.probes.VanillaMapTooltip;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * <p>The map gate is pinned as the supplier it is, open and closed, which is the whole of what this
 * level can say about it: what the live read answers, and that the installed one is the union
 * covering starscape, belong to the seams themselves and to the install site's own test.
 */
final class MapLayerCellTooltipTest {

    private final MapHoverTooltip tooltipMock = mock(MapHoverTooltip.class);
    private final MapLayer tooltipLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    @BeforeEach
    void registerALayerShowingATooltip() {

        when(tooltipLayerMock.getId())
            .thenReturn("tooltip_layer");
        when(tooltipLayerMock.getMapRenderer())
            .thenReturn(layerRendererMock);

        when(layerRendererMock.resolveHoverTooltip())
            .thenReturn(Optional.of(tooltipMock));

        MapLayerRegistry.registerLayers(List.of(tooltipLayerMock), tooltipLayerMock);

        // The registry is static, so a screen left wired would outlive its test; a fresh fake starts
        // each test from the intel screen closed, which resolves reads to the map screen's pick.
        MapLayerRegistry.registerIntelScreen(intelScreenFake);
    }

    @AfterEach
    void restoreARegisteredLayer() {
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class RenderInUICoordsAboveUIAndTooltips {

        @BeforeEach
        void hoverACell() {
            // Every case here is about what the gates do around a live hover, so the hover is the
            // group's fixture rather than each test's opening lines.
            MapHoverState
                .getInstance()
                .publishHover(new MapHover("system", List.of("system")));
        }

        @AfterEach
        void clearTheHover() {
            // The hover state is a process-wide singleton, so one left published would reach the
            // next test as a hover it never asked for.
            MapHoverState.getInstance().clearHover();
        }

        @Test
        void standsAsideWhileTheVanillaMapIsDrawingItsOwnTooltip() {
            // The cursor is over a star, so the map is already naming it. Both boxes would otherwise
            // stack over one icon. Every gate above this one is open, so a dispatcher that skipped
            // the step-aside would reach the injected tooltip and draw.
            var vanillaMapTooltipMock = mock(VanillaMapTooltip.class);

            when(vanillaMapTooltipMock.isShowing())
                .thenReturn(true);

            runWithHoverSwitchesOn(() -> {
                new MapLayerCellTooltip(vanillaMapTooltipMock, () -> true)
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
            var vanillaMapTooltipMock = mock(VanillaMapTooltip.class);

            runWithHoverSwitchesOn(() -> {
                new MapLayerCellTooltip(vanillaMapTooltipMock, () -> false)
                    .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                verifyNoInteractions(vanillaMapTooltipMock);
                verifyNoInteractions(tooltipMock);
            });
        }

        @Test
        void drawsWhileAMapIsOnScreen() {
            // The open side of the same gate, all the way through to the injected box - the only
            // case that proves the dispatcher reaches its tooltip rather than that it declines to.
            // Which map states open the gate is not this test's to say: the read arrives as a
            // supplier, so "showing" is all this level can express. That the supplied read is the
            // union covering starscape is pinned against the real composition in
            // MapLayerCellTooltipGateIntegrationTest.
            var vanillaMapTooltipMock = mock(VanillaMapTooltip.class);
            var sectorMock = mock(SectorAPI.class);
            var systemMock = mock(StarSystemAPI.class);

            when(systemMock.getId())
                .thenReturn("system");
            when(sectorMock.getStarSystems())
                .thenReturn(List.of(systemMock));

            runWithHoverSwitchesOn(() -> {
                try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                    globalMock
                        .when(Global::getSector)
                        .thenReturn(sectorMock);

                    new MapLayerCellTooltip(vanillaMapTooltipMock, () -> true)
                        .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                    verify(tooltipMock)
                        .renderFor(sectorMock, systemMock);
                }
            });
        }

        // Runs body with both hover switches on - the settings tier above every gate this group is
        // about, and the one thing all three cases need identically. They are static reads, so they
        // can only be answered for the length of a scope, which is what makes this a wrapper rather
        // than a @BeforeEach like the hover.
        private void runWithHoverSwitchesOn(Runnable body) {
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                settingsMock
                    .when(KmuMapLayerSettings::getMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
                    .thenReturn(true);

                body.run();
            }
        }
    }

    @Nested
    class ShouldDrawTooltipFor {

        @Test
        void shouldDrawTooltipForIsTrueForAHoveredCell() {

            var hover = new MapHover("system", List.of("system"));

            assertThat(MapLayerCellTooltip.shouldDrawTooltipFor(hover))
                .isTrue();
        }

        @Test
        void shouldDrawTooltipForIsFalseWhenNothingIsHovered() {
            assertThat(MapLayerCellTooltip.shouldDrawTooltipFor(MapHover.NONE))
                .isFalse();
        }
    }

    @Nested
    class ResolveActiveTooltip {

        @Test
        void resolveActiveTooltipAnswersTheActiveLayersInjectedTooltip() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                // No sector means no stored pick, so the registry resolves to the registered default.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip())
                    .contains(tooltipMock);
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWhenTheActiveLayerInjectsNone() {
            // The claims view's shape: the layer paints, and simply has nothing to say about one cell.
            when(layerRendererMock.resolveHoverTooltip()).thenReturn(Optional.empty());
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip())
                    .isEmpty();
            }
        }

        @Test
        void resolveActiveTooltipAnswersTheActivePicksBoxWhileAnotherLayerWithholdsIts() {
            // The per-layer half of the hover switching: a layer whose own tooltip switch is off
            // offers no box, and that says nothing about the layer beside it - which is the case a
            // single shared switch could not express. The dispatcher does not know the difference
            // between a withheld box and a layer that has nothing to say, and must not.
            var silencedLayerMock = mock(MapLayer.class);
            var silencedRendererMock = mock(MapLayerRenderer.class);

            when(silencedLayerMock.getId())
                .thenReturn("silenced_layer");
            when(silencedLayerMock.getMapRenderer())
                .thenReturn(silencedRendererMock);

            when(silencedRendererMock.resolveHoverTooltip())
                .thenReturn(Optional.empty());

            MapLayerRegistry.registerLayers(
                List.of(silencedLayerMock, tooltipLayerMock),
                tooltipLayerMock);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip())
                    .contains(tooltipMock);
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWhenTheActiveLayerHasNoRenderer() {
            // The "show nothing" tab's shape: a registered layer that supplies no renderer, which must
            // stay an ordinary layer here rather than a named special case.
            var silentLayerMock = mock(MapLayer.class);

            when(silentLayerMock.getId())
                .thenReturn("silent");

            MapLayerRegistry.registerLayers(List.of(silentLayerMock), silentLayerMock);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip())
                    .isEmpty();
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWithoutAnActiveLayer() {
            // The pre-registration frame: the listener is installed on game load, so it can be asked
            // before any composition root has run rather than dereference a null pick.
            MapLayerRegistry.registerLayers(List.of(), null);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip())
                    .isEmpty();
            }
        }
    }
}
