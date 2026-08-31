package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a modal raised over a core screen covers the map whatever the cursor is doing, which is
 * the whole of this cover: the modal takes every event outside its own box, so there is no point on
 * the screen that is still the map.
 */
final class ModalDialogMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileAModalIsUp() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so the modal covers the map wherever the pointer happens to be.
            assertThat(new ModalDialogMapCover(() -> true).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoModalIsUp() {
            // The ordinary case, and the one that keeps this cover from being a hover switch: with
            // no modal up the map hovers as it did before this cover existed.
            assertThat(new ModalDialogMapCover(() -> false).isCoveringCursor())
                .isFalse();
        }
    }
}
