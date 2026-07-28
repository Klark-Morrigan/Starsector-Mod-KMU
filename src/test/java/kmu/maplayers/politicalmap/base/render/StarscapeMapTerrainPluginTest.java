package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * Pins the single thing this half adds to the political map's draw: it paints only while a starscape
 * map is on screen, so the pair never has both halves laying the same overlay down in one frame.
 * Reaching the inherited draw is observed at its first act, the active-view read, which is as far as
 * it gets here since no view is registered.
 */
final class StarscapeMapTerrainPluginTest {
    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapDrawsThroughWhileAStarscapeMapIsShowing() {
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                    mockStatic(PoliticalMapViewRegistry.class)) {
                new StarscapeMapTerrainPlugin(() -> true).renderOnMap(FACTOR, ALPHA_MULT);

                viewRegistryMock.verify(PoliticalMapViewRegistry::getActiveView);
            }
        }

        @Test
        void renderOnMapStandsAsideWhileNoStarscapeMapIsShowing() {
            // The engine calls this half in either mode, so standing aside is the only thing
            // keeping it off the map while the base half is the one already drawing there.
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                    mockStatic(PoliticalMapViewRegistry.class)) {
                new StarscapeMapTerrainPlugin(() -> false).renderOnMap(FACTOR, ALPHA_MULT);

                viewRegistryMock.verifyNoInteractions();
            }
        }
    }
}
