package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.widgets.tooltip.CursorTooltip;
import kmlib.starsector.ui.widgets.tooltip.TooltipHeightFit;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.starsector.ui.widgets.tooltip.TooltipStyle;

import java.util.List;

/**
 * Fits a hover box to the room the screen gives it: the typography comes down first, and only where
 * even the smallest it will draw at is still too tall does the box start giving up content.
 *
 * <p>That order is the whole of the rule. A tooltip takes no input, so nothing the box loses can be
 * scrolled back to - which makes every line worth keeping at a smaller size than it is worth dropping,
 * and makes dropping one the last resort rather than the first response. Size is given up through the
 * widget's own compression ({@link TooltipHeightFit}), which holds the deepest line the box was asked
 * for and brings the context above it down to meet that line.
 *
 * <p>Past the floor that compression stops at there is nothing more to give up but content, and what a
 * box may leave out is its own subject matter's question rather than a typography's. So the answer here
 * is a number - the most entries any one listing may draw - solved down until the box fits, with the
 * listings themselves closing over what that leaves out ({@link CellTooltipBlocks}).
 *
 * <p>The cut box is then compressed afresh from the authored look rather than left at the floor it was
 * measured against, because it is a smaller box than the one that needed the floor: the faces are
 * bitmap atlases crisp at one size, and a box scaled further off its own atlas than it had to be is
 * softer for nothing.
 *
 * <p>A box that fits is handed back untouched, which is nearly every box.
 */
final class CellTooltipContentFit {

    // The step between two allowances, and what a range is divided by to halve it - the two ends being
    // averaged.
    private static final int ONE_ENTRY = 1;
    private static final int BOTH_ENDS = 2;

    private CellTooltipContentFit() {
    }

    /**
     * Answers the blocks to draw and the typography to draw them in, so that the box stands no taller
     * than {@code heightBudget} where anything can make it.
     *
     * @param boxAssembly    the whole box - heading, body and footer - assembled at whatever entry
     *                       allowance it is asked for
     * @param typography     the look the box was authored in, before anything is given up
     * @param heightBudget   the tallest the box may stand, in UI units
     * @param longestListing how many entries the body's longest listing holds, which bounds the search:
     *                       no allowance above it takes a tail off anything
     * @return the box as it is to be drawn
     */
    static FittedCellBox fitToHeight(
            BoxAssembly boxAssembly,
            TooltipStyle typography,
            float heightBudget,
            int longestListing) {

        var wholeBox = boxAssembly.assembleWithin(CellTooltipBlocks.NO_ENTRY_LIMIT);
        var compressedStyle = TooltipHeightFit.fitToHeight(wholeBox, typography, heightBudget);

        // Nearly every box: it fits, at its own look or at a compressed one, and nothing it was asked
        // for goes unsaid.
        if (isFittingWithin(wholeBox, compressedStyle, heightBudget)) {
            return new FittedCellBox(wholeBox, compressedStyle);
        }
        var cutBox = boxAssembly.assembleWithin(solveLargestFittingAllowance(
            boxAssembly,
            compressedStyle,
            heightBudget,
            longestListing));

        return new FittedCellBox(
            cutBox,
            TooltipHeightFit.fitToHeight(cutBox, typography, heightBudget));
    }

    // The most entries a listing may draw and the box still fit, searched between the narrowest listing
    // there is and the whole body - which is known to overflow, that being why this is being asked at
    // all.
    //
    // Halved toward the answer from two ends whose verdicts are already settled, so every probe narrows
    // a range whose near end fits and whose far end does not. The near end needs no probe of its own:
    // where even one entry per listing overflows, the box is as short as this pass can make it and is
    // drawn overflowing rather than emptied further.
    //
    // Largest rather than any allowance that fits, because every entry dropped is something the player
    // asked to see: a box that gave up more than the screen required would be quietly answering a
    // question it was never asked.
    //
    // Weighed at the floor the compression reached rather than at a look re-solved per probe. The floor
    // is the shortest the whole box could be made, so an allowance that fits under it fits under the
    // gentler look the cut box is finally drawn in.
    private static int solveLargestFittingAllowance(
            BoxAssembly boxAssembly,
            TooltipStyle compressedStyle,
            float heightBudget,
            int longestListing) {

        var fittingAllowance = CellTooltipBlocks.LEAST_ENTRY_ALLOWANCE;
        var overflowingAllowance = longestListing;

        while (overflowingAllowance - fittingAllowance > ONE_ENTRY) {

            var probedAllowance = fittingAllowance
                + (overflowingAllowance - fittingAllowance) / BOTH_ENDS;

            if (isFittingWithin(
                    boxAssembly.assembleWithin(probedAllowance),
                    compressedStyle,
                    heightBudget)) {

                fittingAllowance = probedAllowance;
            } else {
                overflowingAllowance = probedAllowance;
            }
        }
        return fittingAllowance;
    }

    // Whether a box drawn this way comes inside the room it has. Asked through the widget's own height
    // read, the very one the layout stacks its rows by, so a box weighed as fitting is the box that is
    // then drawn.
    private static boolean isFittingWithin(
            List<TooltipSection> sections,
            TooltipStyle typography,
            float heightBudget) {

        return CursorTooltip.measureBoxHeight(sections, typography) <= heightBudget;
    }

    /**
     * The whole box assembled at one entry allowance - the heading, the body laid out within that
     * allowance, and whatever the footer then says.
     *
     * <p>The whole box rather than the body alone, because what the footer says depends on what the
     * body left out: a box measured from its body and drawn with a line the measurement never saw would
     * be weighed as fitting and drawn overflowing by that line.
     */
    interface BoxAssembly {

        /**
         * @param entryAllowance the most entries any one listing may draw
         * @return the box's blocks, in reading order
         */
        List<TooltipSection> assembleWithin(int entryAllowance);
    }

    /**
     * One box as it is to be drawn: its blocks and the look they are drawn in.
     *
     * <p>The two come back together because they were settled against each other - the look was solved
     * for these blocks, and the blocks were cut against that look. Drawn in any other pairing, a box
     * measured as fitting would not.
     *
     * @param sections   the blocks to draw, in reading order
     * @param typography the look to draw them in
     */
    record FittedCellBox(
        List<TooltipSection> sections,
        TooltipStyle typography) {
    }
}
