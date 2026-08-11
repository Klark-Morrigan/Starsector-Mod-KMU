package kmu.maplayers.base.hover.cover;

import kmu.starsector.consolecommands.ConsoleOverlayFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that an open console covers the map whatever the cursor is doing, which is the whole of this
 * cover: a console takes the screen, so there is no point on it that is still the map.
 */
final class ConsoleMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileAConsoleIsOpen() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so a console covers the map wherever the pointer happens to be.
            var consoleOverlayFake = new ConsoleOverlayFake();
            
            consoleOverlayFake.openConsole();

            assertThat(new ConsoleMapCover(consoleOverlayFake).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoConsoleIs() {
            // Also the answer an install without Console Commands gets, the role failing open to
            // "no console", so the map hovers as it did before this cover existed.
            var consoleOverlayFake = new ConsoleOverlayFake();

            assertThat(new ConsoleMapCover(consoleOverlayFake).isCoveringCursor())
                .isFalse();
        }
    }
}
