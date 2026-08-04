package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.base.tooltip.SystemCellTooltip;

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
}
