package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.ribbon.BlocPaletteReader;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins what the claim planner takes from a contest before handing it to the counting rule: the
 * claimant is the bloc the presence gate is stated against, and a system nobody claims plans
 * nothing.
 *
 * <p>The counting and the ordering both have suites of their own - the band keeps the contest's
 * own ranking, and nothing is hoisted to the front of it. What is stated here is the one thing
 * this planner decides: which bloc counts as the painter. Read it off the wrong faction and a
 * two-bloc system where the claimant is the junior standing draws no band, or a lone claimant's
 * cell draws one the fill never earned.
 */
final class ClaimedSystemRibbonPlannerTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);

    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            new FactionPalette(HEGEMONY_BRIGHT, new Color(40, 60, 120)),
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, new Color(20, 90, 80)))
        ::get;

    private static final RibbonPlanInputs STANDARD_INPUTS =
        new RibbonPlanInputs(PALETTES, new RibbonSegmentLengths(3, 1));

    // No memory flag imposed a claimant, so the contest settled the system on its own.
    private static final String NO_DECREE = null;

    // Where a market falls in the system's listing. No case here poses a tie, so the positions
    // only have to be distinct.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;

    // The size every posed colony carries and the sibling term posed on it, held constant: this
    // rule reads neither, so a case varying either would vary nothing it can see.
    private static final int MARKET_SIZE = 5;
    private static final int NO_SIBLING_MARKETS = 0;

    @Nested
    class PlanSystemRibbon {

        @Test
        void statesTheGateAgainstTheClaimantRatherThanTheTopStanding() {
            // The Hegemony holds the claim while Tri-Tachyon stands ahead of it in the contest.
            // The rival is Tri-Tachyon, so a band is drawn - and it keeps the contest's own order,
            // which no part of this planner reaches in to change.
            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(TRITACHYON, FIRST_LISTED),
                    buildStandingOn(HEGEMONY, SECOND_LISTED)));

            assertThat(planFrom(contest).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void drawsNoBandWhereTheClaimantIsTheOnlyBlocStanding() {
            // The gate read off the claimant: the one bloc present is the one the fill already
            // names, so the cell stays bare. Taking the painter from the top standing instead
            // would agree here by accident and disagree wherever the two differ.
            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                HEGEMONY,
                List.of(buildStandingOn(HEGEMONY, FIRST_LISTED)));

            assertThat(planFrom(contest))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void plansNoBandForASystemNobodyClaims() {
            // Nothing painted the cell, so there is no painter for the gate to be stated against -
            // a band drawn here would be one no fill asked for.
            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                null,
                List.of(buildStandingOn(HEGEMONY, FIRST_LISTED)));

            assertThat(planFrom(contest))
                .isEqualTo(RibbonPlan.NONE);
        }
    }

    private static RibbonPlan planFrom(SystemClaimBreakdown contest) {

        // The contest is answered for whatever system is asked about, so the system itself only
        // has to be the one handed through.
        var systemMock = mock(StarSystemAPI.class);

        return new ClaimedSystemRibbonPlanner(
                new ClaimBreakdownReaderFake(contest),
                HolderGrouping.identity(),
                STANDARD_INPUTS)
            .planSystemRibbon(systemMock);
    }

    // The contest this planner is posed over, however it is asked for it. Its second read - the
    // decree flag - is not this rule's business and is answered as unset.
    private record ClaimBreakdownReaderFake(SystemClaimBreakdown contest)
        implements ClaimBreakdownReader {

        @Override
        public SystemClaimBreakdown readBreakdown(StarSystemAPI system) {
            return contest;
        }

        @Override
        public String readCoreFactionId(StarSystemAPI system) {
            return null;
        }
    }

    // One faction standing on a single colony, which is all these cases need: what a standing is
    // made of is the counting rule's business, not this one's.
    private static FactionClaimScore buildStandingOn(String factionId, int listedPosition) {
        return new FactionClaimScore(
            factionId,
            true, // Every faction posed here may claim; the rule reads presence, not candidacy.
            new MarketClaimBreakdown(
                EntityNameplate.createUnmarkedNameplate("Colony " + listedPosition),
                listedPosition,
                true, // The player knows the colony is there, so it counts toward the band.
                ContestAdmission.WEIGHED,
                MARKET_SIZE,
                NO_SIBLING_MARKETS,
                OptionalInt.empty()),
            List.of());
    }
}
