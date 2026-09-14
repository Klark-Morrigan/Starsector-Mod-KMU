package kmu.maplayers.base.refresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the reading a consumer traces the signals it does not fold into its staleness with: which of
 * them a player has raised between two moments, named the way a line carrying it reads.
 *
 * <p>Posed over a real board rather than a stand-in, since what a reading is taken of is the
 * board's own counters and there is nothing between the two to stub.
 */
final class RefreshSignalRevisionsTest {

    private static final String NO_SIGNALS_RAISED = "none";

    private final MapLayerRefreshBoard board = new MapLayerRefreshBoard();

    @Nested
    class DescribeSignalsRaisedSince {

        @Test
        void namesASignalRaisedBetweenTheTwoReadings() {

            var before = readTheTracedSignals();

            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            assertThat(readTheTracedSignals().describeSignalsRaisedSince(before))
                .isEqualTo("FILTER");
        }

        @Test
        void namesEverySignalRaisedInTheOrderTheReadingWasTakenOver() {
            // A line read on every rebuild has to read the same way every rebuild, so the order is
            // the caller's listing rather than whatever order a hash lands them in.
            var before = readTheTracedSignals();

            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            assertThat(readTheTracedSignals().describeSignalsRaisedSince(before))
                .isEqualTo("FILTER,MAP_STYLE");
        }

        @Test
        void reportsNoneWhereNothingWasRaised() {
            // The common answer, and the one a reader passes over: most rebuilds follow a settings
            // change or a view switch rather than a sidebar flip.
            var before = readTheTracedSignals();

            assertThat(readTheTracedSignals().describeSignalsRaisedSince(before))
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void reportsNoneForASignalRaisedBeforeTheEarlierReading() {
            // What seeding the reading buys: a board carrying raises from before a consumer existed
            // must not have them reported against that consumer's first pass.
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            var before = readTheTracedSignals();

            assertThat(readTheTracedSignals().describeSignalsRaisedSince(before))
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void leavesOutASignalTheReadingWasNotTakenOver() {
            // A consumer traces what it chose to trace: a signal outside the reading is not its
            // business, and naming it would put another layer's flip on this one's line.
            var before = readTheTracedSignals();

            board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(readTheTracedSignals().describeSignalsRaisedSince(before))
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void namesASignalTheEarlierReadingNeverCovered() {
            // Nothing to have stood still against, so it counts as raised - which reports too much
            // rather than too little, and too little is what a trace must never do.
            var before = RefreshSignalRevisions.readRevisionsOf(
                board, MapLayerCommonRefreshSignal.FILTER);

            assertThat(readTheTracedSignals().describeSignalsRaisedSince(before))
                .isEqualTo("RECEDE_STYLE,MAP_STYLE");
        }
    }

    // The three sidebar preferences a rebuild traces, read in the order a description names them.
    private RefreshSignalRevisions readTheTracedSignals() {

        return RefreshSignalRevisions.readRevisionsOf(
            board,
            MapLayerCommonRefreshSignal.FILTER,
            MapLayerCommonRefreshSignal.RECEDE_STYLE,
            MapLayerCommonRefreshSignal.MAP_STYLE);
    }
}
