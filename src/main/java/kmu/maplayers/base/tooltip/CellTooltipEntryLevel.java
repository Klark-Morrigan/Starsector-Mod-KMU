package kmu.maplayers.base.tooltip;

/**
 * Where one listed line stands: how far in it is set from the block's own lines, and how far under the
 * box's own voice it speaks. Carried down the walk that lays a listing out
 * ({@link CellTooltipSections}) and read by the vocabulary that draws each line ({@link CellTooltipRows}).
 *
 * <p>Two counts rather than one, because a line may be set in without being demoted. The factions
 * inside an alliance sit under the line naming it, yet they are the same kind of statement it is - who
 * holds the system - while a market beneath a faction is the account of <em>why</em>. Read off the
 * indent alone, that market would draw at one size under a lone faction and another under an allied
 * one, because the alliance added a level the account had nothing to do with. Held apart, what a line
 * <em>is</em> settles how far under the box's voice it reads, and where it sits settles only where it
 * sits.
 *
 * <p>The two travel as one value rather than as a pair of ints threaded through the walk, so the walk
 * and the vocabulary cannot come to disagree about which number is which - two ints of the same type,
 * passed positionally, can be swapped at a call site and still compile.
 *
 * @param indentDepth        how far beneath the block the line was found; zero for one of the block's
 *                           own lines
 * @param subordinationLevel how many steps the line stands under the box's own voice; zero for a line
 *                           speaking in it
 */
record CellTooltipEntryLevel(
    int indentDepth,
    int subordinationLevel) {

    // What a line the block lists in its own right stands at: flush with its siblings and speaking in
    // the box's own voice. Both counts are measured up from here, so every deeper level is stated as
    // steps taken from what the block itself says rather than as an absolute a caller has to know.
    private static final int LISTED_INDENT_DEPTH = 0;
    private static final int NO_SUBORDINATION = 0;

    // One step of either count. Named because the two refinements below differ only in which counts
    // it is added to, which is the entire distinction this type exists to draw.
    private static final int ONE_STEP = 1;

    /**
     * The level a block's own lines sit at, and the level its walk starts from. Offered as one value
     * rather than as a zero each side spells out, since where a listing begins is one fact and two
     * copies of it agree only until one is edited.
     */
    static final CellTooltipEntryLevel LISTED_LEVEL =
        new CellTooltipEntryLevel(LISTED_INDENT_DEPTH, NO_SUBORDINATION);

    /**
     * Floors both counts at what a block's own line stands at. Neither can be reached from below by
     * walking a listing, so this holds the only way past it - a level built directly - to the same
     * range: a negative depth would draw a line further left than the box's content edge, and a
     * negative demotion would have the host resolve a size larger than the body's for a line meant to
     * be quieter than it.
     */
    CellTooltipEntryLevel {
        indentDepth = Math.max(LISTED_INDENT_DEPTH, indentDepth);
        subordinationLevel = Math.max(NO_SUBORDINATION, subordinationLevel);
    }

    /**
     * The level of a line gathered under this one as a peer - set in beneath it while still speaking at
     * the same remove from the box's voice. An alliance's member factions are the case: naming the
     * alliance and naming the factions in it are one answer stated at two granularities, so nothing has
     * been broken down and nothing is demoted.
     *
     * @return the level for such a line
     */
    CellTooltipEntryLevel groupedUnder() {
        return new CellTooltipEntryLevel(indentDepth + ONE_STEP, subordinationLevel);
    }

    /**
     * The level of a line standing under this one as its account - set in beneath it and one step
     * further under the box's voice. A market beneath the faction holding it is the case: the line
     * above states a finding and this one states part of why it holds.
     *
     * @return the level for such a line
     */
    CellTooltipEntryLevel subordinatedUnder() {
        return new CellTooltipEntryLevel(indentDepth + ONE_STEP, subordinationLevel + ONE_STEP);
    }

    /**
     * Whether a line standing here is shallow enough for the box to show at {@code detailLevel} - the
     * question the detail cut asks of every tier the walk reaches.
     *
     * <p>Asked of the subordination alone. A line set in without being demoted - the member factions
     * inside an alliance - is the same kind of statement the line above it is, so it survives the
     * shallowest cut; read off the indent instead, those factions would be dropped at the very level
     * that exists to state who holds the system.
     *
     * @param detailLevel how deep the box has been asked to read
     * @return true where the line is to be shown
     */
    boolean isAdmittedBy(HoverTooltipDetailLevel detailLevel) {
        return subordinationLevel <= detailLevel.getMaximumSubordination();
    }

    /**
     * Whether the line at this level is one the block lists in its own right, rather than something
     * found beneath one of those. What tells the two line shapes apart, since only the flush ones read
     * as being listed.
     *
     * @return true for one of the block's own lines
     */
    boolean isListedInItsOwnRight() {
        return indentDepth <= LISTED_INDENT_DEPTH;
    }
}
