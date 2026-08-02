package kmu.maplayers.base.hover;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.map.CampaignMapTransform;
import kmlib.starsector.ui.map.ModelviewMatrixReader;

import kmu.maplayers.base.geometry.CellHitTest;

import org.apache.log4j.Logger;
import org.lwjgl.input.Mouse;

/**
 * Works out what the cursor is over on the map and publishes it for the frame.
 *
 * <p>Runs inside the map's render pass because that is the only place it can: undoing the map's
 * pan, centring, and zoom to turn a cursor pixel back into a world point needs the widget's GL
 * matrices, which are only bound while that pass runs. A layer therefore drives this from its
 * {@code renderOnMap} rather than from an input listener.
 *
 * <p>Reading the cursor rather than consuming input events is the whole point: the map keeps
 * hovering stars and drawing their tooltips exactly as it did, because nothing here takes an event
 * away from it. The overlay is a passive second reader of a cursor the game is still free to
 * interpret its own way.
 *
 * <p>Framework rather than one layer's, because the matrix inversion is the hard part of hovering
 * and it is the same inversion whatever a layer paints: {@link MapHoverState} already declares the
 * value, the shared holder, and the consumers, so the only piece a second layer would otherwise
 * have to rediscover is this one.
 */
public final class MapHoverPublisher {
    private static final Logger LOG = Global.getLogger(MapHoverPublisher.class);

    // Where the map's modelview is read back from. Held rather than resolved per frame because the
    // renderer underneath cannot change while the game runs, so the binding is a fixed collaborator
    // of this publisher's session-long life.
    private final ModelviewMatrixReader modelviewMatrixReader;

    // The last system named in the log, so the trace reports each move onto a new cell once rather
    // than re-reporting the same cell every frame the cursor rests on it.
    private String lastLoggedSystemId;

    /**
     * @param modelviewMatrixReader the binding the running renderer needs, from
     *                              {@code ModelviewMatrixReaders#selectForActiveRenderer}
     */
    public MapHoverPublisher(ModelviewMatrixReader modelviewMatrixReader) {
        this.modelviewMatrixReader = modelviewMatrixReader;
    }

    /**
     * Resolves the cursor to a cell and the cluster around it and publishes the result, or parks
     * the hover when the cursor is over no cell.
     *
     * <p>Parking is what every guard below reaches for, rather than leaving the previous frame's
     * answer standing: a stale hover washes a cell the cursor has left and answers the tooltip
     * with the wrong system, which reads as a bug in the highlight rather than in the read.
     *
     * @param targets the frame's drawn cells, or null when the layer painted nothing to hover
     *                over - a build that has yet to succeed, or a diagnostic overlay standing in
     *                for the production draw lists. Nullable so the park stays here, in the one
     *                place that owns what "no hover" means, rather than in each layer's caller
     * @param factor  the per-vertex scale this render pass applies, needed to undo the map's zoom
     */
    public void publishHoverFrom(MapHoverTargets targets, float factor) {

        if (targets == null || !Mouse.isInsideWindow()) {
            parkHover();
            return;
        }
        // A snapshot the map's transform could not be read into resolves nothing, so the hover
        // parks rather than reporting a cell worked out from a transform that is not the map's.
        var transform = CampaignMapTransform.captureFromMapPass(factor, modelviewMatrixReader);
        if (transform == null) {
            parkHover();
            return;
        }
        var worldPoint = transform.unprojectToWorld(Mouse.getX(), Mouse.getY());
        if (worldPoint == null) {
            parkHover();
            return;
        }
        var hoveredSystemId = CellHitTest.resolveSystemIdAt(
            worldPoint.x,
            worldPoint.y,
            targets.getFillPolygonByCellId());

        if (hoveredSystemId == null) {
            parkHover();
            return;
        }
        MapHoverState.getInstance().publishHover(new MapHover(
            hoveredSystemId,
            targets.getClusterIndex().findClusterMembersOf(hoveredSystemId)));

        logHoverChange(hoveredSystemId);
    }

    // Parks the hover and resets the log guard, so stepping off a cell and back onto it reports
    // again rather than being swallowed as unchanged.
    private void parkHover() {
        MapHoverState.getInstance().clearHover();
        lastLoggedSystemId = null;
    }

    // Traces each move onto a new cell: which system the cursor resolved to and how large a
    // cluster that pulls in - the two answers this pass exists to produce, and the ones a wrong
    // highlight is diagnosed against. Set KMU log verbosity to DEBUG in LunaLib to see it.
    private void logHoverChange(String hoveredSystemId) {
        if (hoveredSystemId.equals(lastLoggedSystemId) || !LOG.isDebugEnabled()) {
            return;
        }
        lastLoggedSystemId = hoveredSystemId;

        LOG.debug("Map hover resolved; system="
            + hoveredSystemId
            + " clusterMembers="
            + MapHoverState.getInstance().getHover().clusterMemberSystemIds());
    }
}
