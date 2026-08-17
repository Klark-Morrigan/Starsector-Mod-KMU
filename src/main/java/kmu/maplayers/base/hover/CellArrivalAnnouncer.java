package kmu.maplayers.base.hover;

/**
 * What a host answers the cursor reaching a cell it was not on with.
 *
 * <p>A role rather than a sound player and a cue carried side by side, because both halves of that
 * answer belong to the host's look: which sample sounds, and at what level, is settled where the
 * look is settled. What the read under it owns is the moment - which frames are an arrival at all -
 * and nothing about how one is answered.
 *
 * <p>Nothing crosses the seam. Which cell was reached is already the hover's to publish, so an
 * announcer taking it would invite an answer that varies by cell, which is not a thing any look
 * here asks for.
 */
@FunctionalInterface
public interface CellArrivalAnnouncer {

    /** Answers the cursor having reached a cell it was not on. */
    void announceCellArrival();
}
