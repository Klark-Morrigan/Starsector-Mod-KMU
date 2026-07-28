package kmu.maplayers.base.layer;

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
    class GetMapRenderer {

        @Test
        void getMapRendererIsNullBecauseThisLayerDrawsNothing() {
            assertThat(NoLayer.INSTANCE.getMapRenderer()).isNull();
        }
    }
}
