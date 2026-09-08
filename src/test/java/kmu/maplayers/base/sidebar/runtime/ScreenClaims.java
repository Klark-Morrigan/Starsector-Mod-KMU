package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.coreui.ModalDialogState;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;

import kmu.maplayers.base.chrome.arrange.ArrangementDialogState;

/**
 * Screen claims in the states a host suite needs to put one in, so a test that is about a host says which
 * claim it is standing under rather than composing two reads it does not care about.
 *
 * <p>The console-backed one takes the overlay from its caller, since a suite exercising the console half
 * drives it through a presence it holds; the others settle every read outright, a host test having no
 * use for which of them answered.
 */
final class ScreenClaims {

    // A modal wholly in place, which is where one rests once its fade has run.
    private static final float FULLY_RAISED = 1f;

    // The arranging dialog up for input, as against dismissed and only fading.
    private static final boolean RAISED = true;

    private ScreenClaims() {
    }

    /** A screen nothing has claimed, which is where the panel is expected to draw. */
    static ScreenClaim createUnclaimedScreen() {
        return createClaimOverConsole(createClosedConsole());
    }

    /** A screen claimed by a modal a core screen has raised in front of itself, fully in place. */
    static ScreenClaim createScreenClaimedByAModal() {
        return createScreenClaimedByAModalAt(FULLY_RAISED);
    }

    /**
     * A screen claimed by a modal part way through its own fade, which is the state the panel has to
     * follow rather than snap through.
     *
     * @param brightness how far through that fade the modal stands, 0..1
     * @return a claim reporting it as showing at that brightness
     */
    static ScreenClaim createScreenClaimedByAModalAt(float brightness) {
        return new ScreenClaim(
            createClosedConsole(),
            () -> false,
            () -> ArrangementDialogState.DOWN,
            () -> new ModalDialogState(true, brightness));
    }

    /** A screen claimed by the codex, with none of the other reads answering. */
    static ScreenClaim createScreenClaimedByTheCodex() {
        return new ScreenClaim(
            createClosedConsole(),
            () -> true,
            () -> ArrangementDialogState.DOWN,
            () -> ModalDialogState.NONE);
    }

    /** A screen claimed by this mod's own bar-arranging dialog, fully in place, with no other read answering. */
    static ScreenClaim createScreenClaimedByTheArrangementDialog() {
        return createScreenClaimedByTheArrangementDialogAt(RAISED, FULLY_RAISED);
    }

    /**
     * A screen under this mod's own bar-arranging dialog in a given state, which is how the two halves of
     * that dialog's reading are told apart: up for input, or only painted.
     *
     * @param isRaised     whether the dialog is up for input
     * @param fadeFraction how far onto the screen its paint stands, 0..1
     * @return a claim reading the dialog in that state and nothing else
     */
    static ScreenClaim createScreenClaimedByTheArrangementDialogAt(boolean isRaised, float fadeFraction) {
        return new ScreenClaim(
            createClosedConsole(),
            () -> false,
            () -> new ArrangementDialogState(isRaised, fadeFraction),
            () -> ModalDialogState.NONE);
    }

    /**
     * A claim whose console half the caller drives and whose other reads are settled shut, so a console
     * test cannot pass on the wrong read.
     *
     * @param consoleOverlay the console state to read
     * @return a claim over it alone
     */
    static ScreenClaim createClaimOverConsole(ConsoleCommandsOverlay consoleOverlay) {
        return new ScreenClaim(
            consoleOverlay,
            () -> false,
            () -> ArrangementDialogState.DOWN,
            () -> ModalDialogState.NONE);
    }

    // A console read that answers shut, for the claims whose console half is not what they are about. Its
    // own read rather than a shared instance, since a claim holding one is free to be driven by its caller.
    private static ConsoleCommandsOverlay createClosedConsole() {
        return new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake());
    }
}
