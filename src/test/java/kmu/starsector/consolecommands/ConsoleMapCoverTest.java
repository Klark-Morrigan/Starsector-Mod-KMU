package kmu.starsector.consolecommands;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;
import kmlib.testfixtures.starsector.settings.ModStateScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.CONSOLE_COMMANDS;

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
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var consoleOverlay = new ConsoleCommandsOverlay(consolePresenceFake);

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, () ->
                assertThat(new ConsoleMapCover(consoleOverlay).isCoveringCursor())
                    .isTrue());
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoConsoleIs() {
            // Posed on an install without Console Commands, no scope reporting it enabled, so this
            // is the gate's fail-open answer rather than a closed console: the map hovers exactly as
            // it did before this cover existed.
            var consoleOverlay = new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake());

            assertThat(new ConsoleMapCover(consoleOverlay).isCoveringCursor())
                .isFalse();
        }
    }
}
