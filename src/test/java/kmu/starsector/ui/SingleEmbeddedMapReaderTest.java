package kmu.starsector.ui;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.layout.PositionFake;
import kmlib.testfixtures.starsector.ui.map.probes.PlacedSectorMapWidgetFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one reading both rules that act on another mod's map surface share: which surface, if
 * any, can be spoken about this frame.
 *
 * <p>The two ways it answers nothing are what make the rules downstream safe. None found is the
 * ordinary state before a mod has built its panel, or after a walk that could not be made; more than
 * one leaves no way to say which surface either rule was meant for. Neither is something a caller
 * acting on a single surface could tell apart or act on differently.
 */
final class SingleEmbeddedMapReaderTest {

    private static final float FULLY_DRAWN = 1f;

    @Nested
    class ResolveSingleEmbeddedMap {

        @Test
        void resolveSingleEmbeddedMapAnswersTheOneSurfaceOnScreen() {
            var embeddedMap = createEmbeddedMap();

            assertThat(new SingleEmbeddedMapReader(() -> List.of(embeddedMap))
                    .resolveSingleEmbeddedMap())
                .isSameAs(embeddedMap);
        }

        @Test
        void resolveSingleEmbeddedMapAnswersNothingWithNoneFound() {
            // The walk ran before the panel was built, or the reach into the tree broke. Both mean
            // there is nothing on screen either rule has been given leave to act on.
            assertThat(new SingleEmbeddedMapReader(List::of).resolveSingleEmbeddedMap())
                .isNull();
        }

        @Test
        void resolveSingleEmbeddedMapAnswersNothingWithMoreThanOneFound() {
            // The precondition both rules rest on, for their own reasons: the frame carries one
            // transform, and the mode that permits acting names a mod the widgets cannot be matched
            // against.
            var embeddedMaps = List.of(createEmbeddedMap(), createEmbeddedMap());

            assertThat(new SingleEmbeddedMapReader(() -> embeddedMaps).resolveSingleEmbeddedMap())
                .isNull();
        }
    }

    // A placed, drawn map surface. Nothing here reads its box, so one shape serves every case.
    private static EmbeddedMap createEmbeddedMap() {
        return new EmbeddedMap(
            new PlacedSectorMapWidgetFake(
                new PositionFake(new Rectangle(20f, 30f, 200f, 150f)), FULLY_DRAWN),
            List.of());
    }
}
