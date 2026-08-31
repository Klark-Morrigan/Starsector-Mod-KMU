package kmu.maplayers.base.layer;

import kmu.maplayers.base.installation.MapLayerInstallation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the empty view's two defining answers: the id a save stores for it, frozen because renaming
 * it silently resets every save holding that pick, and its lack of a renderer - the whole way "show
 * nothing" is expressed to the map surface, rather than a flag the surface tests for.
 */
final class NoLayerTest {

    @Nested
    class GetId {

        @Test
        void getIdReturnsTheFrozenSavedId() {
            assertThat(NoLayer.INSTANCE.getId()).isEqualTo("no_layer");
        }
    }

    @Nested
    class ResolveRenderer {

        @Test
        void resolveRendererIsNullBecauseThisLayerDrawsNothing() {
            // Whichever sector is asked about: there is nothing here for a sector to differ in, so
            // the installation goes unread and every sector gets the same answer.
            assertThat(NoLayer.INSTANCE.resolveRenderer(new MapLayerInstallation(null))).isNull();
        }
    }
}
