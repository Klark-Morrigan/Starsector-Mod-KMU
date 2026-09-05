package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that this mod's own bar-arranging dialog covers the map whatever the cursor is doing, which is
 * the whole of this cover: the dialog claims every event outside its own box, so there is no point on
 * the screen that is still the map.
 *
 * <p>Its own cover rather than a case of the modal one beside it, and that is what is really pinned
 * here: a dialog this mod stands up carries none of the game's modal shape, so the walk behind
 * {@link ModalDialogMapCover} answers no on exactly the frames this has to answer yes.
 */
final class ArrangementDialogMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileTheDialogIsUp() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so the dialog covers the map wherever the pointer happens to be.
            assertThat(new ArrangementDialogMapCover(() -> true).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileTheDialogIsDown() {
            // The ordinary case, and the one that keeps this cover from being a hover switch: with the
            // dialog down the map hovers as it did before this cover existed.
            assertThat(new ArrangementDialogMapCover(() -> false).isCoveringCursor())
                .isFalse();
        }
    }
}
