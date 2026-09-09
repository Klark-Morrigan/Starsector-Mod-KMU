package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.coreui.OverlayPresence;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;
import kmlib.testfixtures.starsector.settings.ModStateScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.CONSOLE_COMMANDS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins that any one claimant alone stands the panel down, and that none is asked to speak for another.
 *
 * <p>Worth pinning as a disjunction rather than through the hosts, because the reads fail open
 * independently: an install without the console mod answers no console for the whole session, and a build
 * whose core UI cannot be walked - or whose app state cannot be reached - answers no modal and no codex for
 * the whole session. Composed with an AND any of those would silence the others, and nothing at a host would
 * show it - this mod's own dialog included, which nothing else on the list can speak for.
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
        void isScreenClaimedIsFalseWithNoConsoleCodexOrModalUp() {

            assertThat(ScreenClaims.createUnclaimedScreen().isScreenClaimed())
                .isFalse();
        }

        @Test
        void isScreenClaimedIsTrueWhileTheCodexIsUp() {
            // Its own read rather than a case of the modal beside it: the codex is raised outside the
            // core UI, so the modal read answers no on exactly the frames this one has to answer yes.
            assertThat(ScreenClaims.createScreenClaimedByTheCodex().isScreenClaimed())
                .isTrue();
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
        void isScreenClaimedIsTrueWhileTheArrangementDialogIsUp() {
            // Its own read rather than a case of the modal beside it, and this is the one that would go
            // unnoticed: this mod's dialog descends from nothing of the game's, so the modal walk finds
            // no dialog on exactly the frames one is standing over the panel.
            assertThat(ScreenClaims.createScreenClaimedByTheArrangementDialog().isScreenClaimed())
                .isTrue();
        }

        @Test
        void isScreenClaimedIsTrueForTheArrangementDialogStillFadingIn() {
            // Raised is the half input reads, and it is true from the frame the dialog opens, when
            // its paint is still at nothing.
            assertThat(ScreenClaims.createScreenClaimedByTheArrangementDialogAt(true, 0f).isScreenClaimed())
                .isTrue();
        }

        @Test
        void isScreenClaimedIsFalseForTheArrangementDialogOnlyFadingOut() {
            // The one claimant that lets go before its fade has run: dismissed on the press, it claims
            // nothing over its own dissolving box, and neither may the panel on its behalf.
            assertThat(ScreenClaims.createScreenClaimedByTheArrangementDialogAt(false, 0.5f).isScreenClaimed())
                .isFalse();
        }

        @Test
        void isScreenClaimedLeavesTheModalReadUntakenWhileAConsoleIsUp() {
            // The modal read walks the core UI's children; the console read is a field. Asked in the other
            // order the walk is paid for on every frame, whatever else is on screen.
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var modalReadCount = new AtomicInteger();
            var claim = new ScreenClaim(
                new ConsoleCommandsOverlay(consolePresenceFake),
                () -> false,
                () -> OverlayPresence.NONE,
                () -> {
                    modalReadCount.incrementAndGet();
                    return OverlayPresence.NONE;
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

        @Test
        void resolveClaimStrengthFollowsTheArrangementDialogThroughItsOwnFade() {
            // The dialog fades at the pace of the game's own prompts, so the panel rides it exactly as
            // it rides a modal's - answered full here, the sidebar would snap where a prompt beside it
            // dissolves.
            assertThat(ScreenClaims.createScreenClaimedByTheArrangementDialogAt(true, 0.4f).resolveClaimStrength())
                .isCloseTo(0.4f, within(TOLERANCE));
        }

        @Test
        void resolveClaimStrengthFollowsTheArrangementDialogOutPastThePress() {
            // Raised has gone false on the press, and the paint has not: the panel keeps thinning with
            // a box still on screen rather than coming back under it.
            assertThat(ScreenClaims.createScreenClaimedByTheArrangementDialogAt(false, 0.4f).resolveClaimStrength())
                .isCloseTo(0.4f, within(TOLERANCE));
        }

        @Test
        void resolveClaimStrengthTakesTheDeeperOfTheArrangementDialogAndAModal() {
            // A prompt raised over the dialog is darker than either alone, so the panel is at least as
            // far gone as the further of the two.
            var claim = new ScreenClaim(
                ScreenClaims.createClosedConsole(),
                () -> false,
                () -> new OverlayPresence(true, 0.3f),
                () -> new OverlayPresence(true, 0.6f));

            assertThat(claim.resolveClaimStrength())
                .isCloseTo(0.6f, within(TOLERANCE));
        }

        @Test
        void resolveClaimStrengthLeavesTheModalReadUntakenWithTheArrangementDialogFullyUp() {
            // A dialog wholly in place has taken everything the walk could add, so the walk is skipped
            // on the frames it is standing.
            var modalReadCount = new AtomicInteger();
            var claim = new ScreenClaim(
                ScreenClaims.createClosedConsole(),
                () -> false,
                () -> new OverlayPresence(true, 1f),
                () -> {
                    modalReadCount.incrementAndGet();
                    return OverlayPresence.NONE;
                });

            claim.resolveClaimStrength();

            assertThat(modalReadCount)
                .hasValue(0);
        }

        @Test
        void resolveClaimStrengthIsFullForTheCodexWhoseFadeIsNotFollowed() {
            // Read at full strength like the console, but on a different footing: the codex does fade
            // in, over a few tenths of a second and on a panel the reading never reaches. Pinned so a
            // later attempt to ride that fade is a deliberate change rather than a plausible tidy-up.
            assertThat(ScreenClaims.createScreenClaimedByTheCodex().resolveClaimStrength())
                .isCloseTo(1f, within(TOLERANCE));
        }
    }
}
