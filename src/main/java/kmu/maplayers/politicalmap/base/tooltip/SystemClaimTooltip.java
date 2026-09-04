package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.starsector.nexerelin.NexerelinAlliances;

import java.util.List;

/**
 * The claim contest a political-map layer shows for the hovered star system: who holds it, who stands
 * with the holder, who nearly took it, and who is merely present, each named with its crest and the
 * standing the mechanic weighed it at - and, under every faction named, the colonies it holds the
 * system with and the terms each colony's score was summed from. The one {@link MapHoverTooltip} the
 * claims view injects.
 *
 * <p>What this box decides is the account beneath those lines - how far into a faction the box goes -
 * and it composes one account, read to whatever depth was asked for: who claims the system at the
 * shallowest level, the colonies behind that a tier down, their terms a tier below again - and there
 * the account ends, which is where its cycle wraps. One tree read to three depths rather than three
 * bodies, so no two depths can describe one system differently.
 *
 * <p>What is drawn is settled by the cut the listing is laid out under
 * ({@link kmu.maplayers.base.tooltip.layout.CellTooltipBody}); what is composed stops at the same place, so a
 * hover asking only who claims the system selects, ranks and words no faction's colonies at all.
 *
 * <p>Every faction the box names takes an account, including one the mechanic weighed nothing for: its
 * colonies are exactly what the player can read nowhere else, the line above them stating a nought that
 * says only that the contest passed it over.
 *
 * <p>The parts are read from the very standing the number above them came out of
 * ({@link ClaimScoreRowResolver}), so the lines always add up to the standing the faction's own line
 * shows and the map painted its fill by. A breakdown computed beside the score rather than under it
 * could drift from it, and a box explaining a number it disagrees with is worse than no box.
 *
 * <p>Every colony line says how old the box's news of it is, where nobody is looking at the colony as
 * the box is drawn ({@link ColonyObservationNotes}). It reaches further here than on the domination
 * side: this listing carries two shapes no score accounts for - a concealed base the mechanic skipped
 * before scoring, and a colony the economy does not list - and both are listed at nought, where when it
 * was last seen is the only thing the account has left to add.
 *
 * <p>Stateless past the seams it is built around, so one shared instance serves the layer.
 */
public final class SystemClaimTooltip extends SystemClaimContestTooltip {

    /**
     * The one shared instance, explaining vanilla claims - the same mechanic the layer's fills are
     * resolved through, so the box and the cell under it can never name different claimants.
     *
     * <p>This is where the alliance set behind the allied block is bound, and the only place the claim
     * box names where alliances come from. The gate answers with the identity grouping wherever the mod
     * supplying them is absent, so the box needs no branch of its own and reads as its two eligibility
     * blocks alone on such an install.
     *
     * <p>Deliberately not the claims view's own grouping, which pins itself to identity so the fills
     * stay per claiming faction: that decision is about what the map paints, while the block needs the
     * live alliance set, and reusing the view's would leave it permanently empty on the one layer that
     * draws it.
     */
    public static final SystemClaimTooltip INSTANCE = new SystemClaimTooltip(
        VANILLA_CLAIM_BREAKDOWN_READER,
        NexerelinAlliances::resolveGrouping);

    SystemClaimTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource) {

        super(claimBreakdownReader, holderGroupingSource);
    }

    @Override
    protected HoverTooltipDetailLevel resolveDeepestAccountLevel() {
        // The colonies behind a faction, and the terms behind a colony's score - and there the claim
        // account ends. Vanilla settles a claim on size, a garrison and how many colonies the faction
        // holds beside it; no patrol enters the arithmetic anywhere, so the level below has nothing to
        // show and the cycle collapses from here instead of offering it.
        return HoverTooltipDetailLevel.MARKET_STATS;
    }

    @Override
    protected List<CellTooltipEntry> resolveAccountEntries(
            ListedClaimContest contest,
            FactionClaimStanding standing,
            SystemColonyReading colonyReading,
            HoverTooltipDetailLevel detailLevel) {

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
            contest.colonyVisibility().shouldIncludeUndiscoveredMarkets(),
            detailLevel);
    }
}
