package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.FOG_KEPT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.SHORTENED_UNCONTESTED_RULES;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsFor;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsOver;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildSectorHolding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the claim planner takes from a contest before handing it to the counting rule: the
 * claimant is the bloc contest is judged against, and a system nobody claims is planned without
 * one.
 *
 * <p>The counting and the ordering both have suites of their own - the band counts the system's own
 * colonies and keeps the contest's ranking. What is stated here is the one thing this planner
 * decides: which bloc counts as the painter.
 *
 * <p>Every cell with anything in it bands, so the painter shows in the run lengths rather than in
 * whether there is a band: a cell the painter is alone in draws its colonies as a tally, and one
 * with a rival in it at the authored run. The cases that turn on the painter are therefore posed
 * with the shortening on, which is the only setting under which the two lengths differ - read the
 * painter off the wrong faction and a system whose claimant holds nothing in it reports itself as
 * that claimant's quiet footprint instead of the contest it is.
 *
 * <p>The unclaimed case is stated as an absent painter rather than as a refusal, which is what lets
 * the settlements vanilla's claim walk never sees - pirate and Path bases, which it skips as hidden;
 * player colonies, which it skips by name; Remnant stations, whose faction carries no territorial
 * flag - report themselves. This layer's fill is the claim, so on those systems it says nothing at
 * all and the band is the only readout there is.
 */
final class ClaimedSystemRibbonPlannerTest {

    private static final String SYSTEM_ID = "corvus";

    // No memory flag imposed a claimant, so the contest settled the system on its own.
    private static final String NO_DECREE = null;

    // The contest settled on nobody, which on this layer is a cell no fill covers.
    private static final String NO_CLAIMANT = null;

    // Every faction posed here may claim a system; this rule reads presence rather than candidacy.
    private static final boolean IS_TERRITORIAL = true;

    // The scores the standings are posed at. Only their order matters, the band reporting counts.
    private static final int LEADING_SCORE = 30;
    private static final int TRAILING_SCORE = 10;

    @Nested
    class PlanSystemRibbon {

        @Test
        void statesTheGateAgainstTheClaimantRatherThanTheTopStanding() {
            // The Hegemony holds the claim while Tri-Tachyon stands ahead of it in the contest.
            // The rival is Tri-Tachyon, so a band is drawn - and it keeps the contest's own order,
            // which no part of this planner reaches in to change.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON);

            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStanding(TRITACHYON, LEADING_SCORE),
                    buildStanding(HEGEMONY, TRAILING_SCORE)));

            assertThat(planFrom(contest, sector).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void shortensTheRunsWhereTheClaimantIsTheOnlyBlocPresent() {
            // The claimant is the one bloc in the system, so the band adds only how much is there
            // to a fill that has already named the owner - and draws as the tally that says so.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY);

            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                HEGEMONY,
                List.of(buildStanding(HEGEMONY, LEADING_SCORE)));

            assertThat(planWithShorteningFrom(contest, sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunsWhereTheClaimantHoldsNothingInTheSystem() {
            // The painter read off the claimant rather than off the top standing, stated where the
            // two disagree: the Hegemony holds the claim without a colony in the system, and
            // Tri-Tachyon - which stands ahead of it - is the one bloc actually there. Against the
            // claimant that is a rival and the runs stay authored; against the top standing the
            // cell would be Tri-Tachyon's own and shorten into a footprint the fill never named.
            var sector = buildSectorHolding(SYSTEM_ID, TRITACHYON);

            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStanding(TRITACHYON, LEADING_SCORE),
                    buildStanding(HEGEMONY, TRAILING_SCORE)));

            assertThat(planWithShorteningFrom(contest, sector).segments())
                .containsExactly(new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void bandsASystemNobodyClaimsFromTheColoniesInIt() {
            // A haven vanilla's claim walk gives to nobody: two settlers, neither of them holding
            // a market it looks at, so the contest comes back empty and this layer's fill covers
            // the cell with nothing. The band is the only thing that can report the system is
            // settled at all, so it is planned without a painter - and with two blocs in it and
            // nobody to be a rival of, the count is what makes it a contest.
            //
            // Neither bloc is ranked, the contest having listed neither, so both fall to the tail
            // in id order.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON);

            var contest = new SystemClaimBreakdown(NO_DECREE, NO_CLAIMANT, List.of());

            assertThat(planFrom(contest, sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void plansNoBandForASystemNobodyClaimsAndNobodyLivesIn() {
            // The other half of dropping the refusal, and what keeps the widening from banding the
            // empty sector: a system with nothing standing in it counts nought for every bloc, so
            // there is no footprint for a band to report, contested or not.
            var sector = buildSectorHolding(SYSTEM_ID);

            var contest = new SystemClaimBreakdown(NO_DECREE, NO_CLAIMANT, List.of());

            assertThat(planFrom(contest, sector))
                .isEqualTo(RibbonPlan.NONE);
        }
    }

    // The planner over a posed contest, counting the colonies of the stubbed sector's one system.
    private static RibbonPlan planFrom(SystemClaimBreakdown contest, SectorAPI sector) {

        return planThrough(
            contest,
            sector,
            buildInputsOver(sector, HolderGrouping.identity(), FOG_KEPT));
    }

    // The same planner with the uncontested shortening on, which is what a case turning on the
    // painter takes: the run length is the only place that decision shows.
    private static RibbonPlan planWithShorteningFrom(
            SystemClaimBreakdown contest,
            SectorAPI sector) {

        return planThrough(
            contest,
            sector,
            buildInputsFor(
                HolderPass.over(sector, FOG_KEPT, HolderGrouping.identity()),
                SHORTENED_UNCONTESTED_RULES));
    }

    private static RibbonPlan planThrough(
            SystemClaimBreakdown contest,
            SectorAPI sector,
            RibbonPlanInputs inputs) {

        var readerFake = new ClaimBreakdownReaderFake();
        readerFake.setBreakdown(SYSTEM_ID, contest);

        return new ClaimedSystemRibbonPlanner(readerFake, inputs)
            .planSystemRibbon(buildOnlySystem(sector));
    }

    // One faction's weighed standing at the given score. What a standing is made of is the counting
    // rule's business, not this one's, so every case states the score alone.
    private static FactionClaimStanding buildStanding(String factionId, int score) {
        return ClaimStandingFixture.buildStandingOnOneMarket(factionId, score, IS_TERRITORIAL);
    }
}
