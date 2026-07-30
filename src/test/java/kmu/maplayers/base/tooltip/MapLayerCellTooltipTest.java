package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.PoliticalMapHover;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.render.MapLayerRenderer;

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
import static org.mockito.Mockito.when;

/**
 * Pins the two decisions the dispatcher makes for itself, both separable from the in-engine draw, the
 * live vanilla-tooltip step-aside, and the injected tooltip's own content: a box shows for a hovered
 * cell and not for an unhovered one, and which box shows is whatever the active pick's renderer
 * injects. The second is pinned with a stand-in layer, since which concrete layers exist is the
 * composition root's business and the dispatcher must not know - a layer with no renderer and no
 * registered pick at all resolve alike to nothing to draw.
 */
final class MapLayerCellTooltipTest {
    private final MapHoverTooltip tooltipMock = mock(MapHoverTooltip.class);
    private final MapLayer tooltipLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    @BeforeEach
    void registerALayerShowingATooltip() {
        when(tooltipLayerMock.getId()).thenReturn("tooltip_layer");
        when(tooltipLayerMock.getMapRenderer()).thenReturn(layerRendererMock);
        when(layerRendererMock.resolveHoverTooltip()).thenReturn(Optional.of(tooltipMock));
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
    class ShouldDrawTooltipFor {

        @Test
        void shouldDrawTooltipForIsTrueForAHoveredCell() {
            var hover = new PoliticalMapHover("system", List.of("system"));

            assertThat(MapLayerCellTooltip.shouldDrawTooltipFor(hover)).isTrue();
        }

        @Test
        void shouldDrawTooltipForIsFalseWhenNothingIsHovered() {
            assertThat(MapLayerCellTooltip.shouldDrawTooltipFor(PoliticalMapHover.NONE)).isFalse();
        }
    }

    @Nested
    class ResolveActiveTooltip {

        @Test
        void resolveActiveTooltipAnswersTheActiveLayersInjectedTooltip() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                // No sector means no stored pick, so the registry resolves to the registered default.
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip()).contains(tooltipMock);
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWhenTheActiveLayerInjectsNone() {
            // The claims view's shape: the layer paints, and simply has nothing to say about one cell.
            when(layerRendererMock.resolveHoverTooltip()).thenReturn(Optional.empty());
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip()).isEmpty();
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWhenTheActiveLayerHasNoRenderer() {
            // The "show nothing" tab's shape: a registered layer that supplies no renderer, which must
            // stay an ordinary layer here rather than a named special case.
            var silentLayerMock = mock(MapLayer.class);
            when(silentLayerMock.getId()).thenReturn("silent");
            MapLayerRegistry.registerLayers(List.of(silentLayerMock), silentLayerMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip()).isEmpty();
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWithoutAnActiveLayer() {
            // The pre-registration frame: the listener is installed on game load, so it can be asked
            // before any composition root has run rather than dereference a null pick.
            MapLayerRegistry.registerLayers(List.of(), null);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerCellTooltip.resolveActiveTooltip()).isEmpty();
            }
        }
    }
}
