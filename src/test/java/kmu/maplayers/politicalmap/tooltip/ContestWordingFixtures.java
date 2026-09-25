package kmu.maplayers.politicalmap.tooltip;

/**
 * The install a political box is stood up over where the wording is not what a case is about.
 *
 * <p>Named rather than written out per case so a suite states which sector it is posing rather than a
 * lambda the reader has to unpick. Which mod supplies the answer is deliberately absent: what a box
 * takes is the wording, and a suite that named the mod would be testing the gate through the box
 * instead of through {@link kmu.mods.nexerelin.NexerelinContestWording}, where that answer is
 * actually made.
 *
 * <p>Only the contested side is kept. A suite that does vary the wording varies it per case, off a
 * field of its own, which a fixed source cannot serve.
 */
public final class ContestWordingFixtures {

    /** An install where systems change hands, so a rival block is headed as a contest. */
    public static final ContestWordingSource CONTESTED_WORDING = () -> ContestWording.CONTESTED;

    private ContestWordingFixtures() {
    }
}
