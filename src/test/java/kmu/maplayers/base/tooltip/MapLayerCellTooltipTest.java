package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverState;
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

import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
 * <p>Which of an injected tooltip's boxes the detail mode calls for is pinned the same way, over
 * stand-in tooltips: the decision is a pure read of the mode against what a tooltip offers, so it is
 * checkable without an engine even though drawing the chosen box is not.
 *
 * <p>The map gate is pinned as the supplier it is, open and closed, which is the whole of what this
 * level can say about it: what the live read answers, and that the installed one is the union
 * covering Starscape, belong to the seams themselves and to the install site's own test.
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

        @AfterEach
        void dropTheDetailModeBackToNormal() {
            // The mode holder is a process-wide singleton for the same reason the hover is, so a
            // flip left standing would reach the next test as a detail level it never asked for.
            HoverTooltipDetailModeState.getInstance().discardModeFromPreviousSave();
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
                new MapLayerCellTooltip(vanillaMapTooltipProbeMock, () -> true)
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
                new MapLayerCellTooltip(vanillaMapTooltipProbeMock, () -> false)
                    .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                verifyNoInteractions(vanillaMapTooltipProbeMock);
                verifyNoInteractions(tooltipMock);
            });
        }

        @Test
        void drawsWhileAMapIsOnScreen() {
            // The open side of the same gate, all the way through to the injected box - the only
            // case that proves the dispatcher reaches its tooltip rather than that it declines to.
            // Which map states open the gate is not this test's to say: the read arrives as a
            // supplier, so "showing" is all this level can express. That the supplied read is the
            // union covering Starscape is pinned against the real composition in
            // MapLayerCellTooltipGateIntegrationTest.
            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);
            var sectorMock = mock(SectorAPI.class);
            var systemMock = mock(StarSystemAPI.class);

            when(systemMock.getId())
                .thenReturn("system");
            when(sectorMock.getStarSystems())
                .thenReturn(List.of(systemMock));

            runWithHoverTooltipSwitchOn(() -> {
                try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                    globalMock
                        .when(Global::getSector)
                        .thenReturn(sectorMock);

                    new MapLayerCellTooltip(vanillaMapTooltipProbeMock, () -> true)
                        .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                    verify(tooltipMock)
                        .renderFor(sectorMock, systemMock);
                }
            });
        }

        @Test
        void drawsTheCounterpartTheDetailModeCallsFor() {
            // The dispatcher's one read of the shared mode. Selection itself is pure and knows no
            // holder, so nothing else pins that the box drawn is the box the last toggle selected -
            // a dispatcher that resolved the mode and then drew the base anyway would pass every
            // other case here. The mode is set on the shared holder rather than injected, since the
            // holder is what the live toggle writes and the dispatcher reads it the same way it
            // reads the hover.
            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);
            var sectorMock = mock(SectorAPI.class);
            var systemMock = mock(StarSystemAPI.class);
            var expandedTooltipMock = mock(MapHoverTooltip.class);

            when(systemMock.getId())
                .thenReturn("system");
            when(sectorMock.getStarSystems())
                .thenReturn(List.of(systemMock));
            when(tooltipMock.resolveExpandedVariant())
                .thenReturn(Optional.of(expandedTooltipMock));

            HoverTooltipDetailModeState.getInstance().toggleMode();

            runWithHoverTooltipSwitchOn(() -> {
                try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {

                    globalMock
                        .when(Global::getSector)
                        .thenReturn(sectorMock);

                    new MapLayerCellTooltip(vanillaMapTooltipProbeMock, () -> true)
                        .renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));

                    verify(expandedTooltipMock)
                        .renderFor(sectorMock, systemMock);
                        
                    // Drawn instead of the injected box, not alongside it: two boxes over one cell
                    // is the failure the swap exists to avoid.
                    verify(tooltipMock, never())
                        .renderFor(sectorMock, systemMock);
                }
            });
        }

    }

    @Nested
    class SelectVariantFor {

        @Test
        void selectVariantForAnswersTheBaseUnderTheNormalMode() {
            // Over a tooltip that does define a richer box: the mode is what decides, so defining a
            // counterpart must not be enough to draw it.
            var expandedVariantFake = new MapHoverTooltipFake();
            var baseFake = new ExpandedVariantMapHoverTooltipFake(expandedVariantFake);

            assertThat(MapLayerCellTooltip.selectVariantFor(baseFake, HoverTooltipDetailMode.NORMAL))
                .isSameAs(baseFake);
        }

        @Test
        void selectVariantForAnswersTheBaseUnderTheNormalModeWithoutACounterpart() {
            // The ordinary box in the ordinary mode - the path every layer takes today, which the
            // seam must leave exactly where it was.
            var baseFake = new MapHoverTooltipFake();

            assertThat(MapLayerCellTooltip.selectVariantFor(baseFake, HoverTooltipDetailMode.NORMAL))
                .isSameAs(baseFake);
        }

        @Test
        void selectVariantForAnswersTheCounterpartUnderTheExpandedMode() {
            // The one case the toggle exists for. Asserted on the instance rather than the type,
            // since a tooltip may well offer a counterpart of the same shape as itself.
            var expandedVariantFake = new MapHoverTooltipFake();
            var baseFake = new ExpandedVariantMapHoverTooltipFake(expandedVariantFake);

            assertThat(MapLayerCellTooltip.selectVariantFor(baseFake, HoverTooltipDetailMode.EXPANDED))
                .isSameAs(expandedVariantFake);
        }

        @Test
        void selectVariantForAnswersTheBaseUnderTheExpandedModeWithoutACounterpart() {
            // The graceful-undefined path: the mode is on, and this tooltip has nothing richer to
            // say, so the player keeps the normal box rather than losing it. The stand-in inherits
            // the interface's own empty answer, so this pins the default an implementation gets.
            var baseFake = new MapHoverTooltipFake();

            assertThat(MapLayerCellTooltip.selectVariantFor(baseFake, HoverTooltipDetailMode.EXPANDED))
                .isSameAs(baseFake);
        }
    }
}
