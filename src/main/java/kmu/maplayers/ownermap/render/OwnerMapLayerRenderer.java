package kmu.maplayers.ownermap.render;

import kmu.maplayers.base.hover.MapLayerHoverGates;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapLayerFrameParts;
import kmu.maplayers.base.render.SequencedMapLayerRenderer;
import kmu.maplayers.ownermap.MapLayerViewRegistry;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.owners.holders.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveSource;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.maplayers.ownermap.render.hover.OwnerMapPreviewHighlight;

import java.util.function.Supplier;

/**
 * Draws an owner map on the sector map as merged HOI4-style clusters, where adjacent same-holder
 * systems fuse into one solid cluster - by composing the owner-painted parts into the framework's
 * frame sequence. The sequence itself, and every rule about when each part is asked, is
 * {@link SequencedMapLayerRenderer}'s; what is settled here is which owner-map piece answers each part.
 *
 * <ul>
 *   <li>The layer's {@link MapLayerViewRegistry} is the stand-down read: a frame paints whichever view
 *       is active on the showing screen, or nothing when the tab is open with every view deselected.</li>
 *   <li>The {@link OwnerMapCache} is the cache, built under that view's rules.</li>
 *   <li>The {@link OwnerMapOverlayRenderer} is the compositor, stacking the overlay's sub-layers over the
 *       cache's draw lists.</li>
 *   <li>The layer's hover gates are its switches, and the active view's own box is its tooltip.</li>
 * </ul>
 *
 * <p>Resolving the view through the registry rather than at the surface is what keeps the framework
 * out of the layer's business: which of a layer's views draws is that layer's own question, and the
 * sequence learns only what it was handed back.
 */
public final class OwnerMapLayerRenderer {

    private OwnerMapLayerRenderer() {
    }

    /**
     * The renderer one sector's map machinery holds for an owner-painted layer, reading the covers
     * and the cursor of the screen the game is showing.
     *
     * @param machinery                 the machinery this renderer is being made for, whose sector its
     *                                  cache cuts its cells from, whose movers that cut leaves out,
     *                                  whose hover holder the cursor read publishes into, whose picker
     *                                  the preview is read off, and whose origin its profiling rows are
     *                                  grouped under
     * @param layerId                   the ID of the layer this renderer draws, which its rows are
     *                                  reported under - handed in by the layer rather than named here,
     *                                  so the renderer holds no second spelling of an ID the layer
     *                                  already owns
     * @param viewRegistry              the layer's own views, which decide whether a frame paints and
     *                                  under which view's rules
     * @param bodyPreferences           the layer's body preferences, stored under its own keys, which
     *                                  each rebuild samples its picks from
     * @param previewHighlight          the picker preview over that same machinery
     * @param hoverGates                the layer's own switches for the two kinds of cursor feedback
     * @param bandLayoutSource          where the layer's choosable sub-layers ride, read per pass
     * @param diagnosticsHolderProvider the holding the diagnostic overlays read
     * @param holderResolveSource       the per-system holder read the incremental refresh uses
     * @return a renderer for that machinery, its cache empty until the first frame builds it
     */
    public static SequencedMapLayerRenderer<OwnerPaintedView> createForLiveScreen(
            SectorMapMachinery machinery,
            String layerId,
            MapLayerViewRegistry viewRegistry,
            OwnerMapBodyPreferences bodyPreferences,
            OwnerMapPreviewHighlight previewHighlight,
            MapLayerHoverGates hoverGates,
            Supplier<OwnerMapBandLayout> bandLayoutSource,
            HolderProvider diagnosticsHolderProvider,
            SystemHolderResolveSource holderResolveSource) {

        // The cache and the compositor are made for the same machinery, so the draw lists one builds
        // and the hover the other lights are one sector's.
        var cache = new OwnerMapCache(
            machinery,
            bodyPreferences,
            diagnosticsHolderProvider,
            holderResolveSource);

        return SequencedMapLayerRenderer.createForLiveScreen(
            machinery,
            layerId,
            new MapLayerFrameParts<>(
                viewRegistry::resolveActiveViewOn,
                cache,
                new OwnerMapOverlayRenderer(
                    cache,
                    machinery.resolveHoverState(),
                    previewHighlight,
                    hoverGates,
                    bandLayoutSource),
                hoverGates,
                OwnerPaintedView::resolveHoverTooltip));
    }
}
