package kmu.maplayers.base.hover.cover;

import kmu.maplayers.base.sidebar.runtime.ScreenClaim;

/**
 * The cover anything claiming the screen lays over the map: a console, the codex, this mod's own
 * bar-arranging dialog, or a modal a core screen has raised in front of itself. While one is up
 * nothing the cursor rests on is the map.
 *
 * <p>One cover over {@link ScreenClaim} rather than one per claimant, because the sidebar stands down
 * under exactly the same claimants for exactly the same reason. The claim is where each claimant is
 * named, read and gated - which one needs a reading of its own, and why the console is asked only
 * where its mod is installed - so a claimant added there covers the map too, with nothing to list
 * twice.
 *
 * <p>The crisp answer rather than the fade: a claimant takes the cursor from the frame it is raised,
 * so a hover waiting for its fade would light cells under a dialog already eating the player's clicks.
 *
 * <p>No cursor test. Every claimant claims the whole screen - a console takes it, and the codex and the
 * modals dim it behind a screen-spanning backdrop - so where the pointer is does not come into it.
 *
 * <p>Fails open per the role's rule, which every read behind the claim guarantees and documents.
 */
public final class ScreenClaimMapCover
        extends FlagMapCover {

    /**
     * Reads the live claim every host shares. Where that ranks it is {@link MapCoverReader}'s to
     * state.
     */
    public ScreenClaimMapCover() {
        this(ScreenClaim.INSTANCE);
    }

    /**
     * @param screenClaim the claim to read
     */
    ScreenClaimMapCover(ScreenClaim screenClaim) {
        super(screenClaim::isScreenClaimed);
    }
}
