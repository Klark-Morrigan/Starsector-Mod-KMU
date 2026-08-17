package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a raised pause menu covers the map whatever the cursor is doing, which is the whole of
 * this cover: the menu takes the screen, so there is no point on it that is still the map.
 */
final class PauseMenuMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileThePauseMenuIsUp() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so the menu covers the map wherever the pointer happens to be.
            assertThat(new PauseMenuMapCover(() -> true).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoMenuIsUp() {
            // The ordinary case, and the one that keeps this cover from being a hover switch: with
            // no menu up the map hovers as it did before this cover existed.
            assertThat(new PauseMenuMapCover(() -> false).isCoveringCursor())
                .isFalse();
        }
    }
}
