package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.coreui.ModalDialogState;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;

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
            () -> false,
            () -> new ModalDialogState(true, brightness));
    }

    /** A screen claimed by the codex, with none of the other reads answering. */
    static ScreenClaim createScreenClaimedByTheCodex() {
        return new ScreenClaim(
            createClosedConsole(),
            () -> true,
            () -> false,
            () -> ModalDialogState.NONE);
    }

    /** A screen claimed by this mod's own bar-arranging dialog, with no other read answering. */
    static ScreenClaim createScreenClaimedByTheArrangementDialog() {
        return new ScreenClaim(
            createClosedConsole(),
            () -> false,
            () -> true,
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
        return new ScreenClaim(consoleOverlay, () -> false, () -> false, () -> ModalDialogState.NONE);
    }

    // A console read that answers shut, for the claims whose console half is not what they are about. Its
    // own read rather than a shared instance, since a claim holding one is free to be driven by its caller.
    private static ConsoleCommandsOverlay createClosedConsole() {
        return new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake());
    }
}
