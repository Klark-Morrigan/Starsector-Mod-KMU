package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.ribbon.ColonyCellRibbons;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The order a claimed cell's band comes out in: the contest's own ranking, folded into blocs.
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
 * <p>Allied factions fold into one place, since a bloc is what the cell was painted for and two
 * allies' colonies are one bloc's presence. The fold keeps the better-placed ally's position, a
 * bloc having no business standing behind where its strongest member stood.
 *
 * <p>A bloc the contest never listed - one present only through colonies the mechanic's walk did
 * not reach - is not ranked here at all, and draws behind the ranked blocs in id order. Inventing
 * a place for it among the standings would say it took part in a contest it never entered.
 *
 * <p>Pure over a finished contest and a grouping, every live read reached through the inputs.
 */
public final class ClaimCellRibbons {

    private ClaimCellRibbons() {
    }

    /**
     * Plans one claimed cell's band, ranked by the contest behind its claim.
     *
     * @param paintingBlocId the bloc the cell's fill was painted for - the claimant's bloc, or the
     *                       decreed one where a memory flag took the system outright
     * @param system         the system the cell draws as, whose colonies the band counts
     * @param contest        the whole claim contest for the cell's system, standings and all
     * @param inputs         the pass the colonies are read from, where a bloc's shades come from,
     *                       and how far its runs go
     * @return the cell's runs in draw order, or {@link RibbonPlan#NONE} where the gate the counts
     *         are handed to leaves the cell bare
     */
    public static RibbonPlan planClaimCellRibbon(
            String paintingBlocId,
            StarSystemAPI system,
            SystemClaimBreakdown contest,
            RibbonPlanInputs inputs) {

        return ColonyCellRibbons.planCellRibbon(
            paintingBlocId,
            system,
            rankBlocsByContest(contest, inputs.grouping()),
            inputs);
    }

    // The blocs of the contest in the order it ranked their factions, each at its best-placed
    // member's position. The standings arrive ranked, so first mention is that position and a
    // second member of the same bloc adds nothing to say.
    private static List<String> rankBlocsByContest(
            SystemClaimBreakdown contest,
            HolderGrouping grouping) {

        var rankedBlocIds = new LinkedHashSet<String>();

        for (var standing : contest.scores()) {
            rankedBlocIds.add(grouping.resolveBlocId(standing.factionId()));
        }
        return new ArrayList<>(rankedBlocIds);
    }
}
