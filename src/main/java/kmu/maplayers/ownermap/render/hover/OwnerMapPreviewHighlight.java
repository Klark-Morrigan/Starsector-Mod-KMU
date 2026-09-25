package kmu.maplayers.ownermap.render.hover;

import kmu.maplayers.base.render.MapFrame;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

/**
 * The accent a layer paints for a pointer resting on its own sidebar rather than on the map -
 * what picking that row would light, shown without picking it.
 *
 * <p>A seam because what a row stands for is the layer's. The tier knows only where in the band
 * stack such an accent belongs and that it traces the frame's own draw lists; which rows exist,
 * what each one selects, and what shade it burns in are answered by whoever built the sidebar.
 *
 * <p>Handed the frame's painted geometry rather than a sector, so no paint state moves for a
 * preview: it can only ever light what the map is already showing.
 */
public interface OwnerMapPreviewHighlight {

    /**
     * Paints the previewed selection for one map frame, or nothing when no row is under the
     * pointer.
     *
     * @param clusters the draw lists the frame painted, which is the only geometry the preview
     *                    traces and the theme it draws to
     * @param mapFrame    the scale every coordinate is multiplied by, and the map's own fade
     *                    applied on top of every element's opacity
     */
    void renderPreviewOnMap(OwnerMapClusters clusters, MapFrame mapFrame);
}
