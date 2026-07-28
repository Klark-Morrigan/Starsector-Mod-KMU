package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what this renderer decides for itself before any drawing happens: that a deselected view
 * costs a frame nothing, and that a game load leaves nothing of the previous sector behind. What the
 * discard actually empties is {@link PoliticalMapCacheTest}'s; the cache refresh, the cursor read and
 * the GL emission run only in-engine and are covered by their own collaborators.
 */
final class PoliticalMapLayerRendererTest {
    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapStandsDownWhileNoViewIsSelected() {
            // The tab is open with every view deselected. Standing down on that one read is what
            // keeps a dark overlay near-free per frame: nothing downstream is consulted, not even the
            // hover toggle that gates the cheapest of the work below it.
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                viewRegistryMock.when(PoliticalMapViewRegistry::getActiveView).thenReturn(null);

                PoliticalMapLayerRenderer.INSTANCE.renderOnMap(FACTOR, ALPHA_MULT);

                settingsMock.verifyNoInteractions();
            }
        }
    }

    @Nested
    class DiscardStateFromPreviousSave {

        @Test
        void discardStateFromPreviousSaveRunsBeforeAnySectorHasBeenDrawn() {
            // Called on every load including the session's first, when there is nothing built to drop
            // and no hover to park.
            assertThatCode(PoliticalMapLayerRenderer.INSTANCE::discardStateFromPreviousSave)
                    .doesNotThrowAnyException();
        }
    }
}
