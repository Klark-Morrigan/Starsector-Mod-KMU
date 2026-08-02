package kmu.maplayers.base.hover;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.map.MapCursor;
import kmlib.starsector.ui.map.ModelviewMatrixReader;

import kmu.maplayers.base.geometry.CellHitTest;

import org.apache.log4j.Logger;

/**
 * Works out which cell the cursor is over on the map and publishes it for the frame.
 *
 * <p>Runs inside the map's render pass because that is the only place it can: the cursor read it
 * drives needs the map widget's GL matrices, which are bound only while that pass runs. A layer
 * therefore drives this from its {@code renderOnMap} rather than from an input listener.
 *
 * <p>What is owned here is the step from a world point to a hover, and the rule that anything less
 * than a trustworthy answer parks: {@link MapCursor} resolves the pixel, {@link CellHitTest} names
 * the cell, and this decides what the frame is told. Framework rather than one layer's, because
 * {@link MapHoverState} already declares the value, the shared holder and the consumers, so the
 * sequencing between them is the last piece a second layer would otherwise have to work out again
 * - and it would have to get every park right to avoid lighting a cell the cursor is not on.
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

        if (targets == null) {
            parkHover();
            return;
        }
        // No world point means the read could not be trusted - the cursor has left the window, the
        // transform is not the map's, or it will not invert. Which of the three it was does not
        // change the answer here: a cell resolved from an untrustworthy point is worse than none.
        var worldPoint = MapCursor.resolveWorldPointDuringMapPass(factor, modelviewMatrixReader);
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
