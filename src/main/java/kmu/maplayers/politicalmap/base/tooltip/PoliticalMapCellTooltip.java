package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.SystemCellTooltip;

import java.util.List;

/**
 * What every political-map view's hover box is beneath the framework's shape: a cell tooltip that may
 * cite the system's claim. Each view explains the mechanic its own fills were painted by, but any of
 * them can have to say the system is held by decree - the claims view as the claim itself, the faction
 * and alliance views as a fact standing over their standings - so the read answering that is bound
 * here rather than by each body.
 *
 * <p>Binding it once is what stops two boxes running on different readers: a decree resolved one way
 * on one tab and another way on the next would answer one hover two ways, a keystroke apart, with
 * nothing on screen to say which was right.
 *
 * <p>A decree is also drawn here, as the banner heading the layer's boxes, rather than by whichever body
 * happens to mention it. It is a fact about the system rather than about the mechanic a view paints by,
 * so it reads the same under all of them - and stating it once is what stops a view added later heading
 * its box with the name alone while the tab beside it names the faction holding the system. A box whose
 * body makes the same statement says so and goes without the banner, so the one decree is met once
 * either way.
 */
public abstract class PoliticalMapCellTooltip extends SystemCellTooltip {

    /**
     * The reader every political-map tooltip runs on in game. Stateless, so the boxes share one
     * rather than each minting its own, and named in a single place so a change of binding cannot
     * reach one view and miss the other.
     *
     * <p>It resolves the player's colony rule per read rather than holding one, since a box
     * lives for the whole session while that rule is a setting the player may move between two
     * hovers.
     */
    static final ClaimBreakdownReader VANILLA_CLAIM_BREAKDOWN_READER =
        new LiveVisibilityClaimBreakdownReader();

    /**
     * The claim read a body draws on - the whole scored contest, or the decree alone, whichever it
     * has something to say about. Held rather than reached for statically, so a body draws on the
     * read it is handed rather than on whichever one a running game happens to have.
     */
    protected final ClaimBreakdownReader claimBreakdownReader;

    protected PoliticalMapCellTooltip(ClaimBreakdownReader claimBreakdownReader) {
        this.claimBreakdownReader = claimBreakdownReader;
    }

    @Override
    protected final List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
        // Whose space this is heads the box rather than sitting in it: a decree settles the system
        // outright, so it is read straight off the system name above it instead of being found among
        // the findings below. Being a title line is also what parts it from the body - the box's break
        // under the heading falls beneath it rather than above it.
        //
        // The decree is read on its own rather than out of the full claim breakdown: a box may only
        // need to know whether one holds the system, and scoring every market in it to answer that
        // would charge the whole claim computation to every faction and alliance hover.
        return CoreTerritoryHeading.resolveHeadingRows(
            sector,
            claimBreakdownReader.readCoreFactionId(system),
            isStatingCoreClaimInBody());
    }

    /**
     * Whether the deeper detail levels would state anything more about the system than the shallowest
     * already does.
     *
     * <p>Asked per hovered system rather than once per box: the deeper tiers account for the colonies
     * behind what this box lists, so a system it lists nothing for has nothing to account for, and
     * every level would draw the same thing. The hint is dropped there rather than offering a key that
     * changes nothing on screen.
     *
     * <p>Answered through the very read the body is built from, so a box cannot offer to expand a
     * listing it is about to draw as empty - which the fog alone can produce, a faction present only
     * through colonies the player has not found leaving nothing this box may state.
     *
     * <p>Re-declared abstract rather than left at the inherited false, because there is no honest
     * default here: a box of this layer that answered false out of inheritance would silently withhold
     * the hint over a system it has plenty more to say about, and nothing on screen would say the key
     * was worth pressing.
     *
     * @param sector the live sector, whose economy the answer may read
     * @param system the star system under the cursor
     * @return true where a deeper level would show the player something the shallowest does not
     */
    @Override
    protected abstract boolean hasDeeperDetailFor(SectorAPI sector, StarSystemAPI system);

    /**
     * Whether this box's own body names the faction holding the system by decree. A box that does goes
     * without the heading, since one hover stating the same decree twice reads as two separate facts
     * about the system rather than as one said over.
     *
     * <p>Answered by the box rather than worked out from what it drew, because it follows from what the
     * body is for rather than from what a given system happened to yield: a body that states the claim
     * states it whenever there is one, and a system under no decree makes the question moot either way.
     *
     * <p>Not stating it is the ordinary case and the default, so a box whose subject is something other
     * than the claim overrides nothing.
     *
     * @return true where the body already states the decree, so the heading would repeat it
     */
    protected boolean isStatingCoreClaimInBody() {
        return false;
    }
}
