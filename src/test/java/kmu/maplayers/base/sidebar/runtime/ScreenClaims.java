package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;

/**
 * Screen claims in the states a host suite needs to put one in, so a test that is about a host says which
 * claim it is standing under rather than composing two reads it does not care about.
 *
 * <p>The console-backed one takes the overlay from its caller, since a suite exercising the console half
 * drives it through a presence it holds; the other two settle both reads outright, a host test having no
 * use for which of them answered.
 */
final class ScreenClaims {

    private ScreenClaims() {
    }

    /** A screen nothing has claimed, which is where the panel is expected to draw. */
    static ScreenClaim createUnclaimedScreen() {
        return createClaimOverConsole(new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake()));
    }

    /** A screen claimed by a modal a core screen has raised in front of itself. */
    static ScreenClaim createScreenClaimedByAModal() {
        return new ScreenClaim(
            new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake()),
            () -> true);
    }

    /**
     * A claim whose console half the caller drives and whose modal half is settled shut, so a console test
     * cannot pass on the wrong read.
     *
     * @param consoleOverlay the console state to read
     * @return a claim over it alone
     */
    static ScreenClaim createClaimOverConsole(ConsoleCommandsOverlay consoleOverlay) {
        return new ScreenClaim(consoleOverlay, () -> false);
    }
}
