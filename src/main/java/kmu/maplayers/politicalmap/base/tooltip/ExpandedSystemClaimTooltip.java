package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.base.tooltip.CellTooltipEntry;

import java.util.List;

/**
 * The claim contest stated in full: the same factions the ordinary box names, each opened up into the
 * colonies it holds the system with and the terms each colony's score was summed from.
 *
 * <p>Every faction the box names takes one, including a faction the mechanic weighed nothing for: its
 * colonies are exactly what the player can read nowhere else, the line above them stating a nought
 * that says only that the contest passed it over.
 *
 * <p>The counterpart {@link SystemClaimTooltip} offers for the expanded detail mode. The ordinary box
 * answers who claims the system; this one answers on what, which is a different question and a far
 * longer answer - so it is a box the player asks for rather than one they are always given.
 *
 * <p>It is the same contest either way. The claimant, the decree, the ranking, the three headings and
 * the lines naming the factions are all the shared shape's ({@link SystemClaimContestTooltip}), read
 * from the one pass, so the two boxes cannot differ on anything but how far into a faction they go.
 *
 * <p>The parts are read from the very standing the number above them came out of
 * ({@link ClaimScoreRowResolver}), so the lines always add up to the standing the ordinary box shows and
 * the map painted its fill by. A breakdown computed beside the score rather than under it could drift
 * from it, and a box explaining a number it disagrees with is worse than no box.
 *
 * <p>Stateless past the reader it is built around, like the box it stands in for.
 */
public final class ExpandedSystemClaimTooltip extends SystemClaimContestTooltip {

    ExpandedSystemClaimTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected List<CellTooltipEntry> resolveAccountEntries(
            SystemClaimBreakdown breakdown,
            FactionClaimStanding standing) {

        // The mechanic settles a claim over colonies nobody has found, and the box declines to
        // repeat what it learned there. Asked through the shared read, so the account withholds
        // exactly what the listing above it withheld.
        //
        // The whole contest travels with the standing: who the claim holder is and which listing
        // ties actually decided something are facts of the contest, not of one faction's list, and
        // the resolver reads both off the very breakdown this box is drawing.
        return ClaimScoreRowResolver.resolveMarketRows(
            breakdown,
            standing,
            isListingUnfoundMarkets());
    }
}
