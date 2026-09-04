package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the codex covers the map whatever the cursor is doing, which is the whole of this cover.
 * Worth pinning rather than reading off the shape, because the panel is plainly a box in the middle
 * of the screen and a reader could reasonably expect one: what covers the rest is the screen-spanning
 * backdrop taking the events behind it, and a cursor test added later would let the map answer the
 * pointer over exactly the dimmed remainder the player can see and cannot reach.
 */
final class CodexMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileTheCodexIsUp() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so the codex covers the map wherever the pointer happens to be - including
            // the dimmed screen around the panel, which is most of where the map is showing.
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
