package kmu.maplayers.base.refresh;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What some signals stood at when a reading was taken, and which of them have been raised since.
 *
 * <p>For the signals a consumer does not fold into its staleness. Those still say something worth
 * having - a player touched a preference, and at what moment - but nothing downstream reads them,
 * so the only place that answer can land is beside the pass it preceded. Held as a reading rather
 * than asked of the board twice, since "has this moved" is a question about two moments and the
 * board keeps only the current one.
 *
 * <p>What a caller does with the answer is its own: the political map names it on the tag of the
 * scope its rebuild is measured under, so a capture says which flip a rebuild followed without a
 * reader pairing the row against the board's own lines by hand.
 *
 * <p>Immutable, and holding whichever signals the reading was taken over rather than all of them -
 * a consumer traces what it chose to trace.
 */
public final class RefreshSignalRevisions {

    /** What a reading naming no raise reports, so the answer is a word rather than a blank. */
    private static final String NO_SIGNALS_RAISED = "none";

    private static final String SIGNAL_SEPARATOR = ",";

    private final Map<MapLayerRefreshSignal, Integer> revisionBySignal;

    private RefreshSignalRevisions(Map<MapLayerRefreshSignal, Integer> revisionBySignal) {
        this.revisionBySignal = revisionBySignal;
    }

    /**
     * Reads where {@code signals} stand on {@code board} right now.
     *
     * @param board   the board the signals are raised on
     * @param signals the signals to read, in the order a description should name them
     * @return the reading, to be compared against a later one
     */
    public static RefreshSignalRevisions readRevisionsOf(
            MapLayerRefreshBoard board,
            MapLayerRefreshSignal... signals) {

        // Insertion ordered, so a description names the signals in the order the caller listed
        // them rather than in whatever order a hash lands them - a line read every rebuild has to
        // read the same way every rebuild.
        var revisions = new LinkedHashMap<MapLayerRefreshSignal, Integer>();

        for (var signal : signals) {
            revisions.put(signal, board.getRevision(signal));
        }
        return new RefreshSignalRevisions(revisions);
    }

    /**
     * Names the signals of this reading that stand higher than they did in {@code previous}.
     *
     * @param previous the earlier reading, commonly the one taken when the caller last acted
     * @return the raised signals' ids, separated by commas, or {@code "none"} where none moved. A
     *         signal the earlier reading did not cover counts as raised, that reading having no
     *         answer for it to have stood still against
     */
    public String describeSignalsRaisedSince(RefreshSignalRevisions previous) {

        var raised = new StringBuilder();

        for (var entry : revisionBySignal.entrySet()) {

            if (Objects.equals(entry.getValue(), previous.revisionBySignal.get(entry.getKey()))) {
                continue;
            }
            if (raised.length() > 0) {
                raised.append(SIGNAL_SEPARATOR);
            }
            raised.append(entry.getKey().getId());
        }
        return raised.length() == 0 ? NO_SIGNALS_RAISED : raised.toString();
    }
}
