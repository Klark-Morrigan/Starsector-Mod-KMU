package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.coreui.CodexView;

/**
 * The cover the codex lays over the map: while it is up nothing the cursor rests on is the map.
 *
 * <p>Its own cover rather than a case of the modal one beside it, because the codex is not raised
 * inside the core UI at all - the campaign state holds a second screen panel of its own for it, so
 * the modal cover's walk of the core UI's children cannot reach it. {@link CodexView} states the
 * whole of that finding; what matters here is that a screen this mod draws over has a second way of
 * being covered, and only one of the two is a modal.
 *
 * <p>Nor does the game's own silencing reach us. It stands the screen underneath down by feeding
 * that panel a cursor parked far off screen, which every widget in the tree then hit-tests against
 * - but the layers read the mouse for themselves from a render pass, so they see the real pointer
 * and go on lighting cells behind the codex.
 *
 * <p>No cursor test, for the same reason the pause menu and the console need none - though the codex
 * gets there differently, and the difference is worth stating because the panel plainly does not fill
 * the screen. It is raised with a screen-spanning sibling behind it that dims the backdrop and takes
 * the events, so what is covered is the whole screen even though what is drawn is a box in the middle
 * of it. Measuring the pointer against that box would leave the map answering the cursor over the
 * dimmed remainder, which is the part the player can see and cannot reach.
 *
 * <p>Fails open per the role's rule - a game whose state cannot be read reports no codex - which the
 * reading behind it guarantees and documents.
 */
public final class CodexMapCover extends FlagMapCover {

    /**
     * Reads the live app state - one hop off it and a memoised name lookup, which puts this beside
     * the modal cover's walk of the core UI's own children rather than among the settled flags above
     * it.
     */
    public CodexMapCover() {
        super(CodexView::isCodexShowing);
    }
}
