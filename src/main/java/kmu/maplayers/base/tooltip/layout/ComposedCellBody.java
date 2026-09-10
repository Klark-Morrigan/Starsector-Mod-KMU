package kmu.maplayers.base.tooltip.layout;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

/**
 * What a layer composed for one paint of its hover box: the blocks its body reads as, and how deep
 * the box goes for this system.
 *
 * <p>The two travel back together because the second is a fact about the first. Where the tree runs
 * out turns on what the body found - a system the box lists nobody for has no account for any deeper
 * level to open - so a box asked the two questions separately would read the system a second time to
 * answer a question its own paint had just settled, once per frame for as long as the cursor rests on
 * the cell.
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
 * <p>What is carried is the depth alone rather than any wording for it: what a press does is the
 * level's to say ({@link HoverTooltipDetailLevel#resolveArrivalPhrase}), the same phrase over every
 * layer, so a box states only how far its own account reaches.
 *
 * @param blocks           the body's blocks, in reading order; empty where the layer has nothing to
 *                         say about the system, which is what stops the box being drawn at all
 * @param deepestHeldLevel the deepest level this box holds anything at for this system - where the
 *                         cycle wraps, so the press after it collapses the box rather than offering
 *                         a tier that would redraw what is already on screen. The shallowest level
 *                         where the body found nothing to account for
 */
public record ComposedCellBody(
    CellTooltipBlocks blocks,
    HoverTooltipDetailLevel deepestHeldLevel) {

    /**
     * A layer with nothing to say about the system, and so nothing to open up either. Offered as one
     * value rather than as an empty pair each caller spells out, since "nothing found" is one state
     * and two spellings of it agree only until one is edited.
     */
    public static final ComposedCellBody NOTHING =
        new ComposedCellBody(CellTooltipBlocks.NOTHING, HoverTooltipDetailLevel.FACTIONS);
}
