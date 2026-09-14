package kmu.maplayers.base.refresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the trace a consumer writes onto its own line: a raise of a traced signal is named once,
 * the reading advances on the ask so it is never named twice, an untraced signal is never named,
 * and raises made before the tracker existed belong to whatever ran before it.
 *
 * <p>Each case builds its own board, so the raises it reads are only the ones it made: a board is
 * one sector's, and one made here is nobody's.
 */
class RefreshSignalTrackerTest {

    // What a description names when nothing moved, as the reading reports it.
    private static final String NO_SIGNALS_RAISED = "none";

    private final MapLayerRefreshBoard board = new MapLayerRefreshBoard();

    @Nested
    class DescribeRaisesSinceTheLastReading {

        @Test
        void describeRaisesSinceTheLastReadingNamesATracedSignalRaisedSinceConstruction() {

            var tracker = buildTrackerOf(MapLayerCommonRefreshSignal.FILTER);

            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            assertThat(tracker.describeRaisesSinceTheLastReading())
                .isEqualTo(MapLayerCommonRefreshSignal.FILTER.getId());
        }

        @Test
        void describeRaisesSinceTheLastReadingNamesNothingWhileNoTracedSignalMoves() {

            var tracker = buildTrackerOf(MapLayerCommonRefreshSignal.FILTER);

            assertThat(tracker.describeRaisesSinceTheLastReading())
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void describeRaisesSinceTheLastReadingAdvancesSoOneRaiseIsNamedOnce() {
            // The reading advances on the ask, so a caller writing this onto the line of each pass
            // reports a flip against the pass that followed it rather than against every pass after.
            var tracker = buildTrackerOf(MapLayerCommonRefreshSignal.FILTER);

            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
            tracker.describeRaisesSinceTheLastReading();

            assertThat(tracker.describeRaisesSinceTheLastReading())
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void describeRaisesSinceTheLastReadingNamesNoSignalItDoesNotTrace() {
            // A tracer names what it chose to trace: a consumer folding a signal into its own
            // staleness has already accounted for it, and naming it here would report it twice.
            var tracker = buildTrackerOf(MapLayerCommonRefreshSignal.FILTER);

            board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(tracker.describeRaisesSinceTheLastReading())
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void describeRaisesSinceTheLastReadingIgnoresRaisesMadeBeforeItWasBuilt() {
            // A board outlives any one consumer and may already carry raises from a load, so
            // reporting those would name flips that happened before this tracker existed.
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            assertThat(buildTrackerOf(MapLayerCommonRefreshSignal.FILTER)
                    .describeRaisesSinceTheLastReading())
                .isEqualTo(NO_SIGNALS_RAISED);
        }

        @Test
        void describeRaisesSinceTheLastReadingNamesEveryRaisedSignalInTheOrderItTracesThem() {

            var tracker = buildTrackerOf(
                MapLayerCommonRefreshSignal.FILTER,
                MapLayerCommonRefreshSignal.MAP_STYLE);

            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            // The traced order rather than the order they were raised in, so one line read every
            // pass reads the same way every pass.
            assertThat(tracker.describeRaisesSinceTheLastReading())
                .isEqualTo(MapLayerCommonRefreshSignal.FILTER.getId()
                    + "," + MapLayerCommonRefreshSignal.MAP_STYLE.getId());
        }
    }

    private RefreshSignalTracker buildTrackerOf(MapLayerRefreshSignal... tracedSignals) {
        return new RefreshSignalTracker(board, tracedSignals);
    }
}
