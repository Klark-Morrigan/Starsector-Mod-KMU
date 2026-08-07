package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipEntry;

import java.util.List;

/**
 * The claim contest stated in full: the same factions the ordinary box names, each opened up into the
 * colonies it holds the system with and the terms each colony's score was summed from.
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
            FactionClaimScore standing) {

        // A decree settles the system before a single market is weighed, so over one the account is
        // the arithmetic that would have decided it rather than the arithmetic that did - and the
        // strongest market goes uncalled-out, since nothing it scored took the system.
        return ClaimScoreRowResolver.resolveMarketRows(
            standing,
            !KmlibStrings.hasText(breakdown.overrideFactionId()));
    }
}
