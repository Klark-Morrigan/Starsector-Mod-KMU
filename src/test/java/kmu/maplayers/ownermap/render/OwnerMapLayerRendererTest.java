package kmu.maplayers.ownermap.render;

import kmu.maplayers.base.hover.MapLayerHoverGates;
import kmu.maplayers.base.hover.MapLayerHoverGatesFake;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.render.SequencedMapLayerRenderer;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.MapLayerViewRegistry;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.owners.holders.HolderProviderFake;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveFake;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;
import kmu.maplayers.ownermap.render.hover.OwnerMapPreviewHighlightFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins which owner-map piece answers each part of the framework's frame: that the layer's view
 * registry is what stands a frame down, that the hover box is the active view's own, and that the
 * machinery behind the renderer can be released before it ever drew. The frame itself - when each
 * part is asked and what guards it - is {@code SequencedMapLayerRendererTest}'s; the cache's
 * refresh and the compositor's stack are {@link OwnerMapCacheTest}'s and
 * {@link OwnerMapOverlayRendererTest}'s.
 */
final class OwnerMapLayerRendererTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    // The layer's views, stubbed per case to the view a frame finds painting - or to none.
    private final MapLayerViewRegistry viewRegistryMock = mock(MapLayerViewRegistry.class);

    @Nested
    class CreateForLiveScreen {

        @Test
        void createForLiveScreenStandsEveryBeatDownOnADeselectedView() {
            // The tab is open with every view deselected, which the registry answers as no view. The
            // registry being the frame's stand-down read is what keeps a dark overlay near-free: no
            // beat consults the layer's switches, since nothing below the read is reached.
            var hoverGatesMock = mock(MapLayerHoverGates.class);

            when(viewRegistryMock.resolveActiveViewOn(any()))
                .thenReturn(null);

            var renderer = buildRenderer(hoverGatesMock);

            renderer.prepareFrame(FACTOR);
            renderer.renderOnMap(FACTOR, ALPHA_MULT, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);
            renderer.publishHoverForPass(FACTOR);

            verifyNoInteractions(hoverGatesMock);
        }

        @Test
        void createForLiveScreenAnswersTheActiveViewsTooltip() {
            // Which view is up decides what there is to say about a system, so the box handed to the
            // framework is whichever the active view injects - never a fixed one for the layer.
            var tooltipMock = mock(MapHoverTooltip.class);
            var viewMock = mock(OwnerPaintedView.class);

            when(viewMock.resolveHoverTooltip())
                .thenReturn(Optional.of(tooltipMock));
            when(viewRegistryMock.resolveActiveViewOn(any()))
                .thenReturn(viewMock);

            assertThat(buildRenderer(MapLayerHoverGatesFake.createAnswering()).resolveHoverTooltip())
                .contains(tooltipMock);
        }

        @Test
        void createForLiveScreenAnswersNoTooltipOnADeselectedView() {
            // Nothing is painted, so there is nothing for a hover to describe either.
            when(viewRegistryMock.resolveActiveViewOn(any()))
                .thenReturn(null);

            assertThat(buildRenderer(MapLayerHoverGatesFake.createAnswering()).resolveHoverTooltip())
                .isEmpty();
        }

        @Test
        void createForLiveScreenYieldsARendererReleasableBeforeAnySectorHasBeenDrawn() {
            // Reached for a sector installed on with the map never opened, when the cache has
            // nothing built to release.
            var renderer = buildRenderer(MapLayerHoverGatesFake.createAnswering());

            assertThatCode(renderer::disposeMachinery)
                .doesNotThrowAnyException();
        }
    }

    // The renderer as a layer composes it, over machinery of its own - one sector's, and never shared.
    private SequencedMapLayerRenderer<OwnerPaintedView> buildRenderer(MapLayerHoverGates hoverGates) {

        return OwnerMapLayerRenderer.createForLiveScreen(
            new SectorMapMachinery(null),
            "test_owner_map",
            viewRegistryMock,
            OwnerMapBodyPreferencesFixtures.createUnderTestKeys(),
            new OwnerMapPreviewHighlightFake(),
            hoverGates,
            BandLayoutFixtures::buildGeometryBelowAndReadoutsAbove,
            HolderProviderFake.createHoldingNothing(),
            SystemHolderResolveFake.createSourceHoldingNothing());
    }
}
