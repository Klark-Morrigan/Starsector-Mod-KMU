package kmu.maplayers.politicalmap.base.ribbon;

/**
 * The player's answers about how a band is laid: how far its runs reach, and how far they reach on
 * a cell nobody contests.
 *
 * <p>One value because the two are read together, travel together and are meaningless apart. The
 * uncontested rule states its own run length only as a change to the authored one, so it says
 * nothing without the pair it modifies. Handed as two loose parameters they would reach every
 * counting rule and every planner side by side anyway - and one of them could then be this pass's
 * while the other was a stale read.
 *
 * <p>Both are settings, sampled once at the start of a pass, which is the other half of why they
 * are paired: a band laid at one pass's proportions under another pass's shortening is a cell drawn
 * to a design nobody chose.
 *
 * @param lengths         how far a market's segment and an interjection run
 * @param uncontestedRuns how far those runs reach on a cell no bloc but its own painter holds
 *                        anything in
 */
public record RibbonPlanRules(
    RibbonSegmentLengths lengths,
    UncontestedRibbonRuns uncontestedRuns) {

    /**
     * The lengths a cell nobody contests lays its runs at: these rules' own authored pair, put
     * through these rules' own shortening.
     *
     * <p>Answered here rather than by handing a caller the two halves to combine, which is the
     * pairing's whole point. The shortening states itself only as a change to some authored pair,
     * so a caller that reached for both could pass it the wrong one - this pass's shortening over
     * a previous pass's lengths - and produce a band at proportions nobody set. The one pairing
     * that is ever correct is the one inside this value, so it is the only one reachable.
     *
     * @return the market run cut to a single width where the shortening is on, and the authored
     *         lengths where it is not
     */
    public RibbonSegmentLengths resolveUncontestedLengths() {
        return uncontestedRuns.resolveRunLengths(lengths);
    }
}
