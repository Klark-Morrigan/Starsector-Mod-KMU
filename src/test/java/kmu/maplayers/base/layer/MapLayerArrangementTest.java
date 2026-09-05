package kmu.maplayers.base.layer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two things the arrangement value itself answers for: which ids the player has taken off
 * the bar, and that the lists it was built from cannot be moved out from under a reader afterwards.
 *
 * <p>The second is not incidental. Both lists arrive from something still being edited - a file
 * being parsed, or a dialog's working state - so a value holding the caller's own list would report
 * an arrangement that changed without anything recording one.
 */
final class MapLayerArrangementTest {

    @Nested
    class IsLayerHidden {

        @Test
        void isLayerHiddenAnswersForAnIdTheArrangementNames() {

            var arrangement = new MapLayerArrangement(List.of(), List.of("beta"));

            assertThat(arrangement.isLayerHidden("beta"))
                .isTrue();
        }

        @Test
        void isLayerHiddenAnswersNoForAnIdTheArrangementDoesNotName() {
            // Hiding is stated rather than assumed: a layer the player has never touched is on the
            // bar, which is what an install with no arrangement at all has to read as.
            var arrangement = new MapLayerArrangement(List.of("beta"), List.of());

            assertThat(arrangement.isLayerHidden("beta"))
                .isFalse();
        }
    }

    @Nested
    class Construction {

        @Test
        void constructionCopiesTheOrderItWasBuiltFrom() {

            var editedOrder = new ArrayList<>(List.of("alpha", "beta"));
            var arrangement = new MapLayerArrangement(editedOrder, List.of());

            editedOrder.add("gamma");

            assertThat(arrangement.orderedLayerIds())
                .containsExactly("alpha", "beta");
        }

        @Test
        void constructionCopiesTheHidingItWasBuiltFrom() {

            var editedHiding = new ArrayList<>(List.of("alpha"));
            var arrangement = new MapLayerArrangement(List.of(), editedHiding);

            editedHiding.add("beta");

            assertThat(arrangement.hiddenLayerIds())
                .containsExactly("alpha");
        }
    }
}
