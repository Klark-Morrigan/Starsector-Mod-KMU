package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the codex covers the map whatever the cursor is doing, which is the whole of this cover:
 * it is raised full screen and takes every event, so there is no point on the screen that is still
 * the map.
 */
final class CodexMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileTheCodexIsUp() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so the codex covers the map wherever the pointer happens to be.
            assertThat(new CodexMapCover(() -> true).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileTheCodexIsNotUp() {
            // The ordinary case, and the one that keeps this cover from being a hover switch: with
            // the codex closed the map hovers as it did before this cover existed.
            assertThat(new CodexMapCover(() -> false).isCoveringCursor())
                .isFalse();
        }
    }
}
