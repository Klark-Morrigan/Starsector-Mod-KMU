package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;
import kmlib.testfixtures.starsector.settings.ModStateScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.CONSOLE_COMMANDS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that either claimant alone stands the panel down, and that neither is asked to speak for the other.
 *
 * <p>Worth pinning as a disjunction rather than through the hosts, because the two reads fail open
 * independently: an install without the console mod answers no console for the whole session, and a build
 * whose core UI cannot be walked answers no modal for the whole session. Composed with an AND either of
 * those would silence the other, and nothing at a host would show it.
 *
 * <p>The short-circuit is pinned too. The modal read walks the core UI's children where the console read is
 * a field, so an order that asked the walk first would pay for it on every frame of every screen - which is
 * the sort of cost that only ever shows up as a frame rate.
 */
class ScreenClaimTest {

    @Nested
    class IsScreenClaimed {

        @Test
        void isScreenClaimedIsFalseWithNeitherAConsoleNorAModalUp() {

            assertThat(ScreenClaims.createUnclaimedScreen().isScreenClaimed())
                .isFalse();
        }

        @Test
        void isScreenClaimedIsTrueWhileAConsoleIsUp() {

            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var claim = ScreenClaims
                .createClaimOverConsole(new ConsoleCommandsOverlay(consolePresenceFake));

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, () ->
                assertThat(claim.isScreenClaimed())
                    .isTrue());
        }

        @Test
        void isScreenClaimedIsTrueWhileAModalIsUpWithNoConsole() {
            // The half added here, and the one that has to answer on its own: the console read is silent on
            // an install without that mod, which is most of them.
            assertThat(ScreenClaims.createScreenClaimedByAModal().isScreenClaimed())
                .isTrue();
        }

        @Test
        void isScreenClaimedLeavesTheModalReadUntakenWhileAConsoleIsUp() {
            // The modal read walks the core UI's children; the console read is a field. Asked in the other
            // order the walk is paid for on every frame, whatever else is on screen.
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var modalReadCount = new AtomicInteger();
            var claim = new ScreenClaim(
                new ConsoleCommandsOverlay(consolePresenceFake),
                () -> {
                    modalReadCount.incrementAndGet();
                    return false;
                });

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, claim::isScreenClaimed);

            assertThat(modalReadCount)
                .hasValue(0);
        }
    }
}
