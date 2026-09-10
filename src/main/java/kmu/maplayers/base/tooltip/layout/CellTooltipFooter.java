package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.HoverTooltipDetailLevelInput;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The line a hover box ends on, and the rule behind it: what one press of the cycle key would do to
 * this box, and how many entries the box had no room to show.
 *
 * <p>Both halves are the box speaking about its own account rather than about the system, which is
 * why they share one line and one quiet shade - and why the line is not content: a box with nothing
 * to say about the system is not drawn at all rather than drawn as a lone offer to expand into
 * nothing.
 *
 * <p>{@link #resolveOfferedLevel} lives here rather than beside either caller because it is one rule
 * with two entries - the hint this draws, and the key the input pass claims. Settled separately, the
 * two are one edit away from a box advertising a key that does nothing, or swallowing a press it
 * said nothing about.
 */
final class CellTooltipFooter {

    // What a box that drew everything it was asked for withheld. Named so the composition below reads
    // as asking whether anything was left out rather than as comparing against a bare zero.
    private static final int NOTHING_WITHHELD = 0;

    // The two halves of the line a given box may have nothing for: a box at a level that offers no
    // further reading of this system, and one that had room for all of it. Named so the composition
    // states what the line is missing rather than handing it unexplained nulls.
    private static final String NO_OFFER = null;
    private static final String NOTHING_TO_STATE = null;

    private CellTooltipFooter() {
    }

    /**
     * The block the box ends on, or none where it has neither half to state.
     *
     * <p>Its own block, so the shared parting sets it off from the content the way any two blocks are
     * set off - a line about the box read as the last line of a list would be taken for one of that
     * list.
     *
     * <p>The offer is taken from what the composition already found rather than from a read of its
     * own: it answers a fact about the body beside it, and a second read would charge the whole
     * layer's economy walk to a line of fine print, once per frame the cursor rests on the cell.
     *
     * @param detailLevel        how deep the box is being drawn
     * @param deepestHeldLevel   the deepest level this box holds anything at for the hovered system
     * @param withheldEntryCount how many entries the box had no room to draw, over the whole box
     * @return the footer block, or empty where the press would show nothing new and nothing was cut
     */
    static Optional<TooltipSection> buildSection(
            HoverTooltipDetailLevel detailLevel,
            HoverTooltipDetailLevel deepestHeldLevel,
            int withheldEntryCount) {

        var offeredLevel = resolveOfferedLevel(detailLevel, deepestHeldLevel);
        var isStatingWithheld = withheldEntryCount > NOTHING_WITHHELD;

        if (offeredLevel.isEmpty() && !isStatingWithheld) {
            return Optional.empty();
        }
        // What the press would be called is the arriving level's to say rather than this line's, so
        // every layer names one step the same way.
        return Optional.of(TooltipSection.createSection(List.of(buildRow(
            offeredLevel.map(HoverTooltipDetailLevel::resolveArrivalPhrase).orElse(NO_OFFER),
            isStatingWithheld
                ? formatWithheldPhrase(withheldEntryCount)
                : NOTHING_TO_STATE))));
    }

    /**
     * Where one press would take a box read at {@code detailLevel} whose own tree ends at
     * {@code deepestHeldLevel} - the one rule behind both the hint drawn here and the key the input
     * pass claims.
     *
     * <p>Empty where the press would redraw the box exactly as it stands, which is judged on the
     * depth the box is <em>cut</em> at rather than on the level named: a box holds nothing past its
     * own bound, so two levels either side of that bound cut it identically. That is the whole of the
     * case for a box holding nothing past the shallowest level - one drawing over an unpopulated
     * system, met at whatever depth the player reached over a populated one - which offers neither a
     * tier to open nor a collapse the reader would see. The way back out of a deep level is any box
     * that does have depth, since a level nothing here draws is a level nothing here has to escape.
     *
     * @param detailLevel      how deep the box is being read now, which the press moves on from
     * @param deepestHeldLevel the deepest level this box holds anything at for the hovered system
     * @return the level one press moves to, or empty where the press would change nothing the player
     *         can see
     */
    static Optional<HoverTooltipDetailLevel> resolveOfferedLevel(
            HoverTooltipDetailLevel detailLevel,
            HoverTooltipDetailLevel deepestHeldLevel) {

        var nextLevel = detailLevel.resolveNextLevelWithin(deepestHeldLevel);
        var isRedrawingTheSameBox = nextLevel.resolveDrawnLevelWithin(deepestHeldLevel)
            == detailLevel.resolveDrawnLevelWithin(deepestHeldLevel);

        return isRedrawingTheSameBox
            ? Optional.empty()
            : Optional.of(nextLevel);
    }

    // The line itself, in the game's own colours for the job: the key picked out in the shade every
    // vanilla button highlights its shortcut with, the words about it in the grey vanilla states such
    // hints in, and the figure for what the box left out in that same grey. Runs rather than one string
    // because that is exactly what vanilla draws - the key is the part the eye is meant to find, and the
    // sentence around it is deliberately quiet.
    //
    // The withheld figure is quiet for a reason of its own: it is the box speaking about its own
    // account rather than about the system, which is the shade all such asides are stated in. What it
    // stands for is loud enough where it happened, on the rows standing in for the entries.
    //
    // Composed from whichever runs the line has rather than branched over, so a line missing one half is
    // the same line short a run. Laid at the box's content edge rather than centred: it sits at the foot
    // of the box the way the game's own does, and centring it would read as a verdict over the content
    // above.
    private static TooltipRow buildRow(String phrase, String withheldPhrase) {

        var runs = new ArrayList<TextSpan>();

        if (phrase != NO_OFFER) {

            runs.add(new TextSpan(
                HoverTooltipDetailLevelInput.CYCLE_KEY_NAME,
                StarsectorUiColour.VANILLA_BUTTON_SHORTCUT.resolve()));

            runs.add(new TextSpan(phrase, StarsectorUiColour.VANILLA_GRAY.resolve()));
        }
        if (withheldPhrase != NOTHING_TO_STATE) {
            runs.add(new TextSpan(withheldPhrase, StarsectorUiColour.VANILLA_GRAY.resolve()));
        }
        var remainingRuns = runs.iterator();
        var row = TooltipRow
            .createRow(remainingRuns.next())
            .clearsCrestColumn();

        while (remainingRuns.hasNext()) {
            row = row.continuesWith(remainingRuns.next());
        }
        return row.readsAs(TooltipLineStyle.FOOTNOTE);
    }

    // What the line says about the entries the box could not fit - the count over the whole box,
    // whichever listings the cut fell in.
    private static String formatWithheldPhrase(int withheldEntryCount) {
        return KmuStrings.format(KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_WITHHELD, withheldEntryCount);
    }
}
