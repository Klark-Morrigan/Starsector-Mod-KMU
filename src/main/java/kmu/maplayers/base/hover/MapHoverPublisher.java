package kmu.maplayers.base.hover;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.input.KeyedHoverArrival;
import kmlib.starsector.ui.map.transform.MapCursor;
import kmlib.starsector.ui.map.transform.MapCursorRead;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReader;

import kmu.maplayers.base.geometry.CellHitTest;

import org.apache.log4j.Logger;

import java.util.function.BooleanSupplier;

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
 * <p>The two halves run at different rates, and that is the whole of why they are two calls. A
 * frame can be painted by several map passes, each binding a transform of its own - including
 * passes belonging to a map another mod composited, since the hook names no caller - so the read is
 * made per pass and the last one wins. The moment is per frame: an arrival latch stepped once per
 * pass would report the cursor leaving and reaching the cell it is resting on wherever two passes
 * of one frame resolve differently, which is a tick every frame under a motionless pointer.
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

    // What an arrival is answered with. A role rather than a sound and a level held here, both being
    // the host's look to state; asked at the moment rather than held, so the answer is composed
    // against the settings in force now - this publisher outlives any number of visits to the
    // settings screen.
    private final CellArrivalAnnouncer cellArrivalAnnouncer;

    // Whether the pass now running is one the cursor can be located against at all. Asked per pass
    // rather than settled once, because it turns on what is on screen and on a setting the player
    // can move mid-session. Supplied rather than composed here: which surfaces count as locatable is
    // the host's knowledge, and this end needs only the answer.
    private final BooleanSupplier isCursorLocatableOnThisPass;

    // Where the map's modelview is read back from. Held rather than resolved per frame because the
    // renderer underneath cannot change while the game runs, so the binding is a fixed collaborator
    // of this publisher's session-long life.
    private final ModelviewMatrixReader modelviewMatrixReader;

    // The last completed reading of where the cursor is: the hover it resolved to - MapHover.NONE
    // for a hit test that found no cell - together with the reading it came from, for the trace.
    // Null until some pass has completed one.
    //
    // Left standing by a pass that could not read at all, which is what keeps the moment honest
    // across a frame several passes paint: a run that learned nothing about the cursor must neither
    // announce nor forget, or a foreign map redrawing beside the real one would read as the cursor
    // leaving and returning to the cell it is resting on.
    private SettledCellReading settledCellReading;

    /**
     * @param modelviewMatrixReader       the binding the running renderer needs, from
     *                                    {@code ModelviewMatrixReaders#selectForActiveRenderer}
     * @param cellArrivalAnnouncer        what the host answers a reached cell with, from its own
     *                                    look - the tick {@code MapHoverCues#composeCellArrivalCue}
     *                                    names for this mod's map
     * @param isCursorLocatableOnThisPass whether the pass now running is one the cursor can be
     *                                    located against, from
     *                                    {@code MapHoverGates#isCursorLocatableOn}
     */
    public MapHoverPublisher(
            ModelviewMatrixReader modelviewMatrixReader,
            CellArrivalAnnouncer cellArrivalAnnouncer,
            BooleanSupplier isCursorLocatableOnThisPass) {

        this.modelviewMatrixReader = modelviewMatrixReader;
        this.cellArrivalAnnouncer = cellArrivalAnnouncer;
        this.isCursorLocatableOnThisPass = isCursorLocatableOnThisPass;
    }

    /**
     * Answers the moment the frame's passes settled on, if the cursor reached a cell it was not on.
     * Called once per frame, and from the frame's single preparation rather than from a pass, that
     * being the one place a run happens exactly once however many surfaces paint.
     *
     * <p>It reports on the passes already made rather than the ones about to run, since a frame's
     * answer is only final once its last pass has read. A tick therefore lands one frame after the
     * pointer crossed, which no player can hear; announcing per pass instead would sound the
     * crossings that never happened - one per frame, for as long as two passes of a frame disagree.
     */
    public void announceSettledArrival() {

        // No pass has read since the last frame's answer, so nothing is known to have changed.
        // Re-announcing the standing reading would be silent anyway - the latch already holds its
        // cell - but reading nothing is the truer statement of a frame that resolved nothing.
        if (settledCellReading == null) {
            return;
        }
        // A completed hit test that found no cell is a sighting: the cursor is on nothing. Forgetting
        // the cell is what makes stepping off one and back onto it a fresh arrival, the map being
        // mostly the space between cells.
        if (!settledCellReading.hover().isHovering()) {
            cellArrival.resetArrival();
            return;
        }
        if (!cellArrival.detectArrivalAt(settledCellReading.hover().hoveredSystemId())) {
            return;
        }
        cellArrivalAnnouncer.announceCellArrival();
        logHoverArrival(settledCellReading);
    }

    /**
     * Resolves the cursor to a cell and the cluster around it and publishes the result, or parks
     * the hover when the cursor is over no cell. Called on every pass of the frame that is allowed
     * to read, the last of them owning the answer.
     *
     * <p>Last write wins because the passes cannot be told apart from inside one. The hook belongs
     * to the sector map by convention alone, so a mod compositing a map of its own drives it too,
     * with its own zoom and pan - and it draws before the map screen is composited, a minimap
     * hanging off the campaign HUD. Reading on the frame's first pass would hand every frame's
     * answer to that transform; reading on all of them puts it on the one that drew last.
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

        // A pass the cursor cannot be located against is not a reading at all. The unproject would
        // still answer - a foreign map binds a real transform, just not the one the pointer is
        // over - and the point it lands on is inside real cells, so this cannot be left to the
        // guards below to catch: they only reject an answer that fails to arrive.
        if (!isCursorLocatableOnThisPass.getAsBoolean()) {
            parkHoverWithoutASighting();
            return;
        }
        // Nothing painted, so there are no cell shapes to test the cursor against. That is a
        // missing input rather than an answer about where the cursor is, so the sighting stands.
        if (targets == null) {
            parkHoverWithoutASighting();
            return;
        }
        // No reading means it could not be trusted - the cursor has left the window, the transform
        // is not the map's, or it will not invert. Which of the three it was does not change the
        // answer here: a cell resolved from an untrustworthy point is worse than none.
        var cursorRead = MapCursor.readCursorDuringMapPass(factor, modelviewMatrixReader);
        if (cursorRead == null) {
            parkHoverWithoutASighting();
            return;
        }
        var hoveredSystemId = CellHitTest.resolveSystemIdAt(
            cursorRead.worldPoint().x,
            cursorRead.worldPoint().y,
            targets.getFillPolygonByCellId());

        // A hit test that found nothing publishes the parked hover rather than a cell, and is kept
        // as the frame's sighting all the same: the cursor being over no cell is an answer.
        var hover = hoveredSystemId == null
            ? MapHover.NONE
            : new MapHover(
                hoveredSystemId,
                targets.getClusterIndex().findClusterMembersOf(hoveredSystemId));

        settledCellReading = new SettledCellReading(hover, cursorRead);
        MapHoverState.getInstance().publishHover(hover);
    }

    // Parks the hover for a pass whose inputs never arrived, leaving the last sighting standing.
    // Nothing was learned about where the cursor is, so nothing about where it was is worth
    // discarding - and the moment, being answered from the sighting, stays keyed to the cell the
    // cursor was actually seen on.
    //
    // The hover itself is still cleared, since a highlight left standing on an unverified cell is
    // the fault every guard here exists to avoid.
    private void parkHoverWithoutASighting() {
        MapHoverState.getInstance().clearHover();
    }

    // Traces each move onto a new cell: which system the cursor resolved to and how large a
    // cluster that pulls in - the two answers this pass exists to produce, and the ones a wrong
    // highlight is diagnosed against. Set KMU log verbosity to DEBUG in LunaLib to see it.
    //
    // The read behind it is described beside the answer, because a hover that names the wrong
    // system is not wrong at this end: the cell it resolved really does hold the point it was
    // given. What is wrong is upstream, in the pixel it started from or the viewport and modelview
    // that pixel was mapped through, and those are only diagnosable together with what came out.
    //
    // Described from the reading the announced cell came from, not from a fresh one, and not from
    // whatever the shared holder happens to say. A second read can capture a different transform -
    // it is taken live, and a frame can hold more than one map pass - so a line built that way would
    // account for a hover that never happened, and would do it most convincingly on exactly the
    // frames worth diagnosing.
    //
    // The exception is the clip, and it is the seam's own: it is read where the line is built, which
    // is here, in the frame's single preparation. That is the frame's FIRST admitted pass, while the
    // reading is the previous frame's LAST - so the clip names the pass that printed and not the one
    // that read, and under a mod whose minimap draws ahead of the map screen those are reliably
    // different passes. The fields are named for the printing pass to say so. Not worth closing by
    // carrying a clip in the reading: that would cost a stalling GL read on every hovering frame to
    // improve a line nobody sees unless they turned DEBUG on.
    private void logHoverArrival(SettledCellReading settledReading) {

        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("Map hover resolved; system="
            + settledReading.hover().hoveredSystemId()
            + " clusterMembers="
            + settledReading.hover().clusterMemberSystemIds()
            + "; read: "
            + settledReading.cursorRead().describeRead());
    }

    /**
     * One pass's completed answer about where the cursor is, kept whole so the moment and the line
     * that explains it come from the same reading. Carried together rather than as a cell id alone
     * because the trace is only worth having if it describes the transform the announced cell was
     * resolved through, and a frame can hold more than one.
     *
     * @param hover      what that reading resolved to, {@link MapHover#NONE} for a cursor over no
     *                   cell
     * @param cursorRead the pixel, the transform and the world point it was resolved through
     */
    private record SettledCellReading(MapHover hover, MapCursorRead cursorRead) {
    }
}
