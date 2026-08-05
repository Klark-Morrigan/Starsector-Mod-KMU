package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.SystemCellTooltip;

import java.util.ArrayList;
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
 * <p>A decree is also drawn here, as the banner heading every one of the layer's boxes, rather than by
 * whichever body happens to mention it. It is a fact about the system rather than about the mechanic a
 * view paints by, so it reads the same under all of them - and stating it once is what stops a view
 * added later heading its box with the name alone while the tab beside it names the faction holding the
 * system.
 */
public abstract class PoliticalMapCellTooltip extends SystemCellTooltip {

    /**
     * The reader every political-map tooltip runs on in game. Stateless, so the boxes share one
     * rather than each minting its own, and named in a single place so a change of binding cannot
     * reach one view and miss the other.
     */
    static final ClaimBreakdownReader VANILLA_CLAIM_BREAKDOWN_READER =
        new VanillaClaimBreakdownReader();

    /**
     * The claim read a body draws on - the whole scored contest, or the decree alone, whichever it
     * has something to say about. Held rather than reached for statically so a body can be exercised
     * against a known contest without a running game behind it.
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
        // Fixed for the whole layer rather than left to each box, since a view that headed its box with
        // the name alone would answer the same hover differently from the tab beside it.
        //
        // The decree is read on its own rather than out of the full claim breakdown: a box may only
        // need to know whether one holds the system, and scoring every market in it to answer that
        // would charge the whole claim computation to every faction and alliance hover.
        var rows = new ArrayList<TooltipRow>();

        CoreTerritoryRow
            .resolveCoreTerritoryRow(sector, claimBreakdownReader.readCoreFactionId(system))
            .ifPresent(rows::add);

        return rows;
    }
}
