package kmu.maplayers.base.tooltip;

/**
 * What a layer composed for one paint of its hover box: the blocks its body reads as, and whether
 * the deeper detail levels hold anything more for this system.
 *
 * <p>The two travel back together because the second is a fact about the first. Whether anything
 * deeper is there to show turns on what the body found - a system the box lists nobody for has no
 * account for a deeper level to open - so a box asked the two questions separately would read the
 * system a second time to answer a question its own paint had just settled, once per frame for as
 * long as the cursor rests on the cell.
 *
 * <p>Agreement between the box and the hint at its foot is then structural rather than hoped for.
 * Asked apart, the two are answered from two reads of a live sector, and the shape that eventually
 * takes is the cruellest one: a box advertising a key that does nothing, or drawing a body it has
 * just declined to offer.
 *
 * <p>The blocks come back as the layer stated them rather than as lines already laid out
 * ({@link CellTooltipBlocks}), because how much of them there is room to draw is settled after the
 * reading, against the screen. One read of the sector can then be laid out more than once.
 *
 * <p>What is carried is the fact alone rather than any wording for it: what a press does is the
 * level's to say ({@link HoverTooltipDetailLevel#resolveNextActionPhrase}), the same phrase over
 * every layer, so a box states only whether it has anything for that press to reach.
 *
 * @param blocks          the body's blocks, in reading order; empty where the layer has nothing to
 *                        say about the system, which is what stops the box being drawn at all
 * @param hasDeeperDetail whether a deeper level would state anything more about this system; false
 *                        where every level would draw what is already on screen
 */
public record ComposedCellBody(
    CellTooltipBlocks blocks,
    boolean hasDeeperDetail) {

    /**
     * A layer with nothing to say about the system, and so nothing to open up either. Offered as one
     * value rather than as an empty pair each caller spells out, since "nothing found" is one state
     * and two spellings of it agree only until one is edited.
     */
    public static final ComposedCellBody NOTHING =
        new ComposedCellBody(CellTooltipBlocks.NOTHING, false);
}
