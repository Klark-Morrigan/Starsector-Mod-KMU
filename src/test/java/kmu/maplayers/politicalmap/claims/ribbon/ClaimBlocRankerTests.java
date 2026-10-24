package kmu.maplayers.politicalmap.claims.ribbon;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.buildGroupOf;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the order a claimed cell's band comes out in, which is the whole of what the claim side
 * still decides: how many colonies each bloc draws is one shared rule's answer, stated in its own
 * suite.
 *
 * <p>The cases are posed on the ways a contest's ranking differs from the order a band would
 * otherwise fall into. A rival outscoring the claimant leads the band, since the ranking is the
 * contest's rather than the fill's; a decreed claimant is not hoisted to the head of a contest no
 * score settled; a faction the contest weighed nothing for keeps the place the contest gave it; and
 * two allies take one place rather than two, at the better-placed of them.
 */
final class ClaimBlocRankerTests {

    // The faction view, where a bloc is a single faction. The alliance case states its own.
    private static final HolderGrouping NO_ALLIANCES = HolderGrouping.identity();

    // No memory flag imposed a claimant, so the contest settled the system on its own. The decree
    // case states the flag instead.
    private static final String NO_DECREE = null;

    // Every faction posed here may claim a system; the ranking reads presence rather than
    // candidacy, so territoriality decides nothing about a band's order.
    private static final boolean IS_TERRITORIAL = true;

    // The scores the standings are posed at. Only their order matters, so they are stated as a
    // clear lead over a clear second.
    private static final int LEADING_SCORE = 30;
    private static final int TRAILING_SCORE = 10;

    @Nested
    class RankBlocsByContest {

        // Tri-Tachyon outscored the Hegemony that took the system, which vanilla's own
        // territoriality gate makes perfectly ordinary. The band is the contest's readout, so it
        // opens where the contest does rather than on the bloc the fill is drawn in.
        @Test
        void opensOnTheBlocTheContestRankedFirst() {

            // ARRANGE
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                buildStanding(TRITACHYON, LEADING_SCORE),
                buildStanding(HEGEMONY, TRAILING_SCORE));

            // ACT
            var actualBlocIds = ClaimBlocRanker.rankBlocsByContest(contest, NO_ALLIANCES);

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(TRITACHYON, HEGEMONY);
        }

        // The Diktat holds the system by memory flag alone and stands at the foot of the contest
        // for it. Under a decree no score settled the claim, so nothing is put at the head to say
        // one did: the fill states the decree and the band states the contest beneath it.
        @Test
        void keepsTheContestsOwnRankingRatherThanHoistingTheClaimant() {

            // ARRANGE
            var contest = buildContest(
                DIKTAT,
                DIKTAT,
                buildStanding(TRITACHYON, LEADING_SCORE),
                buildStanding(DIKTAT, TRAILING_SCORE));

            // ACT
            var actualBlocIds = ClaimBlocRanker.rankBlocsByContest(contest, NO_ALLIANCES);

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(TRITACHYON, DIKTAT);
        }

        // Tri-Tachyon is in the system through a colony the mechanic never weighed, so the contest
        // lists it at a nought behind the claimant. It is ranked where the contest put it rather
        // than left to the unranked tail, since the contest did list it.
        @Test
        void ranksAFactionTheContestWeighedNothingForWhereTheContestPutIt() {

            // ARRANGE
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                buildStanding(HEGEMONY, LEADING_SCORE),
                ClaimStandingFixture.buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL));

            // ACT
            var actualBlocIds = ClaimBlocRanker.rankBlocsByContest(contest, NO_ALLIANCES);

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(HEGEMONY, TRITACHYON);
        }

        // The Persean League leads the contest and its ally the Hegemony trails Tri-Tachyon. The
        // two fold into one bloc, which stands where the better-placed of them stood - ahead of the
        // rival, not behind it, a bloc having no business standing behind its strongest member.
        @Test
        void placesABlocAtItsBestPlacedMembersPosition() {

            // ARRANGE
            var contest = buildContest(
                NO_DECREE,
                PERSEAN,
                buildStanding(PERSEAN, LEADING_SCORE),
                buildStanding(TRITACHYON, TRAILING_SCORE),
                buildStanding(HEGEMONY, TRAILING_SCORE));

            // ACT
            var actualBlocIds = ClaimBlocRanker.rankBlocsByContest(
                contest,
                buildGroupOf(HEGEMONY, PERSEAN));

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(GROUP_BLOC_ID, TRITACHYON);
        }
    }

    // A finished contest, stated as the mechanic's reader would build it: the decree if any, who
    // ended up with the system, and the standings ranked as they were scored.
    private static SystemClaimBreakdown buildContest(
            String decreeFactionId,
            String claimantFactionId,
            FactionClaimStanding... standings) {

        return new SystemClaimBreakdown(decreeFactionId, claimantFactionId, List.of(standings));
    }

    // One faction's weighed standing at the given score. What the standing is made of is the
    // shared counting rule's business rather than this one's, so every case states the score alone.
    private static FactionClaimStanding buildStanding(String factionId, int score) {
        return ClaimStandingFixture.buildStandingOnOneMarket(factionId, score, IS_TERRITORIAL);
    }
}
