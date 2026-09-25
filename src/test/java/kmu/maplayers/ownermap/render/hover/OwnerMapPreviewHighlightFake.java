package kmu.maplayers.ownermap.render.hover;

import kmu.maplayers.base.render.MapFrame;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import java.util.ArrayList;
import java.util.List;

/**
 * A sidebar preview that paints nothing and records having been asked.
 *
 * <p>What a preview lights is the layer's, so a tier case about where the accent sits in the band
 * stack wants only to know the compositor reached it.
 */
public final class OwnerMapPreviewHighlightFake implements OwnerMapPreviewHighlight {

    private final List<MapFrame> paintedFrames = new ArrayList<>();

    /**
     * Every frame this preview was asked to paint, in order.
     *
     * @return the recorded frames; empty where the compositor never reached it
     */
    public List<MapFrame> readPaintedFrames() {
        return List.copyOf(paintedFrames);
    }

    @Override
    public void renderPreviewOnMap(OwnerMapClusters clusters, MapFrame mapFrame) {
        paintedFrames.add(mapFrame);
    }
}
