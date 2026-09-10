package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;

/**
 * Grants one map-layer frame preparation per drawn frame, so the per-frame work behind a layer runs
 * once however many surfaces reach it.
 *
 * <p>A layer is painted through more than one terrain surface, and whether a surface draws is
 * settled by that surface alone - each answers only for itself, so two can answer yes for the same
 * frame. Pinning preparation to the band every mode paints narrows that without closing it: a band
 * is something a surface claims rather than something one surface holds. Preparation cannot absorb
 * being run twice either, since it is where the cursor's arrival latch is stepped - the moment a
 * layer announces for the frame just closed - and a latch stepped twice for one frame reports the
 * cursor leaving and reaching the cell it is resting on. So "once" is enforced here rather than
 * argued from which surface the engine reaches first.
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
 * <p>One per machinery, since a frame is a sector's: two sectors drawing in one frame each owe
 * their own draw lists a preparation, and a shared claim would give the second sector's surfaces
 * nothing to prepare with. That also means nothing has to clear it per load - machinery is
 * made fresh when the layers are installed, so a claim left mid-frame by the session before goes
 * with the machinery that held it.
 *
 * <p>Read and written on the game thread alone - both the UI render pass and the terrain pass run
 * there - so the state needs no publication guarantee of its own.
 */
public final class MapFramePreparationClaim implements CampaignUIRenderingListener,
        InstalledMachinery {

    // Where this claim stands. One field rather than a pair of flags, because the three states are
    // exactly three: a fourth combination of "boundary not seen" with "preparation taken" has no
    // meaning here, and holding two flags is what would let one be written.
    private FramePreparationState state = FramePreparationState.BOUNDARY_UNKNOWN;

    // Reached through resolveClaimIn, so the only claims that exist are ones machinery holds.
    MapFramePreparationClaim() {
    }

    /**
     * Takes this frame's preparation for the caller, if it is still going.
     *
     * @return whether the caller is the one to prepare this frame - true for the first surface to
     *         ask after a frame began, and for every asker while no frame boundary is known
     */
    public boolean claimPreparation() {

        // No boundary seen means no frame to be second in, so every asker prepares and the state is
        // left where it is. That is what the surfaces do with no claim at all, which is what this
        // degrades to rather than past.
        if (state == FramePreparationState.BOUNDARY_UNKNOWN) {
            return true;
        }
        if (state == FramePreparationState.PREPARATION_TAKEN) {
            return false;
        }
        state = FramePreparationState.PREPARATION_TAKEN;
        return true;
    }

    /**
     * Forgets that frame boundaries were ever seen.
     *
     * <p>Defensive rather than needed: a surface resolves its machinery and this claim afresh
     * every frame, so a released claim is one nothing asks again. What it guards is the registration
     * the sector's listener manager may still be holding - which goes on being handed frame
     * boundaries until the install after it re-registers - and it leaves that stray in the fail-open
     * state, where an extra preparation costs work rather than the overlay.
     */
    @Override
    public void disposeMachinery() {
        state = FramePreparationState.BOUNDARY_UNKNOWN;
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
        state = FramePreparationState.PREPARATION_OPEN;
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Lands after the map has already drawn, so it bounds nothing this claim is about. Opening a
        // frame here would release the preparation to the surfaces of the frame after it.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // Later still, for the same reason.
    }

    /**
     * The claim {@code machinery}'s surfaces share, made on the first frame one of them asks for
     * it and released with the machinery holding it.
     *
     * <p>The one way to a claim, so a surface and the registration that opens its frames cannot end
     * up on two different ones - a claim nothing opens frames on grants every asker, which is the
     * duplicated preparation the type exists to stop.
     *
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's claim
     */
    static MapFramePreparationClaim resolveClaimIn(SectorMapMachinery machinery) {
        return machinery.resolveMachinery(
            MapFramePreparationClaim.class,
            MapFramePreparationClaim::new);
    }

    // Where a claim stands, as the three states it can actually be in. An enum rather than flags so
    // the fourth combination two booleans would offer - a preparation taken on a frame nobody saw
    // begin - cannot be written at all, and so that each transition below is one assignment.
    private enum FramePreparationState {

        // No boundary pass seen yet, so nothing knows where a frame begins and every claim is
        // granted. The state the surfaces run in before the listener is registered, and the one a
        // load falls back to.
        BOUNDARY_UNKNOWN,

        // A frame has begun and its preparation is still going.
        PREPARATION_OPEN,

        // This frame's preparation has been taken; every later asker is refused until the next.
        PREPARATION_TAKEN,
    }
}
