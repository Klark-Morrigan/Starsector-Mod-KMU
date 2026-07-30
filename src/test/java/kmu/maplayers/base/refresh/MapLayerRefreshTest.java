package kmu.maplayers.base.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the board's own contract, rather than reaching it through a layer that raises signals on
 * it: a request advances the counter of the signal it names and no other, a signal never raised
 * still reads as a number, and the stale-system set is a set that drains once.
 *
 * <p>The signals it raises are declared here rather than taken from the framework's or the
 * political map's sets, since the board's promise is that it holds a counter for whatever a layer
 * declares - a suite that could only show that for the sets already in the tree would not be
 * showing it at all.
 *
 * <p>Read as deltas rather than absolute counts, and drained before each case, because the board
 * is process-wide: any other suite that raises a signal would otherwise decide what this one
 * sees.
 */
class MapLayerRefreshTest {

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @Nested
    class GetRevision {

        @Test
        void getRevisionAnswersZeroForASignalNothingHasRaised() {
            // The board cannot enumerate the layers, so it cannot pre-seed their signals. An
            // unraised one has to read as a number all the same, or a consumer folding a signal
            // its producer has not fired yet would have nothing to fold.
            assertThat(MapLayerRefresh.getRevision(TestRefreshSignal.NEVER_RAISED)).isZero();
        }
    }

    @Nested
    class RequestRefresh {

        @Test
        void requestRefreshAdvancesOnlyTheSignalItNames() {
            var raisedBefore = MapLayerRefresh.getRevision(TestRefreshSignal.RAISED);
            var otherBefore = MapLayerRefresh.getRevision(TestRefreshSignal.OTHER);
            var commonBefore = MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY);

            MapLayerRefresh.requestRefresh(TestRefreshSignal.RAISED);

            // Two proofs from the one raise. A counter per signal is the whole point of the board:
            // a consumer folding one signal must not rebuild because a different one moved. And
            // the signal raised here is declared outside the framework entirely, which is what
            // shows the board is nobody's layer - nothing else in the tree can show that, since
            // every other caller passes a signal the framework or the political map declares, and
            // a board quietly narrowed back to those two sets would still satisfy all of them.
            assertThat(MapLayerRefresh.getRevision(TestRefreshSignal.RAISED))
                    .isEqualTo(raisedBefore + 1);
            assertThat(MapLayerRefresh.getRevision(TestRefreshSignal.OTHER))
                    .isEqualTo(otherBefore);
            assertThat(MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                    .isEqualTo(commonBefore);
        }

        @Test
        void requestRefreshReachesOneCounterFromEverySignalEqualToTheSame() {
            // The counter is keyed by equality rather than by identity, which is what lets a
            // producer and a consumer resolve their signal fresh rather than both having to hold
            // the one instance that seeded it.
            var before = MapLayerRefresh.getRevision(new NamedRefreshSignal("hull-hazard"));

            MapLayerRefresh.requestRefresh(new NamedRefreshSignal("hull-hazard"));

            assertThat(MapLayerRefresh.getRevision(new NamedRefreshSignal("hull-hazard")))
                    .isEqualTo(before + 1);
        }
    }

    @Nested
    class markSystemGroupingStale {

        @Test
        void markSystemGroupingStaleQueuesTheSystemForTheNextDrain() {
            MapLayerRefresh.markSystemGroupingStale("sys");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void markSystemGroupingStaleQueuesOneSystemOnceHoweverOftenItIsMarked() {
            MapLayerRefresh.markSystemGroupingStale("sys");
            MapLayerRefresh.markSystemGroupingStale("sys");

            // A system is stale or it is not, so a colony resized twice in one tick costs one
            // reshape rather than two.
            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void markSystemGroupingStaleIgnoresANullSystemId() {
            MapLayerRefresh.markSystemGroupingStale(null);

            // A producer with nothing to name must not put a null in the set for the drain to
            // hand a consumer that would then look it up.
            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    @Nested
    class drainStaleGroupingSystemIds {

        @Test
        void drainStaleGroupingSystemIdsReturnsEveryQueuedSystem() {
            MapLayerRefresh.markSystemGroupingStale("first");
            MapLayerRefresh.markSystemGroupingStale("second");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds())
                    .containsExactlyInAnyOrder("first", "second");
        }

        @Test
        void drainStaleGroupingSystemIdsEmptiesTheSetSoOneStalenessIsProcessedOnce() {
            MapLayerRefresh.markSystemGroupingStale("sys");
            MapLayerRefresh.drainStaleGroupingSystemIds();

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void drainStaleGroupingSystemIdsReturnsEmptyWhenNothingIsQueued() {
            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    // A set of signals no layer in the tree declares - the stand-in for whatever a second layer
    // watches. An enum, since that is the shape a layer is expected to reach for. NEVER_RAISED is
    // kept apart from the two the cases raise so the unraised-signal read has a constant nothing
    // in this process can have moved.
    private enum TestRefreshSignal implements MapLayerRefreshSignal {
        RAISED,
        OTHER,
        NEVER_RAISED;

        @Override
        public String getId() {
            return name();
        }
    }

    // A signal carrying its identity in a component rather than in a constant, which is the other
    // shape the key allows: anything whose equals and hashCode agree between the producer that
    // raises it and the consumer that reads it.
    private record NamedRefreshSignal(String id) implements MapLayerRefreshSignal {

        @Override
        public String getId() {
            return id;
        }
    }
}
