package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.List;

/**
 * One laying-out of a cell tooltip's body: the blocks as they will be drawn, and how many entries the
 * box had no room for.
 *
 * <p>The two travel together because the second is a fact about the first. What was left out is stated
 * at the foot of the box as one figure over the whole of it, and a box that counted its own losses
 * separately from the laying-out that made them could report a number the rows beside it do not bear
 * out - which is the one thing a row standing for withheld content must never do.
 *
 * @param sections            the body's blocks as drawn, in reading order
 * @param withheldEntryCount  how many entries this laying-out left out across every listing in the
 *                            body; zero for a body drawn whole, which is the ordinary case
 */
record DrawnCellBody(
    List<TooltipSection> sections,
    int withheldEntryCount) {

    /**
     * Copies the blocks, so a walk that went on filling its list cannot reshape a body already handed
     * to the box.
     */
    DrawnCellBody {
        sections = List.copyOf(sections);
    }
}
