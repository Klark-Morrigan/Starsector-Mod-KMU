package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.ownermap.tooltip.SystemColonyReading;

import java.util.Objects;

/**
 * What one paint of a claims box knows about the hovered system, beside the standing being accounted
 * for: the whole contest that standing was ranked in, what the box may say about the colonies behind
 * it, which colonies it may name at all, and how deep the player asked it to read.
 *
 * <p>All four are settled once, before any faction is accounted for, and hold for every market line
 * in the box - so they travel as one value rather than down each call in turn. Threaded loose, a
 * later step could be handed the colony rule of one read beside the contest of another, and the box
 * would explain one reading of the system under another's.
 *
 * <p>The contest travels whole because an account needs two things of it that no single standing
 * carries: who the claim holder is, and which listing ties actually decided something.
 *
 * @param breakdown                    the whole scored contest the standing was ranked in
 * @param colonyReading                what the box may say about the system's colonies beyond their
 *                                     scores, folded once for the box - a claim row carries the ID
 *                                     of the market it was scored from and nothing of the place
 *                                     behind it, so this is what parts an unowned collapse from an
 *                                     unowned derelict, says the player has yet to find either, and
 *                                     dates them
 * @param isListingUndiscoveredMarkets whether a market on an undiscovered entity may be listed
 *                                     though the contest never weighed it. False is the ordinary
 *                                     state and leaves those markets off; true is the dev reveal,
 *                                     under which the account is stated in full
 * @param detailLevel                  how deep the box was asked to read, which the terms beneath a
 *                                     market are worked out only as far as
 */
public record ClaimAccountReading(
    SystemClaimBreakdown breakdown,
    SystemColonyReading colonyReading,
    boolean isListingUndiscoveredMarkets,
    HoverTooltipDetailLevel detailLevel) {

    public ClaimAccountReading {

        Objects.requireNonNull(breakdown, "breakdown");
        Objects.requireNonNull(colonyReading, "colonyReading");
        Objects.requireNonNull(detailLevel, "detailLevel");
    }
}
