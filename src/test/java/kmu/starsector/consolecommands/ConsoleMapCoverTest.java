package kmu.starsector.consolecommands;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

        // The gate answers "no console" on an install without Console Commands, so a case that
        // opens one has to be posed on an install that has it.
        @BeforeEach
        void installEnabledConsoleCommands() {
            StarsectorSettingsFake.installSettingsWithEnabledMods(CONSOLE_COMMANDS::equals);
        }

        @AfterEach
        void clearGameSettings() {
            StarsectorSettingsFake.clearSettings();
        }

        @Test
        void isCoveringCursorAnswersCoveredWhileAConsoleIsOpen() {
            // Asserted with no cursor arranged at all, which is the point: this cover reads no
            // geometry, so a console covers the map wherever the pointer happens to be.
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var consoleOverlay = new ConsoleCommandsOverlay(consolePresenceFake);

            consolePresenceFake.openConsole();

            assertThat(new ConsoleMapCover(consoleOverlay).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoConsoleIs() {
            // Also the answer an install without Console Commands gets, the role failing open to
            // "no console", so the map hovers as it did before this cover existed.
            var consoleOverlay = new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake());

            assertThat(new ConsoleMapCover(consoleOverlay).isCoveringCursor())
                .isFalse();
        }
    }
}
