package kmu.maplayers.base.render;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;

import java.util.function.BooleanSupplier;

/**
 * Whether a vanilla map is drawn, and whether a Starscape one is, settled once per frame for every
 * surface of one sector.
 *
 * <p>Up to three surfaces ask before they draw, and on a working setup the foreign-pass warning asks
 * on every pass as well. On the intel screen each read walks the live widget tree, and on a pass with
 * no vanilla map it walks the core UI's children too. Neither answer can move between two surfaces of
 * one frame, so the first ask settles it and the rest take the answer.
 *
 * <p>Kept against the frame boundary {@link MapFramePreparationClaim} already sees, so a frame is the
 * same frame here as it is to the preparation. Where no boundary has been seen yet, every ask is read
 * afresh - the same fail-open the claim takes, costing a read rather than a stale answer.
 *
 * <p>One per machinery, since a frame is a sector's. Released with the machinery.
 */
final class MapPresenceFrameRead
        implements InstalledMachinery {

    // Where frames begin on this sector.
    private final MapFramePreparationClaim frameClaim;

    private final FrameAnswer vanillaMapDrawn = new FrameAnswer();
    private final FrameAnswer starscapeMapDrawn = new FrameAnswer();

    private MapPresenceFrameRead(MapFramePreparationClaim frameClaim) {
        this.frameClaim = frameClaim;
    }

    @Override
    public void disposeMachinery() {
        vanillaMapDrawn.forgetAnswer();
        starscapeMapDrawn.forgetAnswer();
    }

    /**
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's read, sharing the frame boundary its surfaces' preparation is claimed on
     */
    static MapPresenceFrameRead resolveReadIn(SectorMapMachinery machinery) {

        // Resolved ahead of the read rather than inside its factory: the machinery makes what it holds
        // under one map update, which cannot make a second holding from inside the first.
        var frameClaim = MapFramePreparationClaim.resolveClaimIn(machinery);

        return machinery.resolveMachinery(
            MapPresenceFrameRead.class,
            () -> new MapPresenceFrameRead(frameClaim));
    }

    /**
     * Whether a vanilla map is drawn this frame, a closing one included.
     *
     * @param readVanillaMapDrawn the live read, asked once per frame at most
     * @return the answer this frame's first ask read
     */
    boolean isVanillaMapDrawn(BooleanSupplier readVanillaMapDrawn) {
        return settleAnswer(vanillaMapDrawn, readVanillaMapDrawn);
    }

    /**
     * Whether a Starscape map is drawn this frame, a closing one included.
     *
     * @param readStarscapeMapDrawn the live read, asked once per frame at most
     * @return the answer this frame's first ask read
     */
    boolean isStarscapeMapDrawn(BooleanSupplier readStarscapeMapDrawn) {
        return settleAnswer(starscapeMapDrawn, readStarscapeMapDrawn);
    }

    // The answer for this frame: the one already settled, or the live read taken and kept.
    private boolean settleAnswer(FrameAnswer answer, BooleanSupplier readAnswer) {

        if (!frameClaim.isFrameBoundaryKnown()) {
            return readAnswer.getAsBoolean();
        }

        var frameNumber = frameClaim.getOpenedFrameCount();
        if (frameNumber != answer.settledFrameNumber) {

            answer.isTrue = readAnswer.getAsBoolean();
            answer.settledFrameNumber = frameNumber;
        }

        return answer.isTrue;
    }

    // One question's answer and the frame it was read in.
    private static final class FrameAnswer {

        // The frame number no frame carries, so the first ask of a frame always reads.
        private static final int NO_FRAME_SETTLED = -1;

        private int settledFrameNumber = NO_FRAME_SETTLED;
        private boolean isTrue;

        private void forgetAnswer() {
            settledFrameNumber = NO_FRAME_SETTLED;
        }
    }
}
