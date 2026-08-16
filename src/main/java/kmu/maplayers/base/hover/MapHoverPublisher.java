package kmu.maplayers.base.hover;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.input.KeyedHoverArrival;
import kmlib.starsector.ui.map.transform.MapCursor;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReader;
import kmlib.starsector.ui.sound.UiSoundCue;
import kmlib.starsector.ui.sound.UiSoundPlayer;

import kmu.maplayers.base.geometry.CellHitTest;

import org.apache.log4j.Logger;

import java.util.function.Supplier;

/**
 * Works out which cell the cursor is over on the map, publishes it for the frame, and answers the
 * moment the cursor reaches a new one.
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
 *
 * <p>Which frames are an <em>arrival</em> is the same question the trace has always asked - the cell
 * changed under a cursor that was somewhere else before - so one {@link KeyedHoverArrival} answers
 * both what is sounded and what is logged. Two latches over one question would be two chances to
 * disagree about when the cursor got somewhere, and a tick without a line beside it is exactly the
 * moment the trace exists to explain.
 */
public final class MapHoverPublisher {
    private static final Logger LOG = Global.getLogger(MapHoverPublisher.class);

    // Whether the cursor has just reached a cell it was not on. Keyed by the cell rather than by the
    // cluster around it, so the tick answers the same change the hover box does - a sweep across one
    // cluster still changes which system is being named - and the shared latch is what makes
    // "reached" mean the same thing here as it does on a panel's controls.
    private final KeyedHoverArrival<String> cellArrival = new KeyedHoverArrival<>();

    // What an arrival sounds like, asked at the moment rather than held, so the level the player set
    // is the one in force now: this publisher outlives any number of visits to the settings screen.
    private final Supplier<UiSoundCue> cellArrivalCueSource;

    // Where the map's modelview is read back from. Held rather than resolved per frame because the
    // renderer underneath cannot change while the game runs, so the binding is a fixed collaborator
    // of this publisher's session-long life.
    private final ModelviewMatrixReader modelviewMatrixReader;

    private final UiSoundPlayer soundPlayer;

    /**
     * @param modelviewMatrixReader the binding the running renderer needs, from
     *                              {@code ModelviewMatrixReaders#selectForActiveRenderer}
     * @param soundPlayer           where the arrival tick goes
     * @param cellArrivalCueSource  what that tick sounds like when one is owed, from the host's own
     *                              look - {@code MapHoverCues#composeCellArrivalCue} for this mod's
     *                              map. A source rather than a cue because the level behind it is the
     *                              player's and may change under a publisher already built
     */
    public MapHoverPublisher(
            ModelviewMatrixReader modelviewMatrixReader,
            UiSoundPlayer soundPlayer,
            Supplier<UiSoundCue> cellArrivalCueSource) {

        this.modelviewMatrixReader = modelviewMatrixReader;
        this.soundPlayer = soundPlayer;
        this.cellArrivalCueSource = cellArrivalCueSource;
    }

    /**
     * Resolves the cursor to a cell and the cluster around it and publishes the result, or parks
     * the hover when the cursor is over no cell.
     *
     * <p>Parking is what every guard below reaches for, rather than leaving the previous frame's
     * answer standing: a stale hover washes a cell the cursor has left and answers the tooltip
     * with the wrong system, which reads as a bug in the highlight rather than in the read.
     *
     * <p>The guards part company over the arrival latch, though, because they answer two different
     * questions. A completed hit-test that finds no cell is a sighting - the cursor is on nothing -
     * and forgetting the cell is what makes stepping off one and back onto it a fresh arrival. A
     * read that could not be made is not a sighting at all, so it leaves the latch holding the last
     * cell the cursor was actually seen on.
     *
     * @param targets the frame's drawn cells, or null when the layer painted nothing to hover
     *                over - a build that has yet to succeed, or a diagnostic overlay standing in
     *                for the production draw lists. Nullable so the park stays here, in the one
     *                place that owns what "no hover" means, rather than in each layer's caller
     * @param factor  the per-vertex scale this render pass applies, needed to undo the map's zoom
     */
    public void publishHoverFrom(MapHoverTargets targets, float factor) {

        // Nothing painted, so there are no cell shapes to test the cursor against. That is a
        // missing input rather than an answer about where the cursor is, so the latch stands.
        if (targets == null) {
            parkHoverKeepingLastCell();
            return;
        }
        // No world point means the read could not be trusted - the cursor has left the window, the
        // transform is not the map's, or it will not invert. Which of the three it was does not
        // change the answer here: a cell resolved from an untrustworthy point is worse than none.
        var worldPoint = MapCursor.resolveWorldPointDuringMapPass(factor, modelviewMatrixReader);
        if (worldPoint == null) {
            parkHoverKeepingLastCell();
            return;
        }
        var hoveredSystemId = CellHitTest.resolveSystemIdAt(
            worldPoint.x,
            worldPoint.y,
            targets.getFillPolygonByCellId());

        if (hoveredSystemId == null) {
            parkHoverAndForgetCell();
            return;
        }
        MapHoverState.getInstance().publishHover(new MapHover(
            hoveredSystemId,
            targets.getClusterIndex().findClusterMembersOf(hoveredSystemId)));

        announceArrivalAt(hoveredSystemId);
    }

    // Parks the hover and forgets which cell the cursor was on, for a hit-test that ran and found
    // nothing under the cursor. Forgetting is what makes stepping off a cell and back onto it
    // reached again rather than swallowed as unchanged.
    private void parkHoverAndForgetCell() {
        parkHoverKeepingLastCell();
        cellArrival.resetArrival();
    }

    // Parks the hover but leaves the latch holding the last cell the cursor was seen on, for a
    // frame whose inputs never arrived. Nothing was learned about where the cursor is, so nothing
    // about where it was is worth discarding.
    //
    // The distinction is what keeps the tick honest on a pass that can run more than once a frame:
    // one run failing its read while another resolves the cell the cursor is resting on would,
    // under a latch that forgets, read as leaving and reaching that cell over and over - a tick
    // every frame under a motionless cursor. The hover itself is still cleared, since a highlight
    // left standing on an unverified cell is the fault every guard here exists to avoid.
    private void parkHoverKeepingLastCell() {
        MapHoverState.getInstance().clearHover();
    }

    // What is owed on the cursor reaching a cell, as opposed to resting on one: the tick the player
    // hears and the line the trace prints. The latch is stepped before either is asked for, so a
    // moment left unanswered - a silenced cue, a trace at INFO - still tracks where the cursor is.
    private void announceArrivalAt(String hoveredSystemId) {

        if (!cellArrival.detectArrivalAt(hoveredSystemId)) {
            return;
        }
        soundPlayer.playCueIfPresent(cellArrivalCueSource.get());
        logHoverArrival(hoveredSystemId);
    }

    // Traces each move onto a new cell: which system the cursor resolved to and how large a
    // cluster that pulls in - the two answers this pass exists to produce, and the ones a wrong
    // highlight is diagnosed against. Set KMU log verbosity to DEBUG in LunaLib to see it.
    private void logHoverArrival(String hoveredSystemId) {

        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("Map hover resolved; system="
            + hoveredSystemId
            + " clusterMembers="
            + MapHoverState.getInstance().getHover().clusterMemberSystemIds());
    }
}
