package kmu.maplayers.ownermap.ribbon;

/**
 * The player's answers about how a band is laid: how far its runs reach on a contested cell, and
 * how far they reach on a cell nobody contests.
 *
 * <p>Every populated cell bands, so what is left to answer about an uncontested one is only how
 * loudly. A cell some rival holds something in is the readout the whole design exists for, and its
 * runs are the player's authored lengths. A cell held by one owner alone reports a footprint its
 * fill has already named the owner of, and needs to say only how many stakes are there. At the
 * authored run length an owner with many stakes in a cell would lay more band than the contested
 * cells the design was built around, out-shouting the very cells a reader is meant to be drawn to.
 * A stake run of one width keeps such a cell a tally rather than a statement.
 *
 * <p>The shortening is a switch rather than a third length because it is a change to the authored
 * pair for one kind of cell, not a proportion of its own: a knob to tune would be one more ratio a
 * player has to hold against the two that already decide how a contested band reads.
 *
 * <p>Both pairs are settled by the one factory, from the one authored pair, and nothing else can
 * make the value. The shortening states itself only as a change to some authored pair, so two
 * pairs handed in separately could be this pass's shortening over a previous pass's lengths, and
 * produce a band at proportions nobody set.
 *
 * <p>Both answers are settings, sampled once at the start of a pass: a band laid at one pass's
 * proportions under another pass's shortening is a cell drawn to a design nobody chose.
 */
public final class RibbonPlanRules {

    // How far one stake's run reaches on an uncontested cell when the runs are shortened: the
    // shortest run a band can draw, since the width is the unit every length in the design is a
    // multiple of.
    private static final int ONE_WIDTH_PER_STAKE = 1;

    private final RibbonSegmentLengths contestedLengths;
    private final RibbonSegmentLengths uncontestedLengths;

    private RibbonPlanRules(
            RibbonSegmentLengths contestedLengths,
            RibbonSegmentLengths uncontestedLengths) {

        this.contestedLengths = contestedLengths;
        this.uncontestedLengths = uncontestedLengths;
    }

    /**
     * Settles both pairs of lengths from the player's authored pair and the shortening switch.
     *
     * <p>Only the stake run is shortened. The parting keeps the player's own length, since what it
     * separates - one stake from the next - is the same statement on an uncontested cell as on any
     * other, and shortening it too would leave a tally whose ticks and gaps say nothing apart.
     *
     * @param authoredLengths           the lengths the player set, which a contested band is laid at
     * @param isUncontestedRunShortened whether the stake runs on a cell nobody contests are cut to
     *                                  one width each, instead of the authored length
     * @return the rules a pass lays every cell by
     */
    public static RibbonPlanRules createFromAuthoredLengths(
            RibbonSegmentLengths authoredLengths,
            boolean isUncontestedRunShortened) {

        var uncontestedLengths = isUncontestedRunShortened
            ? new RibbonSegmentLengths(
                ONE_WIDTH_PER_STAKE,
                authoredLengths.interjectionLengthUnits())
            : authoredLengths;

        return new RibbonPlanRules(authoredLengths, uncontestedLengths);
    }

    /** @return how far a stake's segment and an interjection run on a cell some rival holds in */
    public RibbonSegmentLengths getContestedLengths() {
        return contestedLengths;
    }

    /**
     * @return how far a stake's segment and an interjection run on a cell nobody contests: the
     *         stake run cut to a single width where the shortening is on, and the authored lengths
     *         where it is not
     */
    public RibbonSegmentLengths getUncontestedLengths() {
        return uncontestedLengths;
    }
}
