package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.hover.HoverHighlightRenderer;
import kmu.maplayers.base.labels.LabelRenderer;
import kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.render.clusters.ClusterRenderer;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageRenderer;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverGates;
import kmu.maplayers.politicalmap.base.render.ribbon.CellPresenceRibbonRenderer;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins which sub-layer belongs to which band, the one thing this compositor decides that the map can
 * see. Under Starscape the two bands are painted from separate terrain surfaces with the map's own
 * nebulae drawn between them, so a sub-layer emitted for the wrong band is drawn on the wrong side of
 * the fog - and there is nothing in the frame's own output to say so.
 *
 * <p>The emitting passes are mocked out: each is a static GL call that runs only in-engine, and what
 * they draw is their own to cover. What is asserted here is which of them is reached.
 */
final class PoliticalMapOverlayRendererTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    private final PoliticalMapOverlayRenderer overlayRenderer = new PoliticalMapOverlayRenderer();

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapEmitsTheTerritoriesForTheBandBeneathTheNebulae() {
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    FACTOR,
                    ALPHA_MULT,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                clusterRendererMock.verify(() ->
                    ClusterRenderer.renderOnMap(any(), anyFloat(), anyFloat()));

                anchorRendererMock
                    .verifyNoInteractions();
                labelRendererMock
                    .verifyNoInteractions();
                ribbonRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapEmitsThePresenceBandsAndTheFactionNamesForTheBandAboveTheNebulae() {
            // The two sub-layers that are read rather than merely seen, and the reason the band
            // exists: text stops being readable under the fog well before a fill stops reading as
            // territory, and a band that says how a system is split fails the same way.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    FACTOR,
                    ALPHA_MULT,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                ribbonRendererMock.verify(() ->
                    CellPresenceRibbonRenderer.renderOnMap(
                        any(),
                        any(),
                        anyFloat(),
                        anyFloat()));

                labelRendererMock.verify(() ->
                    LabelRenderer.renderOnMap(any(), anyFloat(), anyFloat()));

                clusterRendererMock
                    .verifyNoInteractions();
                anchorRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapEmitsTheHoverHighlightAndTheAnchorsForTheBandBeneathTheNebulae() {
            // Driven with both switches on, which is what makes this a statement about bands rather
            // than about toggles: with them off, a sub-layer moved to the wrong band still emits
            // nothing and every assertion in the two tests above goes on passing.
            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                // Built inside the construction mock, since the highlight renderer is a field this
                // compositor creates for itself - there is no seam to inject one through.
                new PoliticalMapOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    FACTOR,
                    ALPHA_MULT,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                anchorRendererMock.verify(() ->
                    ClusterAnchorRenderer.renderOnMap(any(), anyFloat(), anyFloat()));

                verify(hoverRendererConstructionMock.constructed().get(0))
                    .renderOnMap(any(), any(), any(), anyFloat(), anyFloat());
            }
        }

        @Test
        void renderOnMapLeavesTheHoverHighlightAndTheAnchorsOutOfTheBandAboveTheNebulae() {
            // The half of the pinning that catches a sub-layer promoted by accident: both switches
            // are on, so anything reached here is reached because of the band it was asked for.
            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                new PoliticalMapOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    FACTOR,
                    ALPHA_MULT,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                anchorRendererMock
                    .verifyNoInteractions();

                // Held to exactly one stand-in before it is asserted against: an empty list would
                // satisfy "none of them drew" without the compositor ever having had a highlight
                // renderer to leave alone, which is the shape of passing test this group exists to
                // stop.
                assertThat(hoverRendererConstructionMock.constructed())
                    .singleElement()
                    .satisfies(hoverRenderer -> verifyNoInteractions(hoverRenderer));
            }
        }

        @Test
        void renderOnMapEmitsTheDebugBorderStageForTheBandBeneathTheNebulae() {
            // The debug overlay replaces the territories rather than layering over them, so it sits
            // in the band they would have occupied - a swap inside one band, not a band of its own.
            try (var borderStageRendererMock = mockStatic(ClusterBorderStageRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                overlayRenderer.renderOnMap(
                    buildDebugCacheMock(),
                    FACTOR,
                    ALPHA_MULT,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                borderStageRendererMock.verify(() ->
                    ClusterBorderStageRenderer.renderOnMap(any(), anyFloat(), anyFloat()));

                clusterRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapEmitsNoPresenceBandsUnderTheDebugOverlayInTheBandAboveTheNebulae() {
            // The bands are baked into the territories, and a debug frame built the border-stage
            // overlay instead of them - so there is nothing to paint them from, and asking would
            // reach through a frame this cache never built. The names are unaffected, being held
            // by the cache in their own right, and are asserted so the guard is seen to stop one
            // sub-layer rather than the whole band.
            try (var borderStageRendererMock = mockStatic(ClusterBorderStageRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                overlayRenderer.renderOnMap(
                    buildDebugCacheMock(),
                    FACTOR,
                    ALPHA_MULT,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                ribbonRendererMock
                    .verifyNoInteractions();

                labelRendererMock.verify(() ->
                    LabelRenderer.renderOnMap(any(), anyFloat(), anyFloat()));
            }
        }
    }

    // The two switches that gate sub-layers within a band rather than deciding which band they are
    // in, both open. What each switch decides is its own gate's to cover; what they allow here is a
    // sub-layer to be observed in the band that asked for it.
    private static void openTheTogglesTheBandsDoNotDecide(
            MockedStatic<PoliticalMapHoverGates> hoverGatesMock,
            MockedStatic<KmuPoliticalMapSettings> layerSettingsMock) {

        hoverGatesMock
            .when(PoliticalMapHoverGates::isHoverEffectsEnabled)
            .thenReturn(true);

        layerSettingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapShowClusterAnchors)
            .thenReturn(true);
    }

    // The two switches that gate sub-layers within a band rather than deciding which band they are
    // in. Held off so each test observes the band split alone; what each switch does is its own
    // gate's to cover.
    private static void silenceTheTogglesTheBandsDoNotDecide(
            MockedStatic<PoliticalMapHoverGates> hoverGatesMock,
            MockedStatic<KmuPoliticalMapSettings> layerSettingsMock) {

        hoverGatesMock
            .when(PoliticalMapHoverGates::isHoverEffectsEnabled)
            .thenReturn(false);

        layerSettingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapShowClusterAnchors)
            .thenReturn(false);
    }

    // A cache holding a built, non-debug frame with nothing in it. The bands are decided on the band
    // alone, so an empty frame exercises the split without any geometry having to exist - the styled
    // cells are stubbed only because the compositor's one-shot debug line counts them.
    private static PoliticalMapCache buildCacheMock() {

        var territoriesMock = mock(PoliticalMapTerritories.class);
        
        when(territoriesMock.getStyledCellByCellId())
            .thenReturn(Map.of());

        // Stated rather than left to the default a mock would answer with, since the upper band is
        // asserted to have reached the band pass at all - and a stub that resolves by accident is
        // one the pass could stop calling without any test noticing.
        when(territoriesMock.getRibbonByCellId())
            .thenReturn(Map.of());

        // Read while assembling the hover highlight's arguments, so it has to resolve even though
        // the highlight renderer itself is stood in for.
        when(territoriesMock.getGlobalStyle())
            .thenReturn(mock(GlobalStyle.class));

        var cacheMock = mock(PoliticalMapCache.class);

        when(cacheMock.isDebug())
            .thenReturn(false);
        when(cacheMock.getTerritories())
            .thenReturn(territoriesMock);
        when(cacheMock.getFactionLabels())
            .thenReturn(List.of());

        return cacheMock;
    }

    // A cache holding a debug frame instead: the cache builds the border-stage overlay or the
    // territories, never both, so this is the other of the two states the lower band has to paint.
    // The loops are stubbed empty for the reason the styled cells are - the one-shot debug line
    // counts them.
    private static PoliticalMapCache buildDebugCacheMock() {

        var borderStageOverlayMock = mock(ClusterBorderStageOverlay.class);

        when(borderStageOverlayMock.baseLoops())
            .thenReturn(List.of());

        var cacheMock = mock(PoliticalMapCache.class);

        when(cacheMock.isDebug())
            .thenReturn(true);
        when(cacheMock.getBorderStageOverlay())
            .thenReturn(borderStageOverlayMock);

        return cacheMock;
    }
}
