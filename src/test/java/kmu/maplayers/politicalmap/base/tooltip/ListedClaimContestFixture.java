package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.BlocFriendliness;

/**
 * A contest nothing groups and nothing warms, which is the state every case about something other
 * than the two relation blocks is posed in.
 *
 * <p>Held as one fixture because both claim suites reach past the box for a contest to hand an
 * account, and the two arguments that say "no alliances, no goodwill" are the same pair of
 * statements each time. Written out per call, they read as choices a case made rather than as the
 * resting state it is posing, and a suite that later wanted a routed contest would have no one
 * place to say so.
 */
final class ListedClaimContestFixture {

    // Nobody above neutral with anybody. Vanilla starts most faction pairs at indifference, so this
    // is the ordinary sector rather than a hostile one - and it is what leaves the friendly block
    // empty over every system a case built here poses.
    private static final BlocFriendliness INDIFFERENT_FACTIONS =
        new BlocFriendliness((factionId, otherFactionId) -> false);

    private ListedClaimContestFixture() {
    }

    /**
     * Projects a contest under a colony rule, with every standing left to sort on eligibility
     * alone.
     *
     * @param breakdown       the contest as the mechanic settled it
     * @param colonyVisibility the colony rule the listing is projected under, which is what a case
     *                         about the fog states
     * @return the contest a box would have read, routed by nothing but eligibility
     */
    static ListedClaimContest buildUnroutedContest(
            SystemClaimBreakdown breakdown,
            ColonyVisibility colonyVisibility) {

        return ListedClaimContest.selectFrom(
            breakdown,
            colonyVisibility,
            BlocAffiliation.NONE,
            INDIFFERENT_FACTIONS);
    }
}
