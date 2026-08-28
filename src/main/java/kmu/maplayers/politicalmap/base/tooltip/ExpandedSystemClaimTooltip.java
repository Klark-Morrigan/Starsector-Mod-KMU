package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;

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
 * <p>It is the same contest either way. The claimant, the decree, the ranking, the four headings and
 * the lines naming the factions are all the shared shape's ({@link SystemClaimContestTooltip}), read
 * from the one pass, so the two boxes cannot differ on anything but how far into a faction they go.
 *
 * <p>The parts are read from the very standing the number above them came out of
 * ({@link ClaimScoreRowResolver}), so the lines always add up to the standing the ordinary box shows and
 * the map painted its fill by. A breakdown computed beside the score rather than under it could drift
 * from it, and a box explaining a number it disagrees with is worse than no box.
 *
 * <p>Every colony line says how old the box's news of it is, where nobody is looking at the colony
 * as the box is drawn ({@link ColonyObservationNotes}). It reaches further here than on the
 * domination side: this listing carries two shapes no score accounts for - a concealed base the
 * mechanic skipped before scoring, and a colony the economy does not list - and both are listed at
 * nought, where when it was last seen is the only thing the account has left to add.
 *
 * <p>Stateless past the seams it is built around, like the box it stands in for.
 */
public final class ExpandedSystemClaimTooltip extends SystemClaimContestTooltip {

    ExpandedSystemClaimTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource) {

        super(claimBreakdownReader, holderGroupingSource);
    }

    @Override
    protected List<CellTooltipEntry> resolveAccountEntries(
            ListedClaimContest contest,
            FactionClaimStanding standing,
            SystemColonyReading colonyReading) {

        // The mechanic settles a claim over colonies nobody has found, and the box declines to
        // repeat what it learned there. The rule saying so is taken off the contest rather than
        // read afresh, so the account withholds exactly what the listing above it withheld - one
        // read of the player's settings serves the whole box, however many factions it lists.
        //
        // The scored read travels with it for the same reason: who the claim holder is and which
        // listing ties actually decided something are facts of the contest, not of one faction's
        // list, and the resolver reads both off the very breakdown this box is drawing.
        return ClaimScoreRowResolver.resolveMarketRows(
            contest.breakdown(),
            standing,
            colonyReading,
            contest.colonyVisibility().shouldIncludeUndiscoveredMarkets());
    }
}
