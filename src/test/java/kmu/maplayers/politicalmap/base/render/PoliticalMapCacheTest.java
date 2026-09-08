package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the cache's two guarantees that hold outside a running game: a rebuild that throws leaves
 * something safe to draw, and releasing the cache drops everything it built for its sector. The
 * incremental rebuild paths need a live sector and are covered by the integration tests.
 */
final class PoliticalMapCacheTest {

    // The screen a refresh is driven for. A stand-in rather than one of the two live screens: neither
    // guarantee here turns on which panel the frame was prepared for.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    // The machinery the cache under test belongs to, installed on no sector - which is what makes
    // the rebuild throw part way through, the case below being about what a cache leaves drawable
    // when it does.
    private final MapLayerInstallation installation = new MapLayerInstallation(null);

    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);

    @Nested
    class Refresh {

        @Test
        void refreshInstallsAnEmptyPlaceholderWhenTheRebuildThrows() {
            // No sector, so the rebuild throws part way through. The renderer must still find a draw
            // list rather than dereference a null one, and the next frame retries.
            var cache = new PoliticalMapCache(installation);

            // Every settings class the refresh reads is stubbed inert: the revision counter is the
            // framework's, the cell seed inputs and the debug gate this layer's.
            try (var settingsMock = mockStatic(KmuLunaSettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                cache.refresh(viewMock, SCREEN);
            }

            assertThat(cache.getTerritories())
                .isNotNull();
            assertThat(cache.getTerritories().getStyledCellByCellId())
                .isEmpty();
        }
    }

    @Nested
    class DisposeCachedState {

        @Test
        void disposeCachedStateDropsTheDrawListsBuiltForItsSector() {
            // What a sector removed mid-session leaves behind if this does nothing: the cached names
            // each own a GL buffer, so the drop is what frees them rather than leaving them to
            // LazyLib's finalizer sweep.
            var cache = new PoliticalMapCache(installation);

            try (var settingsMock = mockStatic(KmuLunaSettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                cache.refresh(viewMock, SCREEN);
            }

            assertThat(cache.getTerritories())
                .isNotNull();

            cache.disposeCachedState();

            assertThat(cache.getTerritories())
                .isNull();
            assertThat(cache.getBorderStageOverlay())
                .isNull();
            assertThat(cache.getClusterAnchors())
                .isEmpty();
            assertThat(cache.getFactionLabels())
                .isEmpty();
        }

        @Test
        void disposeCachedStateIsSafeBeforeAnythingHasBeenBuilt() {
            // Reached for a sector installed on with the map never opened, when there are no draw
            // lists and no GL resources to release yet.
            var cache = new PoliticalMapCache(installation);

            cache.disposeCachedState();

            assertThat(cache.getTerritories())
                .isNull();
            assertThat(cache.getFactionLabels())
                .isEmpty();
        }
    }
}
