package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what this renderer decides for itself before any drawing happens: that a deselected view
 * costs a frame nothing, that the hover box it answers the framework with is the active view's, and
 * that a game load leaves nothing of the previous sector behind. What the discard actually empties is
 * {@link PoliticalMapCacheTest}'s; the cache refresh, the cursor read and the GL emission run only
 * in-engine and are covered by their own collaborators.
 */
final class PoliticalMapLayerRendererTest {
    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapStandsDownWhileNoViewIsSelected() {
            // The tab is open with every view deselected. Standing down on that one read is what
            // keeps a dark overlay near-free per frame: nothing downstream is consulted, not even the
            // hover toggle that gates the cheapest of the work below it.
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                viewRegistryMock.when(PoliticalMapViewRegistry::getActiveView).thenReturn(null);

                PoliticalMapLayerRenderer.INSTANCE.renderOnMap(FACTOR, ALPHA_MULT);

                settingsMock.verifyNoInteractions();
            }
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void resolveHoverTooltipAnswersTheActiveViewsTooltip() {
            // Which view is up decides what there is to say about a system, so the box handed to the
            // framework is whichever the active view injects - never a fixed one for the layer.
            var tooltipMock = mock(MapHoverTooltip.class);
            var viewMock = mock(PoliticalMapView.class);
            when(viewMock.resolveHoverTooltip()).thenReturn(Optional.of(tooltipMock));
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                    mockStatic(PoliticalMapViewRegistry.class)) {
                viewRegistryMock.when(PoliticalMapViewRegistry::getActiveView).thenReturn(viewMock);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
                        .contains(tooltipMock);
            }
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileNoViewIsSelected() {
            // The tab is open with every view deselected: nothing is painted, so there is nothing for a
            // hover to describe either.
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                    mockStatic(PoliticalMapViewRegistry.class)) {
                viewRegistryMock.when(PoliticalMapViewRegistry::getActiveView).thenReturn(null);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip()).isEmpty();
            }
        }
    }

    @Nested
    class DiscardStateFromPreviousSave {

        @Test
        void discardStateFromPreviousSaveRunsBeforeAnySectorHasBeenDrawn() {
            // Called on every load including the session's first, when there is nothing built to drop
            // and no hover to park.
            assertThatCode(PoliticalMapLayerRenderer.INSTANCE::discardStateFromPreviousSave)
                    .doesNotThrowAnyException();
        }
    }
}
