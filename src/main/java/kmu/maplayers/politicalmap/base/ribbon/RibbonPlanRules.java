package kmu.maplayers.politicalmap.base.ribbon;

/**
 * The player's answers about how a band is laid: how far its runs reach, and what a cell nobody
 * contests draws.
 *
 * <p>One value because the two are read together, travel together and are meaningless apart. A
 * length says how far a run goes but not whether a cell has one; the uncontested rule says which
 * cells band but states its own run length only as a change to the authored one. Handed as two
 * loose parameters they would reach every counting rule and every planner side by side anyway -
 * and one of them could then be this pass's while the other was a stale read.
 *
 * <p>Both are settings, sampled once at the start of a pass, which is the other half of why they
 * are paired: a band laid at one pass's proportions under another pass's gate is a cell drawn to a
 * design nobody chose.
 *
 * @param lengths          how far a market's segment and an interjection run
 * @param uncontestedBands what a cell no bloc but its own painter holds anything in draws: whether
 *                         it bands at all, and at what run length
 */
public record RibbonPlanRules(
    RibbonSegmentLengths lengths,
    UncontestedCellBands uncontestedBands) {
}
