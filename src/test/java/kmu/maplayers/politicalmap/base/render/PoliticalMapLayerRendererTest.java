package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;
import kmu.starsector.consolecommands.ConsoleOverlayFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins what this renderer decides for itself before any drawing happens: that a deselected view
 * costs a frame nothing whichever of its passes is running, that the hover box it answers the
 * framework with is the active view's and only while this layer's own tooltip switch is on, that an
 * open console counts as a cover over the map, and that a game load leaves nothing of the previous
 * sector behind. What the discard actually empties is
 * {@link PoliticalMapCacheTest}'s; the cache refresh, the cursor read and the GL emission run only
 * in-engine and are covered by their own collaborators.
 */
final class PoliticalMapLayerRendererTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    @Nested
    class PrepareFrame {

        @Test
        void prepareFrameStandsDownWhileNoViewIsSelected() {
            // The tab is open with every view deselected. Standing down on that one read is what
            // keeps a dark overlay near-free per frame: nothing downstream is consulted, not even the
            // hover toggle that gates the cheapest of the work below it.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                PoliticalMapLayerRenderer.INSTANCE.prepareFrame(FACTOR);

                frameworkSettingsMock
                    .verifyNoInteractions();
                layerSettingsMock
                    .verifyNoInteractions();
            }
        }
    }

    @Nested
    class RenderOnMap {

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void renderOnMapStandsDownWhileNoViewIsSelected(MapOverlayBand band) {
            // Every band asks the same question and gets the same answer: a deselected view has
            // nothing to say about either side of the map's nebulae. Driven per band because
            // each is a separate pass from a separate surface, so a band that read the view
            // differently would paint on a frame the others left alone.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                PoliticalMapLayerRenderer.INSTANCE.renderOnMap(FACTOR, ALPHA_MULT, band);

                frameworkSettingsMock
                    .verifyNoInteractions();
                layerSettingsMock
                    .verifyNoInteractions();
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

            when(viewMock.resolveHoverTooltip())
                .thenReturn(Optional.of(tooltipMock));

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
                    .contains(tooltipMock);
            }
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileThisLayersTooltipSwitchIsOff() {
            // The layer withholds its box by offering none, which is how one layer's box goes dark
            // while every other layer's stays up - the dispatcher above draws whatever it is offered.
            var viewMock = mock(PoliticalMapView.class);

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, false);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
                    .isEmpty();

                // The switch answers before the view is consulted, so a view that would have built a
                // box never does the work.
                verifyNoInteractions(viewMock);
            }
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileNoViewIsSelected() {
            // The tab is open with every view deselected: nothing is painted, so there is nothing for a
            // hover to describe either.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);
                
                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
                    .isEmpty();
            }
        }
    }

    @Nested
    class IsMapCoveredAtCursor {

        @Test
        void isMapCoveredAtCursorAnswersCoveredWhileAConsoleIsOpen() {
            // A console covers the whole screen, so the cell under the cursor is not what the player
            // is pointing at - without this the map went on lighting cells and floating hover boxes
            // behind an open console, the sidebar having already stood down and stopped covering it.
            //
            // The console is the only one of the three covers a test can reach: it is asked first
            // and short-circuits the sidebar and the vanilla chrome, both of which read a live map.
            var consoleOverlayFake = new ConsoleOverlayFake();
            consoleOverlayFake.openConsole();

            assertThat(new PoliticalMapLayerRenderer(consoleOverlayFake).isMapCoveredAtCursor())
                .isTrue();
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

    // The three tiers the hover box is switched at, set together: the two above the layer left on,
    // and the layer's own set to what the test is about. Each tier's own arithmetic is
    // PoliticalMapHoverGatesTest's; what is pinned here is which of them this renderer obeys.
    private static void stubTooltipSwitches(
            MockedStatic<KmuMapLayerSettings> frameworkSettingsMock,
            MockedStatic<KmuPoliticalMapSettings> layerSettingsMock,
            boolean isPoliticalTooltipEnabled) {

        frameworkSettingsMock
            .when(KmuMapLayerSettings::getMapHoveringEnabled)
            .thenReturn(true);

        frameworkSettingsMock
            .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
            .thenReturn(true);

        layerSettingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapHoverTooltipEnabled)
            .thenReturn(isPoliticalTooltipEnabled);
    }
}
