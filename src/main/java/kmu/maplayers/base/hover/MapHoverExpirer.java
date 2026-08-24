package kmu.maplayers.base.hover;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * Closes each frame's hover window, so a hover outlives only the frames the map keeps resolving it
 * on.
 *
 * <p>Every other park is written inside a map pass - the cursor left the window, the pass may not be
 * read against, nothing was painted to hover over - which is exactly why one is needed outside them.
 * A frame with no map pass at all parks nothing, and the tooltip that reports the hover draws from a
 * campaign-wide render listener rather than from the map: with the map screen closed and no minimap
 * on the campaign view, the last cell any map resolved goes on being named, anywhere the pointer
 * goes, for the rest of the session.
 *
 * <p>A script rather than a render pass, because the frames this exists for are the ones where the
 * pass it would live in does not run. It runs while paused for the same reason: an open screen, a
 * dialog and the pause menu all pause the campaign, and a hover left standing under one of them is
 * precisely what this is here to let go of.
 *
 * <p>It reads nothing about what is on screen. Which frames may resolve a hover is settled by the
 * passes and the permissions they ask, and a second reading of that here could only come to disagree
 * with them; all this knows is whether a pass published, which is the one fact that says a hover is
 * still somebody's answer.
 */
public final class MapHoverExpirer implements EveryFrameScript {

    // The holder whose window this closes. Handed in rather than reached for, so the rule is
    // exercisable over a holder of the test's own and cannot publish into the shared one.
    private final MapHoverState hoverState;

    /**
     * @param hoverState the holder to expire, which is
     *                   {@link MapHoverState#getInstance()} for a running game
     */
    public MapHoverExpirer(MapHoverState hoverState) {
        this.hoverState = hoverState;
    }

    @Override
    public void advance(float amount) {
        hoverState.expireHoverIfNoPassPublished();
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
