package kmu.maplayers.base.hover;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * Closes each frame's hover window, so a hover outlives only the frames the map keeps resolving it
 * on.
 *
 * <p>What that is for is {@link MapHoverState#expireHoverIfNoPassPublished()}'s to state; this is
 * the tick behind it, and exists because that rule cannot be driven from where the parks it corrects
 * are written.
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

    // The holder whose window this closes. Handed in rather than reached for, because this script
    // is registered on one sector and must go on expiring that sector's hover rather than whichever
    // sector happens to be running when a frame reaches it.
    private final MapHoverState hoverState;

    /**
     * @param hoverState the holder to expire, which is the hover state of the sector this script is
     *                   registered on
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
