package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * Grants one map-layer frame preparation per drawn frame, so the per-frame work behind a layer runs
 * once however many surfaces reach it.
 *
 * <p>A layer is painted through more than one terrain surface, and whether a surface draws is
 * settled by that surface alone - each answers only for itself, so two can answer yes for the same
 * frame. Pinning preparation to the band every mode paints narrows that without closing it: a band
 * is something a surface claims rather than something one surface holds. Preparation cannot absorb
 * being run twice either, since it steps the cursor's arrival latch, and a latch stepped twice for
 * one frame reports the cursor leaving and reaching the cell it is resting on. So "once" is enforced
 * here rather than argued from which surface the engine reaches first.
 *
 * <p>A frame begins where the campaign UI begins drawing one, which is the below-UI render pass -
 * it runs under the screen the map widget hangs off, so the claim is already reset by the time any
 * terrain surface asks for it. Nothing is drawn from that pass; it is read purely as the boundary
 * the surfaces cannot see from where they stand.
 *
 * <p>Fails open, and the direction matters. Until that pass has been seen firing, nothing here knows
 * where a frame starts, so every claim is granted and the surfaces prepare per pass - the very
 * duplication this exists to stop, which is nonetheless the safe way to be wrong. Denying instead
 * would leave the overlay frozen on the single frame that was ever prepared, nothing else bringing
 * the draw lists up to date.
 *
 * <p>Reached as one shared instance because the surfaces that ask are separate objects the engine
 * builds per terrain entity, and are restored from a save at that. Read and written on the game
 * thread alone - both the UI render pass and the terrain pass run there - so the flags need no
 * publication guarantee of their own.
 */
public final class MapFramePreparationClaim implements CampaignUIRenderingListener {

    // The one shared claim the render surfaces ask and the frame boundary resets.
    private static final MapFramePreparationClaim INSTANCE = new MapFramePreparationClaim();

    // Whether the boundary pass has been seen at all. Until it has, no claim can be denied without
    // risking a frozen overlay, so this is what separates "not yet claimed this frame" from "no idea
    // where this frame began".
    private boolean isFrameStartKnown;

    // Whether this frame's preparation has already been handed out.
    private boolean isPreparationClaimed;

    // Reached through getInstance(); the claim stands on its own instance, so the constructor is
    // package-visible rather than sealed to the singleton.
    MapFramePreparationClaim() {
    }

    /**
     * @return the one shared claim, since the surfaces that ask it are separate objects with no way
     *         to reach each other
     */
    public static MapFramePreparationClaim getInstance() {
        return INSTANCE;
    }

    /**
     * Takes this frame's preparation for the caller, if it is still going.
     *
     * @return whether the caller is the one to prepare this frame - true for the first surface to
     *         ask after a frame began, and for every asker while no frame boundary is known
     */
    public boolean claimPreparation() {

        // No boundary seen means no frame to be second in, so every asker prepares. That is what the
        // surfaces do with no claim at all, which is the state this degrades to rather than past.
        if (!isFrameStartKnown) {
            return true;
        }
        if (isPreparationClaimed) {
            return false;
        }
        isPreparationClaimed = true;
        return true;
    }

    /**
     * Forgets that frame boundaries were ever seen, so a session that never re-registers this
     * listener falls back to preparing per pass instead of freezing on one frame.
     *
     * <p>Called per load, before the registration: this is a process-lifetime instance and the
     * registration is remade per save, so a load that fails to remake it would otherwise leave a
     * claim armed by the previous session with nothing left to reset it.
     */
    public void discardFrameTrackingFromPreviousSave() {
        isFrameStartKnown = false;
        isPreparationClaimed = false;
    }

    /**
     * Opens a new frame, releasing the preparation for whichever surface asks first.
     *
     * <p>This pass rather than either of the ones above the UI, because a boundary is only a boundary
     * if it falls before what it bounds: the map widget draws inside the screen, which is composited
     * after this.
     */
    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        isFrameStartKnown = true;
        isPreparationClaimed = false;
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Lands after the map has already drawn, so it bounds nothing this claim is about.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // Later still, for the same reason.
    }
}
