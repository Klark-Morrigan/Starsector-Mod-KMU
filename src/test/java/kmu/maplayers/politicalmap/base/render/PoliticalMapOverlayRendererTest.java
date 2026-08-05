package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.labels.LabelRenderer;
import kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.render.clusters.ClusterRenderer;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverGates;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
            }
        }

        @Test
        void renderOnMapEmitsTheFactionNamesForTheBandAboveTheNebulae() {
            // The names are the whole of the upper band, and the reason the band exists: text stops
            // being readable under the fog well before a fill stops reading as territory.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, layerSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(), FACTOR, ALPHA_MULT,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                labelRendererMock.verify(() ->
                    LabelRenderer.renderOnMap(any(), anyFloat(), anyFloat()));

                clusterRendererMock
                    .verifyNoInteractions();
                anchorRendererMock
                    .verifyNoInteractions();
            }
        }
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

        var cacheMock = mock(PoliticalMapCache.class);
        when(cacheMock.isDebug())
            .thenReturn(false);
        when(cacheMock.getTerritories())
            .thenReturn(territoriesMock);
        when(cacheMock.getFactionLabels())
            .thenReturn(List.of());

        return cacheMock;
    }
}
