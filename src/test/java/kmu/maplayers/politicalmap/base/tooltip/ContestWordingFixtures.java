package kmu.maplayers.politicalmap.base.tooltip;

/**
 * The two installs a political box can be stood up over, as the sources it is bound to.
 *
 * <p>Named rather than written out per case so a suite states which sector it is posing rather than a
 * lambda the reader has to unpick, and so the pair cannot drift apart across the classes that use
 * them. Which mod supplies either answer is deliberately absent: what a box takes is the wording, and
 * a suite that named the mod would be testing the gate through the box instead of through
 * {@link kmu.mods.nexerelin.NexerelinContestWording}, where that answer is actually made.
 */
final class ContestWordingFixtures {

    /** An install where systems change hands, so a rival block is headed as a contest. */
    static final ContestWordingSource CONTESTED_WORDING = () -> ContestWording.CONTESTED;

    /** An install where they never do, so the same block is headed as plain presence. */
    static final ContestWordingSource PRESENT_WORDING = () -> ContestWording.PRESENT;

    private ContestWordingFixtures() {
    }
}
