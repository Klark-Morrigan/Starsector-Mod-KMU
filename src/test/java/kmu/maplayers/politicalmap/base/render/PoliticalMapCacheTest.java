package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the cache's two guarantees that hold outside a running game: a rebuild that throws leaves
 * something safe to draw, and a discard returns the whole cache to its rebuild-forcing seed. The
 * incremental rebuild paths need a live sector and are covered by the integration tests.
 */
final class PoliticalMapCacheTest {
    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);

    @Nested
    class Refresh {

        @Test
        void refreshInstallsAnEmptyPlaceholderWhenTheRebuildThrows() {
            // No sector, so the rebuild throws part way through. The renderer must still find a draw
            // list rather than dereference a null one, and the next frame retries.
            var cache = new PoliticalMapCache();

            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                cache.refresh(viewMock);
            }

            assertThat(cache.getTerritories()).isNotNull();
            assertThat(cache.getTerritories().getStyledCellByCellId()).isEmpty();
        }
    }

    @Nested
    class DiscardCachedState {

        @Test
        void discardCachedStateDropsTheDrawListsBuiltForTheSectorBeingLeft() {
            // The regression this guards: the holder outlives a save, so draw lists carried into a
            // second load would paint the previous sector with nothing to mark them stale - the
            // revision counters are process-wide and do not move on load.
            var cache = new PoliticalMapCache();
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                cache.refresh(viewMock);
            }
            assertThat(cache.getTerritories()).isNotNull();

            cache.discardCachedState();

            assertThat(cache.getTerritories()).isNull();
            assertThat(cache.getDebugTerritories()).isNull();
            assertThat(cache.getClusterAnchors()).isEmpty();
            assertThat(cache.getFactionLabels()).isEmpty();
        }

        @Test
        void discardCachedStateIsSafeBeforeAnythingHasBeenBuilt() {
            // Reached on the first load of a session, when there are no draw lists and no GL
            // resources to release yet.
            var cache = new PoliticalMapCache();

            cache.discardCachedState();

            assertThat(cache.getTerritories()).isNull();
            assertThat(cache.getFactionLabels()).isEmpty();
        }
    }
}
