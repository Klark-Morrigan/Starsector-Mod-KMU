package kmu.maplayers.base.layer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the one subtraction a question about painting makes: the empty view, whose whole job is to draw
 * nothing, and no other layer - a layer another mod ships is taken as painting whatever else is true of
 * it, there being nothing on the seam that says otherwise.
 *
 * <p>And that the count is of whatever list it is handed, so the same reading answers a roster and one
 * screen's row.
 */
final class PaintingLayersTest {

    @Nested
    class CountPaintingLayers {

        @Test
        void countPaintingLayersCountsEveryLayerThatDraws() {

            var layers = List.of(mock(MapLayer.class), mock(MapLayer.class));

            assertThat(PaintingLayers.countPaintingLayers(layers))
                .isEqualTo(2);
        }

        @Test
        void countPaintingLayersLeavesTheEmptyViewOutOfTheCount() {
            // The reading the opener's gate turns on: a row of the empty view and one layer is a row of
            // one layer, since moving a tab past a tab that draws nothing changes nothing on the map.
            var layers = List.of(NoLayer.INSTANCE, mock(MapLayer.class));

            assertThat(PaintingLayers.countPaintingLayers(layers))
                .isEqualTo(1);
        }

        @Test
        void countPaintingLayersCountsNothingForTheEmptyViewAlone() {

            assertThat(PaintingLayers.countPaintingLayers(List.of(NoLayer.INSTANCE)))
                .isZero();
        }

        @Test
        void countPaintingLayersCountsNothingForARowWithNoLayersInIt() {
            // The reading a process that has registered nothing gives, which is a real state: the roster
            // is empty until a composition root registers its first layer.
            assertThat(PaintingLayers.countPaintingLayers(List.of()))
                .isZero();
        }
    }

    @Nested
    class IsLayerPainting {

        @Test
        void isLayerPaintingAnswersNoForTheEmptyView() {

            assertThat(PaintingLayers.isLayerPainting(NoLayer.INSTANCE))
                .isFalse();
        }

        @Test
        void isLayerPaintingAnswersYesForEveryOtherLayer() {
            // Nothing on the seam declares whether a layer draws - a renderer is asked for per sector -
            // so the empty view is the exception and every other registered layer is taken as painting.
            assertThat(PaintingLayers.isLayerPainting(mock(MapLayer.class)))
                .isTrue();
        }
    }
}
