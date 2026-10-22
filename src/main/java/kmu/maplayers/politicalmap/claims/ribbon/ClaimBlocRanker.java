package kmu.maplayers.politicalmap.claims.ribbon;

import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.ownermap.owners.holders.base.ColonyCellRibbons;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The order a cell's band comes out in on the claims layer: the contest's own ranking, folded
 * into blocs.
 *
 * <p>The counting is {@link ColonyCellRibbons}'s and is the same on every layer, so what the claim
 * side supplies is the one thing that is its own - where each bloc stands. Blocs come out at the
 * place of their best-placed member, so the band agrees with the fill about who leads wherever a
 * score settled the claim.
 *
 * <p>Under a decree no score settled it, and nothing is hoisted to say otherwise: the fill states
 * the decree and the band states the contest beneath it. A claimant that scored nothing therefore
 * takes its place among the standings like anyone else, which on a decreed system is the foot.
 *
 * <p>Two factions take one place exactly where the pass's grouping already folds them into one
 * bloc, which is the alliances layer: a bloc is what the cell was painted for, and its members'
 * colonies are that bloc's presence. The fold keeps the better-placed member's position, a bloc
 * having no business standing behind where its strongest member stood. Where the grouping is
 * identity - the claims layer - nothing folds: allies stand at their own places in their own
 * colours, and all their standing together reaches is the length the runs are laid at.
 *
 * <p>A bloc the contest never listed - one present only through colonies the mechanic's walk did
 * not reach - is not ranked here at all. Inventing a place for it among the standings would say it
 * took part in a contest it never entered.
 */
final class ClaimBlocRanker {

    private ClaimBlocRanker() {
    }

    /**
     * Ranks the blocs of a claim contest in the order it ranked their factions, each at its
     * best-placed member's position.
     *
     * @param contest  the whole claim contest for the cell's system, standings and all
     * @param grouping the grouping the pass folds factions into blocs under
     * @return the bloc IDs, best placed first
     */
    static List<String> rankBlocsByContest(
            SystemClaimBreakdown contest,
            HolderGrouping grouping) {

        // The standings arrive ranked, so first mention is a bloc's position and a second member
        // of the same bloc adds nothing to say.
        var rankedBlocIds = new LinkedHashSet<String>();

        for (var standing : contest.standings()) {
            rankedBlocIds.add(grouping.resolveBlocId(standing.factionId()));
        }

        return new ArrayList<>(rankedBlocIds);
    }
}
