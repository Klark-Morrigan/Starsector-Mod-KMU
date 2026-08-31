package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.coreui.ModalDialogState;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;
import kmlib.testfixtures.starsector.settings.ModStateScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.CONSOLE_COMMANDS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    private static final float TOLERANCE = 0.0001f;

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
        void isScreenClaimedIsTrueForAModalStillFadingIn() {
            // The half that must not follow the fade: a modal takes every event outside its box from
            // the frame it is raised, when its brightness is still nothing. Read off the strength
            // below, the panel would go on routing clicks into a dialog already eating them.
            assertThat(ScreenClaims.createScreenClaimedByAModalAt(0f).isScreenClaimed())
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
                    return ModalDialogState.NONE;
                });

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, claim::isScreenClaimed);

            assertThat(modalReadCount)
                .hasValue(0);
        }
    }

    @Nested
    class ResolveClaimStrength {

        @Test
        void resolveClaimStrengthFollowsAModalThroughItsOwnFade() {
            // The whole reason this sits beside the crisp read: the modal darkens the screen by this
            // same curve, so a panel painted at what is left of it thins as the backdrop deepens.
            assertThat(ScreenClaims.createScreenClaimedByAModalAt(0.4f).resolveClaimStrength())
                .isCloseTo(0.4f, within(TOLERANCE));
        }

        @Test
        void resolveClaimStrengthIsNothingOnAnUnclaimedScreen() {

            assertThat(ScreenClaims.createUnclaimedScreen().resolveClaimStrength())
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void resolveClaimStrengthIsFullForAConsoleThatReportsNoFade() {
            // A claimant that snaps is one the panel should snap with. Inventing a fade for the
            // console would leave the panel half dissolved against something that never moved.
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var claim = ScreenClaims
                .createClaimOverConsole(new ConsoleCommandsOverlay(consolePresenceFake));

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, () ->
                assertThat(claim.resolveClaimStrength())
                    .isCloseTo(1f, within(TOLERANCE)));
        }
    }
}
