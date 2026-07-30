package kmu.maplayers.base.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the board's own contract, rather than reaching it through a layer that raises signals on
 * it: each request advances its own counter and no other, and the stale-system set is a set that
 * drains once.
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
    class RequestGeometryRefresh {

        @Test
        void requestGeometryRefreshAdvancesOnlyTheGeometryRevision() {
            var geometryBefore = MapLayerRefresh.getGeometryRevision();
            var allianceBefore = MapLayerRefresh.getAllianceRevision();

            MapLayerRefresh.requestGeometryRefresh();

            assertThat(MapLayerRefresh.getGeometryRevision()).isEqualTo(geometryBefore + 1);
            assertThat(MapLayerRefresh.getAllianceRevision()).isEqualTo(allianceBefore);
        }
    }

    @Nested
    class RequestAllianceRefresh {

        @Test
        void requestAllianceRefreshAdvancesOnlyTheAllianceRevision() {
            var allianceBefore = MapLayerRefresh.getAllianceRevision();
            var geometryBefore = MapLayerRefresh.getGeometryRevision();

            MapLayerRefresh.requestAllianceRefresh();

            assertThat(MapLayerRefresh.getAllianceRevision()).isEqualTo(allianceBefore + 1);
            assertThat(MapLayerRefresh.getGeometryRevision()).isEqualTo(geometryBefore);
        }
    }

    @Nested
    class RequestRecedeStyleRefresh {

        @Test
        void requestRecedeStyleRefreshAdvancesOnlyTheRecedeStyleRevision() {
            var recedeBefore = MapLayerRefresh.getRecedeStyleRevision();
            var mapStyleBefore = MapLayerRefresh.getMapStyleRevision();

            MapLayerRefresh.requestRecedeStyleRefresh();

            assertThat(MapLayerRefresh.getRecedeStyleRevision()).isEqualTo(recedeBefore + 1);
            assertThat(MapLayerRefresh.getMapStyleRevision()).isEqualTo(mapStyleBefore);
        }
    }

    @Nested
    class RequestFilterRefresh {

        @Test
        void requestFilterRefreshAdvancesOnlyTheFilterRevision() {
            var filterBefore = MapLayerRefresh.getFilterRevision();
            var recedeBefore = MapLayerRefresh.getRecedeStyleRevision();

            MapLayerRefresh.requestFilterRefresh();

            assertThat(MapLayerRefresh.getFilterRevision()).isEqualTo(filterBefore + 1);
            assertThat(MapLayerRefresh.getRecedeStyleRevision()).isEqualTo(recedeBefore);
        }
    }

    @Nested
    class RequestMapStyleRefresh {

        @Test
        void requestMapStyleRefreshAdvancesOnlyTheMapStyleRevision() {
            var mapStyleBefore = MapLayerRefresh.getMapStyleRevision();
            var filterBefore = MapLayerRefresh.getFilterRevision();

            MapLayerRefresh.requestMapStyleRefresh();

            assertThat(MapLayerRefresh.getMapStyleRevision()).isEqualTo(mapStyleBefore + 1);
            assertThat(MapLayerRefresh.getFilterRevision()).isEqualTo(filterBefore);
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
}
