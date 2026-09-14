package kmu.maplayers.base.refresh;

/**
 * Which of a few named signals have been raised since the last time anybody asked, over one
 * sector's board.
 *
 * <p>For the signals a consumer traces rather than acts on. A counter reports that somebody
 * clicked something, which is worth reading beside the pass that followed even where nothing folds
 * it into staleness - so this keeps the reading the last ask left and reports the difference, and a
 * caller writes the answer onto whatever line it is already producing.
 *
 * <p>Apart from the consumer that traces: holding it there would put a baseline that decides
 * nothing beside the ones that decide everything, and a reader could not tell which was which.
 * Seeded at construction, so raises made before this existed are never reported: a board outlives
 * any one consumer and may already carry raises from a load.
 */
public final class RefreshSignalTracker {

    // The board the traced signals are raised on. One sector's, since every signal on it is.
    private final MapLayerRefreshBoard board;

    // The signals this traces and no others, in the order a description names them.
    private final MapLayerRefreshSignal[] tracedSignals;

    // Where those signals stood when this was last asked.
    private RefreshSignalRevisions revisionsAtLastReading;

    /**
     * @param board         the board whose raises are traced
     * @param tracedSignals the signals to trace, in the order a description should name them
     */
    public RefreshSignalTracker(MapLayerRefreshBoard board, MapLayerRefreshSignal... tracedSignals) {

        this.board = board;
        this.tracedSignals = tracedSignals.clone();
        this.revisionsAtLastReading = RefreshSignalRevisions.readRevisionsOf(board, tracedSignals);
    }

    /**
     * Names the traced signals raised since the last ask, and advances to now.
     *
     * <p>Advancing on the ask is what makes each raise reported once: a caller writing this onto
     * the line of the pass it preceded is the only reader, so a raise named twice would read as two
     * flips.
     *
     * @return the raised signals' IDs, separated by commas, or a word for none
     */
    public String describeRaisesSinceTheLastReading() {

        var revisionsNow = RefreshSignalRevisions.readRevisionsOf(board, tracedSignals);
        var raisedSince = revisionsNow.describeSignalsRaisedSince(revisionsAtLastReading);

        revisionsAtLastReading = revisionsNow;

        return raisedSince;
    }
}
