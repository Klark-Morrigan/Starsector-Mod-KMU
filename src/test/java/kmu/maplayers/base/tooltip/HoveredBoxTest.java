package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.render.MapLayerRenderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the chain that answers what the cursor is over, which two passes read and must agree on: the
 * one that draws the box and the one that claims the key switching it. Every way the chain can come up
 * empty is a case here - nothing hovered, no layer showing a box for it, no live sector, an id that no
 * longer names a system - because each is a frame on which the key must fall through to vanilla rather
 * than flip a mode over a box that is not there.
 *
 * <p>Which box a layer injects is pinned with a stand-in layer, since which concrete layers exist is
 * the composition root's business and this chain must not know: a layer with no renderer, one that
 * injects no box, and no registered pick at all resolve alike to nothing to draw.
 */
final class HoveredBoxTest {

    private static final String SYSTEM_ID = "system";

    private final MapHoverTooltip tooltipMock = mock(MapHoverTooltip.class);
    private final MapLayer tooltipLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    @BeforeEach
    void registerALayerShowingATooltip() {

        when(tooltipLayerMock.getId())
            .thenReturn("tooltip_layer");
        when(tooltipLayerMock.resolveRenderer(any()))
            .thenReturn(layerRendererMock);

        when(layerRendererMock.resolveHoverTooltip())
            .thenReturn(Optional.of(tooltipMock));

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));

        MapLayerRegistry.registerLayers(List.of(tooltipLayerMock), tooltipLayerMock);

        // The registry is static, so a screen left wired would outlive its test; a fresh fake starts
        // each test from the intel screen closed, which resolves reads to the map screen's pick.
        MapLayerRegistry.registerIntelScreen(new IntelScreenViewFake());
    }

    @AfterEach
    void restoreTheSharedState() {
        MapLayerRosters.restoreNonEmptyRoster();
        MapHoverState.resolveLiveSectorHoverState().clearHover();
    }

    @Nested
    class ResolveHoveredBox {

        @Test
        void resolveHoveredBoxBindsTheInjectedBoxToWhatTheCursorIsOver() {
            // What both passes are after: the box, and the two things it would be drawn for. Bound
            // together so neither pass can pair one frame's box with another frame's system.
            hoverTheSystem();

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(HoveredBox.resolveHoveredBox())
                    .contains(new HoveredBox(tooltipMock, sectorMock, systemMock));
            }
        }

        @Test
        void resolveHoveredBoxIsEmptyWhenNothingIsHovered() {
            // No cell under the cursor, so there is nothing a box could be about.
            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(HoveredBox.resolveHoveredBox())
                    .isEmpty();
            }
        }

        @Test
        void resolveHoveredBoxIsEmptyWhenTheActiveLayerInjectsNoBox() {
            // The claims view's shape: the layer paints, and simply has nothing to say about a cell.
            when(layerRendererMock.resolveHoverTooltip())
                .thenReturn(Optional.empty());

            hoverTheSystem();

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(HoveredBox.resolveHoveredBox())
                    .isEmpty();
            }
        }

        @Test
        void resolveHoveredBoxIsEmptyWithoutALiveSector() {
            // Both passes run for the whole campaign UI, so they can be asked before a game is loaded.
            hoverTheSystem();

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(HoveredBox.resolveHoveredBox())
                    .isEmpty();
            }
        }

        @Test
        void resolveHoveredBoxIsEmptyWhenTheHoveredIdNoLongerNamesASystem() {
            // A system dropped between the hover being published and this frame reading it. Tolerated
            // rather than dereferenced, since the hover is a value the map pass left behind.
            MapHoverState
                .resolveLiveSectorHoverState()
                .publishHover(new MapHover("gone", List.of("gone")));

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(HoveredBox.resolveHoveredBox())
                    .isEmpty();
            }
        }
    }

    @Nested
    class IsOfferingExpansion {

        @Test
        void isOfferingExpansionAsksTheBoxAboutTheSystemItWouldDrawFor() {
            // Asked of the box rather than of the mode, and about this system rather than in general -
            // which is what lets a box offer an expansion on one cell and none on the next.
            when(tooltipMock.isOfferingExpansionFor(sectorMock, systemMock))
                .thenReturn(true);

            assertThat(new HoveredBox(tooltipMock, sectorMock, systemMock).isOfferingExpansion())
                .isTrue();
        }

        @Test
        void isOfferingExpansionIsFalseForABoxWithNothingMoreToState() {

            when(tooltipMock.isOfferingExpansionFor(any(), any()))
                .thenReturn(false);

            assertThat(new HoveredBox(tooltipMock, sectorMock, systemMock).isOfferingExpansion())
                .isFalse();
        }

        @Test
        void isOfferingExpansionIsFalseForABoxTakingNoPartInTheToggle() {
            // The pair of interface defaults read together: a tooltip that defines no richer
            // counterpart offers no expansion either. Pinned on the stand-in that overrides neither,
            // so what is asserted is the answer an implementation inherits rather than a stub's
            // imitation of it - a default that drifted the other way would leave the key claimed
            // over a box with nothing to switch to.
            var tooltipFake = new MapHoverTooltipFake();

            assertThat(new HoveredBox(tooltipFake, sectorMock, systemMock).isOfferingExpansion())
                .isFalse();
        }
    }

    @Nested
    class ResolveActiveTooltip {

        @Test
        void resolveActiveTooltipAnswersTheActiveLayersInjectedTooltip() {
            try (var globalMock = mockStatic(Global.class)) {

                // No sector means no stored pick, so the registry resolves to the registered default.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(HoveredBox.resolveActiveTooltip(new MapLayerInstallation()))
                    .contains(tooltipMock);
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWhenTheActiveLayerInjectsNone() {
            // The claims view's shape: the layer paints, and simply has nothing to say about one cell.
            when(layerRendererMock.resolveHoverTooltip())
                .thenReturn(Optional.empty());

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(HoveredBox.resolveActiveTooltip(new MapLayerInstallation()))
                    .isEmpty();
            }
        }

        @Test
        void resolveActiveTooltipAnswersTheActivePicksBoxWhileAnotherLayerWithholdsIts() {
            // The per-layer half of the hover switching: a layer whose own tooltip switch is off
            // offers no box, and that says nothing about the layer beside it - which is the case a
            // single shared switch could not express. The chain does not know the difference between a
            // withheld box and a layer that has nothing to say, and must not.
            var silencedLayerMock = mock(MapLayer.class);
            var silencedRendererMock = mock(MapLayerRenderer.class);

            when(silencedLayerMock.getId())
                .thenReturn("silenced_layer");
            when(silencedLayerMock.resolveRenderer(any()))
                .thenReturn(silencedRendererMock);

            when(silencedRendererMock.resolveHoverTooltip())
                .thenReturn(Optional.empty());

            MapLayerRegistry.registerLayers(
                List.of(silencedLayerMock, tooltipLayerMock),
                tooltipLayerMock);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(HoveredBox.resolveActiveTooltip(new MapLayerInstallation()))
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

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(HoveredBox.resolveActiveTooltip(new MapLayerInstallation()))
                    .isEmpty();
            }
        }

        @Test
        void resolveActiveTooltipIsEmptyWithoutAnActiveLayer() {
            // The pre-registration frame: both passes are installed on game load, so the chain can be
            // asked before any composition root has run rather than dereference a null pick.
            MapLayerRegistry.registerLayers(List.of(), null);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(HoveredBox.resolveActiveTooltip(new MapLayerInstallation()))
                    .isEmpty();
            }
        }
    }

    @Nested
    class ShouldDrawTooltipFor {

        @Test
        void shouldDrawTooltipForIsTrueForAHoveredCell() {

            var hover = new MapHover(SYSTEM_ID, List.of(SYSTEM_ID));

            assertThat(HoveredBox.shouldDrawTooltipFor(hover))
                .isTrue();
        }

        @Test
        void shouldDrawTooltipForIsFalseWhenNothingIsHovered() {
            assertThat(HoveredBox.shouldDrawTooltipFor(MapHover.NONE))
                .isFalse();
        }
    }

    // Puts the cursor over the registered system, which the chain resolves the hovered id against.
    private void hoverTheSystem() {
        MapHoverState
            .resolveLiveSectorHoverState()
            .publishHover(new MapHover(SYSTEM_ID, List.of(SYSTEM_ID)));
    }
}
