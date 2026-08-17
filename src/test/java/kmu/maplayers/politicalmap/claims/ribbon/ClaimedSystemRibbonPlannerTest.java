package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.ribbon.BlocPaletteReader;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanRules;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedCellBands;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the claim planner takes from a contest before handing it to the counting rule: the
 * claimant is the bloc the presence gate is stated against, and a system nobody claims plans
 * nothing.
 *
 * <p>The counting and the ordering both have suites of their own - the band counts the system's own
 * colonies and keeps the contest's ranking. What is stated here is the one thing this planner
 * decides: which bloc counts as the painter. Read it off the wrong faction and a two-bloc system
 * where the claimant is the junior standing draws no band, or a lone claimant's cell draws one the
 * fill never earned.
 */
final class ClaimedSystemRibbonPlannerTest {

    private static final String SYSTEM_ID = "corvus";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);

    // The dark shade a run is closed off in where another bloc's follows. Only Tri-Tachyon's is
    // named, that being the one bloc a case here reads a divider back from.
    private static final Color TRITACHYON_DARK = new Color(20, 90, 80);

    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            new FactionPalette(HEGEMONY_BRIGHT, new Color(40, 60, 120)),
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK))
        ::get;

    private static final RibbonPlanRules STANDARD_RULES =
        new RibbonPlanRules(
            new RibbonSegmentLengths(3, 1),
            new UncontestedCellBands(false, false));

    // No memory flag imposed a claimant, so the contest settled the system on its own.
    private static final String NO_DECREE = null;

    // Every faction posed here may claim a system; this rule reads presence rather than candidacy.
    private static final boolean IS_TERRITORIAL = true;

    // The scores the standings are posed at. Only their order matters, the band reporting counts.
    private static final int LEADING_SCORE = 30;
    private static final int TRAILING_SCORE = 10;

    // The size every posed colony carries, held constant: this rule reads no size, so a case
    // varying it would vary nothing it can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanSystemRibbon {

        @Test
        void statesTheGateAgainstTheClaimantRatherThanTheTopStanding() {
            // The Hegemony holds the claim while Tri-Tachyon stands ahead of it in the contest.
            // The rival is Tri-Tachyon, so a band is drawn - and it keeps the contest's own order,
            // which no part of this planner reaches in to change.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

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
        void drawsNoBandWhereTheClaimantIsTheOnlyBlocStanding() {
            // The gate read off the claimant: the one bloc present is the one the fill already
            // names, so the cell stays bare. Taking the painter from the top standing instead
            // would agree here by accident and disagree wherever the two differ.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                HEGEMONY,
                List.of(buildStanding(HEGEMONY, LEADING_SCORE)));

            assertThat(planFrom(contest, sector))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void plansNoBandForASystemNobodyClaims() {
            // Nothing painted the cell, so there is no painter for the gate to be stated against -
            // a band drawn here would be one no fill asked for.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            var contest = new SystemClaimBreakdown(
                NO_DECREE,
                null,
                List.of(buildStanding(HEGEMONY, LEADING_SCORE)));

            assertThat(planFrom(contest, sector))
                .isEqualTo(RibbonPlan.NONE);
        }
    }

    // The planner over a posed contest, counting the colonies of the stubbed sector's one system.
    private static RibbonPlan planFrom(SystemClaimBreakdown contest, SectorAPI sector) {

        return new ClaimedSystemRibbonPlanner(
                new ClaimBreakdownReaderFake(contest),
                new RibbonPlanInputs(
                    HolderPass.over(
                        sector,
                        false, // Undiscovered colonies are not shown, as on the live map.
                        HolderGrouping.identity()),
                    PALETTES,
                    STANDARD_RULES))
            .planSystemRibbon(buildOnlySystem(sector));
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

    // One faction's weighed standing at the given score. What a standing is made of is the counting
    // rule's business, not this one's, so every case states the score alone.
    private static FactionClaimStanding buildStanding(String factionId, int score) {
        return ClaimStandingFixture.buildStandingOnOneMarket(factionId, score, IS_TERRITORIAL);
    }
}
