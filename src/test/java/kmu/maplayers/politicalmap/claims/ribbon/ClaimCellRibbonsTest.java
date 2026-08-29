package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.ALLIANCE_BLOC_ID;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.DIKTAT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.DIKTAT_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildAlliedInputsOver;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsOver;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildSectorHolding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the order a claimed cell's band comes out in, which is the whole of what the claim side
 * still decides: how many colonies each bloc draws is one shared rule's answer, stated in its own
 * suite.
 *
 * <p>The cases are posed on the three ways a contest's ranking differs from the order a band would
 * otherwise fall into. A rival outscoring the claimant leads the band, since the ranking is the
 * contest's rather than the fill's; a decreed claimant is not hoisted to the head of a contest no
 * score settled; and two allies take one place rather than two, at the better-placed of them.
 *
 * <p>Two further cases state that the bake's alliance set reaches the shared rule through this
 * entry point at all: a cell the claimant shares with an ally alone falls to the shortened runs,
 * and an outsider beside them puts it back on the authored ones. What the rule then does with an
 * affiliation is its own suite's; what these pin is that the claim side hands one over rather than
 * judging every cell as an install with nothing grouping factions.
 *
 * <p>Each case states its system's colonies as well as its standings, since the runs a ranking is
 * read back off are counted from the system rather than from the contest.
 */
final class ClaimCellRibbonsTest {

    private static final String SYSTEM_ID = "corvus";

    // The faction view, where a bloc is a single faction. The alliance case states its own.
    private static final HolderGrouping NO_ALLIANCES = HolderGrouping.identity();

    // No memory flag imposed a claimant, so the contest settled the system on its own. The decree
    // case states the flag instead.
    private static final String NO_DECREE = null;

    // Every faction posed here may claim a system; the ranking reads presence rather than
    // candidacy, so territoriality decides nothing about a band's order.
    private static final boolean IS_TERRITORIAL = true;

    // The scores the standings are posed at. Only their order matters - the band reports counts -
    // so they are stated as a clear lead over a clear second.
    private static final int LEADING_SCORE = 30;
    private static final int TRAILING_SCORE = 10;

    @Nested
    class PlanClaimCellRibbon {

        @Test
        void opensOnTheBlocTheContestRankedFirst() {
            // Tri-Tachyon outscored the Hegemony that took the system, which vanilla's own
            // territoriality gate makes perfectly ordinary. The band is the contest's readout, so
            // it opens where the contest does rather than on the bloc the fill is drawn in.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON);

            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                buildStanding(TRITACHYON, LEADING_SCORE),
                buildStanding(HEGEMONY, TRAILING_SCORE));

            assertThat(planFor(HEGEMONY, contest, sector).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void keepsTheContestsOwnRankingRatherThanHoistingTheClaimant() {
            // The Diktat holds the system by memory flag alone and stands at the foot of the
            // contest for it. Under a decree no score settled the claim, so nothing is put at the
            // head to say one did: the fill states the decree and the band states the contest
            // beneath it.
            var sector = buildSectorHolding(SYSTEM_ID, TRITACHYON, DIKTAT);

            var contest = buildContest(
                DIKTAT,
                DIKTAT,
                buildStanding(TRITACHYON, LEADING_SCORE),
                buildStanding(DIKTAT, TRAILING_SCORE));

            assertThat(planFor(DIKTAT, contest, sector).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }

        @Test
        void ranksAFactionTheContestWeighedNothingForWhereTheContestPutIt() {
            // Tri-Tachyon is in the system through a colony the mechanic never weighed, so the
            // contest lists it at a nought behind the claimant. It draws its run all the same - the
            // count is the system's rather than the contest's - at the place the contest gave it.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON);

            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                buildStanding(HEGEMONY, LEADING_SCORE),
                ClaimStandingFixture.buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL));

            assertThat(planFor(HEGEMONY, contest, sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void placesABlocAtItsBestPlacedMembersPosition() {
            // The Persean League leads the contest and its ally the Hegemony trails Tri-Tachyon.
            // The two fold into one bloc, which stands where the better-placed of them stood -
            // ahead of the rival, not behind it, a bloc having no business standing behind its
            // strongest member.
            var sector = buildSectorHolding(SYSTEM_ID, PERSEAN, TRITACHYON, HEGEMONY);

            var contest = buildContest(
                NO_DECREE,
                PERSEAN,
                buildStanding(PERSEAN, LEADING_SCORE),
                buildStanding(TRITACHYON, TRAILING_SCORE),
                buildStanding(HEGEMONY, TRAILING_SCORE));

            var plan = planThrough(
                ALLIANCE_BLOC_ID,
                contest,
                sector,
                buildInputsOver(sector, buildAllianceOf(HEGEMONY, PERSEAN), BASE_FOG));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void shortensTheRunsWhereTheOnlyOtherBlocStandsWithTheClaimant() {
            // The bake's alliance set has to reach the shared rule through this entry point, or a
            // claimed system two allies share bands as though they fought over it. The fill is per
            // faction here, as it is on the claims layer, so the two keep their own runs and their
            // own places - all their standing together reaches is the length.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON);

            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                buildStanding(HEGEMONY, LEADING_SCORE),
                buildStanding(TRITACHYON, TRAILING_SCORE));

            var plan = planThrough(HEGEMONY, contest, sector, buildAlliedInputsOver(sector));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunLengthWhereARivalStandsBesideTheClaimantAndItsAlly() {
            // The same alliance set with the Diktat outside it. An ally stops being a rival itself
            // and settles nothing about the rest, so the cell is the contest it looks like and
            // keeps the authored lengths under the same shortening.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON, DIKTAT);

            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                buildStanding(HEGEMONY, LEADING_SCORE),
                buildStanding(TRITACHYON, TRAILING_SCORE),
                buildStanding(DIKTAT, TRAILING_SCORE));

            var plan = planThrough(HEGEMONY, contest, sector, buildAlliedInputsOver(sector));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }
    }

    // The one call every case but the alliance one makes: the faction view and the fog where the
    // player finds it, leaving a case to state the contest and the system.
    private static RibbonPlan planFor(
            String paintingBlocId,
            SystemClaimBreakdown contest,
            SectorAPI sector) {

        return planThrough(
            paintingBlocId,
            contest,
            sector,
            buildInputsOver(sector, NO_ALLIANCES, BASE_FOG));
    }

    private static RibbonPlan planThrough(
            String paintingBlocId,
            SystemClaimBreakdown contest,
            SectorAPI sector,
            RibbonPlanInputs inputs) {

        return ClaimCellRibbons.planClaimCellRibbon(
            Optional.of(paintingBlocId),
            buildOnlySystem(sector),
            contest,
            inputs);
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
