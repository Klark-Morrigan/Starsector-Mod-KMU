package kmu.maplayers.politicalmap.base.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the terrain override the map relies on: {@code BaseTerrain.getActiveLayers}
 * throws by default, and the engine calls it the moment the terrain is added on a
 * fresh game - so failing to override it crashed onGameLoad. This pins the override:
 * an empty set (map-only terrain, no world layers) and no throw. The draw-list build
 * and GL emission live in their own collaborators and are covered there; the emission
 * itself runs only in-engine.
 */
final class PoliticalMapTerrainPluginTest {

    @Nested
    class GetActiveLayers {

        @Test
        void getActiveLayersReturnsEmptyWithoutThrowing() {
            var plugin = new PoliticalMapTerrainPlugin();

            assertThatCode(plugin::getActiveLayers).doesNotThrowAnyException();
            assertThat(plugin.getActiveLayers()).isEmpty();
        }
    }
}
