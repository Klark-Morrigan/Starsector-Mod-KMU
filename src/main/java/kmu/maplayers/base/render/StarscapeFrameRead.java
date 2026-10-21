package kmu.maplayers.base.render;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;

import java.util.function.BooleanSupplier;

/**
 * Whether a Starscape map is on screen, settled once per frame for every Starscape surface of one
 * sector.
 *
 * <p>Both Starscape surfaces ask before they draw, and the engine calls both in either mode, so the
 * question is asked twice every frame a map is up. The read behind it walks the live widget tree of
 * the intel screen, and its answer cannot move between two surfaces of one frame, so the first
 * surface to ask settles it and the second takes the answer.
 *
 * <p>Kept against the frame boundary {@link MapFramePreparationClaim} already sees, so a frame is
 * the same frame here as it is to the preparation. Where no boundary has been seen yet, every ask is
 * read afresh - the same fail-open the claim takes, costing a read rather than a stale answer.
 *
 * <p>One per machinery, since a frame is a sector's. Released with the machinery.
 */
final class StarscapeFrameRead
        implements InstalledMachinery {

    // The frame number no frame carries, so the first ask of a frame always reads.
    private static final int NO_FRAME_SETTLED = -1;

    // Where frames begin on this sector.
    private final MapFramePreparationClaim frameClaim;

    // The frame the answer below was read in, and the answer.
    private int settledFrameNumber = NO_FRAME_SETTLED;
    private boolean isStarscapeMapShowing;

    private StarscapeFrameRead(MapFramePreparationClaim frameClaim) {
        this.frameClaim = frameClaim;
    }

    @Override
    public void disposeMachinery() {
        settledFrameNumber = NO_FRAME_SETTLED;
    }

    /**
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's read, sharing the frame boundary its surfaces' preparation is claimed on
     */
    static StarscapeFrameRead resolveReadIn(SectorMapMachinery machinery) {

        // Resolved ahead of the read rather than inside its factory: the machinery makes what it holds
        // under one map update, which cannot make a second holding from inside the first.
        var frameClaim = MapFramePreparationClaim.resolveClaimIn(machinery);

        return machinery.resolveMachinery(
            StarscapeFrameRead.class,
            () -> new StarscapeFrameRead(frameClaim));
    }

    /**
     * Whether a Starscape map is on screen this frame.
     *
     * @param readStarscapeMapShowing the live read, asked once per frame at most
     * @return the answer this frame's first ask read
     */
    boolean isStarscapeMapShowing(BooleanSupplier readStarscapeMapShowing) {

        if (!frameClaim.isFrameBoundaryKnown()) {
            return readStarscapeMapShowing.getAsBoolean();
        }

        var frameNumber = frameClaim.getOpenedFrameCount();
        if (frameNumber != settledFrameNumber) {

            isStarscapeMapShowing = readStarscapeMapShowing.getAsBoolean();
            settledFrameNumber = frameNumber;
        }

        return isStarscapeMapShowing;
    }
}
