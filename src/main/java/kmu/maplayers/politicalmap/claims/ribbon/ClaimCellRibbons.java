package kmu.maplayers.politicalmap.claims.ribbon;

import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.ribbon.BlocPresence;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plans the ribbon of a cell painted by the claim mechanic, where the count each bloc's run is
 * made of has to be taken from the contest that settled the claim.
 *
 * <p>A held cell already carries its counts: the map sampled the markets it painted from and the
 * sample says how many each bloc holds. A claimed cell carries none - the claim path resolves one
 * faction id per system and nothing about the colonies behind it - so a ribbon drawn there would
 * otherwise have to go and count markets on its own account, and a second walk under a second rule
 * is a second opinion about which markets a cell means. The contest's own standings are read
 * instead: one walk, one list, and the box explaining a claim and the band beside it cannot
 * disagree about what is in the system.
 *
 * <p>A faction counts its standing market and every sibling of it that affected the score. The
 * standing market is counted unconditionally, being the market the whole standing rests on; a
 * sibling counts where the mechanic's own sibling term counted it and the player knows the colony
 * is there. Two exclusions fall out of that, both of them the design's answer rather than an
 * omission:
 *
 * <ul>
 *   <li>A colony the economy does not list took no part in any term - the mechanic's walk never
 *       reached it - so it adds no segment however plainly it sits on the map.</li>
 *   <li>A colony the player has not found is left out, so a band cannot count out holdings the
 *       rest of the map declines to show. Nothing is withheld by the standing market itself: a
 *       market takes a standing only where it is held in the open, and one held in the open is
 *       one the player knows of.</li>
 * </ul>
 *
 * <p>A faction present in a system through unweighed markets alone therefore never reaches the
 * ribbon at all. The contest lists it, at a nought, but it has no market the count above can start
 * from - so it contributes no run and does not open the presence gate on a cell that is otherwise
 * a lone claimant's.
 *
 * <p>Allied factions fold into one run, since a bloc is what the cell was painted for and two
 * allies' colonies are one bloc's presence. The fold is by count rather than by score, so the
 * double counting a summed score would invite - a sibling term that already counts what is being
 * added beside it - cannot arise.
 *
 * <p>Blocs come out in the contest's own ranking, each at the place of its best-placed member, so
 * the band agrees with the fill about who leads wherever a score settled the claim. Under a decree
 * no score settled it, and nothing is hoisted to say otherwise: the fill states the decree and the
 * band states the contest beneath it.
 *
 * <p>Pure over a finished contest and a grouping, with the one live read - a bloc's shades -
 * inverted to {@link BlocPaletteReader}, so every case is posed on hand-built standings.
 */
public final class ClaimCellRibbons {

    // The market a faction's standing rests on, which its count starts at rather than at nothing.
    // A standing exists only where the mechanic weighed a market for it, so a faction that has one
    // holds at least that market.
    private static final int THE_STANDING_MARKET = 1;

    private ClaimCellRibbons() {
    }

    /**
     * Plans one claimed cell's ribbon from the contest behind its claim.
     *
     * @param paintingBlocId the bloc the cell's fill was painted for - the claimant's bloc, or the
     *                       decreed one where a memory flag took the system outright
     * @param contest        the whole claim contest for the cell's system, standings and all
     * @param grouping       the grouping that folds each standing's faction into its bloc, so a
     *                       band is drawn in the same units the fill was
     * @param inputs         where a bloc's shades are read from, and how far its runs go
     * @return the cell's runs in draw order, or {@link RibbonPlan#NONE} where the gate the counts
     *         are handed to leaves the cell bare
     */
    public static RibbonPlan planClaimCellRibbon(
            String paintingBlocId,
            SystemClaimBreakdown contest,
            HolderGrouping grouping,
            RibbonPlanInputs inputs) {

        return RibbonPlan.planCellRibbon(
            paintingBlocId,
            BlocPresence.collectColouredPresences(
                countMarketsByBloc(contest, grouping),
                inputs.palettes()),
            inputs.rules());
    }

    // Each bloc's colony count in the order the contest ranked them. Insertion order carries that
    // ranking through the fold, so a bloc lands where its best-placed member stood and two allies
    // do not report a place neither of them took.
    private static Map<String, Integer> countMarketsByBloc(
            SystemClaimBreakdown contest,
            HolderGrouping grouping) {

        var marketCountByBlocId = new LinkedHashMap<String, Integer>();

        // Only the standings the contest weighed reach the fold. A presence-only one has no market
        // that carried a score, so there is nothing for the count below to start from - it would
        // have to invent a rule of its own about which colonies of a faction the mechanic passed
        // over are worth a segment, which is the second opinion this whole read exists to avoid.
        for (var standing : contest.scores()) {
            if (standing instanceof WeighedClaimStanding weighedStanding) {
                marketCountByBlocId.merge(
                    grouping.resolveBlocId(weighedStanding.factionId()),
                    countKnownScoringMarkets(weighedStanding),
                    Integer::sum);
            }
        }
        return marketCountByBlocId;
    }

    // How many markets of one faction the ribbon has to report: the market its standing rests on,
    // plus every sibling that both told on the score and is a colony the player knows about.
    private static int countKnownScoringMarkets(WeighedClaimStanding standing) {

        var marketCount = THE_STANDING_MARKET;

        for (var sibling : standing.otherMarkets()) {
            if (isCountedSibling(sibling)) {
                marketCount++;
            }
        }
        return marketCount;
    }

    // Whether a sibling of the standing market earns a segment of its own.
    //
    // The economy's listing is the set the mechanic's sibling term counts over, so a colony
    // outside it moved no score and has nothing for a band to report. Concealment is no bar on
    // that side of the contest - a hidden colony is counted like any other - so a raided base the
    // player has found draws its segment, while one nobody has found stays off a band that would
    // otherwise count out colonies the map itself keeps back.
    private static boolean isCountedSibling(MarketClaimBreakdown sibling) {
        return !sibling.isOffEconomyMarket() && sibling.isKnownToPlayer();
    }
}
