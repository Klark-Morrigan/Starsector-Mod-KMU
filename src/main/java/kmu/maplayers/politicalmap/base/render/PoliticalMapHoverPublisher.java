package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.map.CampaignMapTransform;
import kmlib.starsector.ui.map.GlModelviewMatrixReader;

import kmu.maplayers.politicalmap.base.geometry.CellHitTest;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHover;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHoverState;

import org.apache.log4j.Logger;
import org.lwjgl.input.Mouse;

/**
 * Works out what the cursor is over on the political map and publishes it for the frame.
 *
 * <p>Runs inside the map's render pass because that is the only place it can: undoing the map's
 * pan, centring, and zoom to turn a cursor pixel back into a world point needs the widget's GL
 * matrices, which are only bound while that pass runs. The plugin therefore drives this from its
 * {@code renderOnMap} rather than from an input listener.
 *
 * <p>Reading the cursor rather than consuming input events is the whole point: the map keeps
 * hovering stars and drawing their tooltips exactly as it did, because nothing here takes an event
 * away from it. The overlay is a passive second reader of a cursor the game is still free to
 * interpret its own way.
 */
final class PoliticalMapHoverPublisher {
    private static final Logger LOG = Global.getLogger(PoliticalMapHoverPublisher.class);

    // The last system named in the log, so the trace reports each move onto a new cell once rather
    // than re-reporting the same cell every frame the cursor rests on it.
    private String lastLoggedSystemId;

    /**
     * Resolves the cursor to a cell and its territory and publishes the result, or parks the hover
     * when the cursor is over no cell.
     *
     * @param cache  the frame's draw lists, supplying the painted cell shapes to test against
     * @param factor the per-vertex scale this render pass applies, needed to undo the map's zoom
     */
    public void publishHoverFrom(PoliticalMapCache cache, float factor) {
        var territories = cache.getTerritories();
        // No production draw lists means nothing was painted to hover over: the debug border-tracing
        // overlay replaced them, or the first build has yet to succeed.
        if (territories == null || !Mouse.isInsideWindow()) {
            parkHover();
            return;
        }
        // A snapshot the map's transform could not be read into resolves nothing, so the hover
        // parks rather than reporting a cell worked out from a transform that is not the map's.
        var transform = CampaignMapTransform.captureFromMapPass(
                factor, GlModelviewMatrixReader.INSTANCE);
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
                territories.getFillPolygonBySystemId());
        if (hoveredSystemId == null) {
            parkHover();
            return;
        }
        PoliticalMapHoverState.getInstance().publishHover(new PoliticalMapHover(
                hoveredSystemId,
                territories.getClusterIndex().findClusterMembersOf(hoveredSystemId)));
        logHoverChange(hoveredSystemId);
    }

    // Parks the hover and resets the log guard, so stepping off a cell and back onto it reports
    // again rather than being swallowed as unchanged.
    private void parkHover() {
        PoliticalMapHoverState.getInstance().clearHover();
        lastLoggedSystemId = null;
    }

    // Traces each move onto a new cell: which system the cursor resolved to and how large a
    // territory that pulls in - the two answers this pass exists to produce, and the ones a wrong
    // highlight is diagnosed against. Set KMU log verbosity to DEBUG in LunaLib to see it.
    private void logHoverChange(String hoveredSystemId) {
        if (hoveredSystemId.equals(lastLoggedSystemId) || !LOG.isDebugEnabled()) {
            return;
        }
        lastLoggedSystemId = hoveredSystemId;
        LOG.debug("Political map hover resolved; system=" + hoveredSystemId + " clusterMembers="
                + PoliticalMapHoverState.getInstance().getHover().clusterMemberSystemIds());
    }
}
