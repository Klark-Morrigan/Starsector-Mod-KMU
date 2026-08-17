package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;
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
 * Pins the order a claimed cell's band comes out in, which is the whole of what the claim side
 * still decides: how many colonies each bloc draws is one shared rule's answer, stated in its own
 * suite.
 *
 * <p>The cases are posed on the three ways a contest's ranking differs from the order a band would
 * otherwise fall into. A rival outscoring the claimant leads the band, since the ranking is the
 * contest's rather than the fill's; a decreed claimant is not hoisted to the head of a contest no
 * score settled; and two allies take one place rather than two, at the better-placed of them.
 *
 * <p>Each case states its system's colonies as well as its standings, since the runs a ranking is
 * read back off are counted from the system rather than from the contest.
 */
final class ClaimCellRibbonsTest {

    private static final String SYSTEM_ID = "corvus";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PERSEAN = "persean";
    private static final String DIKTAT = "sindria";

    // The alliance the two allies fold into, named by the synthetic id an alliance record carries
    // rather than by either member: a bloc is its own thing, and its shades are its colour
    // faction's, resolved before a band ever asks for them.
    private static final String HEGEMONY_ALLIANCE = "hegemony_compact";

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    private static final Color TRITACHYON_DARK = new Color(20, 90, 80);
    private static final Color DIKTAT_BRIGHT = new Color(220, 150, 120);

    private static final FactionPalette HEGEMONY_PALETTE =
        new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK);

    // The shades every bloc in these cases draws in, read by bloc id exactly as the live map reads
    // them.
    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            HEGEMONY_PALETTE,
            HEGEMONY_ALLIANCE,
            HEGEMONY_PALETTE,
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK),
            DIKTAT,
            new FactionPalette(DIKTAT_BRIGHT, new Color(90, 50, 40)))
        ::get;

    // The design's own proportions - a colony three widths long, parted by one width - paired with
    // the gate a player has left switched off.
    private static final RibbonPlanRules STANDARD_RULES =
        new RibbonPlanRules(
            new RibbonSegmentLengths(3, 1),
            new UncontestedCellBands(false, false));

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

    // The size every posed colony carries. A band counts holdings rather than weighing them, so a
    // case varying it would vary nothing the ranking can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanClaimCellRibbon {

        @Test
        void opensOnTheBlocTheContestRankedFirst() {
            // Tri-Tachyon outscored the Hegemony that took the system, which vanilla's own
            // territoriality gate makes perfectly ordinary. The band is the contest's readout, so
            // it opens where the contest does rather than on the bloc the fill is drawn in.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE),
                buildVisibleMarket(buildFaction(DIKTAT), COLONY_SIZE));

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(PERSEAN), COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE),
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            var contest = buildContest(
                NO_DECREE,
                PERSEAN,
                buildStanding(PERSEAN, LEADING_SCORE),
                buildStanding(TRITACHYON, TRAILING_SCORE),
                buildStanding(HEGEMONY, TRAILING_SCORE));

            var plan = planFor(
                HEGEMONY_ALLIANCE,
                contest,
                buildInputsOver(sector, buildAllianceOf(HEGEMONY, PERSEAN)),
                sector);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }
    }

    // The one call every case but the alliance one makes: the faction view, the shared palettes,
    // and the design's own segment lengths, leaving a case to state the contest and the system.
    private static RibbonPlan planFor(
            String paintingBlocId,
            SystemClaimBreakdown contest,
            SectorAPI sector) {

        return planFor(paintingBlocId, contest, buildInputsOver(sector, NO_ALLIANCES), sector);
    }

    private static RibbonPlan planFor(
            String paintingBlocId,
            SystemClaimBreakdown contest,
            RibbonPlanInputs inputs,
            SectorAPI sector) {

        return ClaimCellRibbons.planClaimCellRibbon(
            paintingBlocId,
            buildOnlySystem(sector),
            contest,
            inputs);
    }

    // The bake's inputs over a stubbed sector: the pass the colonies come out of, with the fog
    // where the player finds it, the shared palettes, and the design's proportions.
    private static RibbonPlanInputs buildInputsOver(SectorAPI sector, HolderGrouping grouping) {

        return new RibbonPlanInputs(
            HolderPass.over(
                sector,
                false, // Undiscovered colonies are not shown, as they are not on the live map.
                grouping),
            PALETTES,
            STANDARD_RULES);
    }

    // A grouping in which the two named factions share one alliance bloc under the alliance's own
    // id, coloured off the first of them - the shape the alliances view folds a bloc in.
    private static HolderGrouping buildAllianceOf(String colourFactionId, String memberFactionId) {
        return new HolderGrouping(
            Map.of(
                colourFactionId,
                HEGEMONY_ALLIANCE,
                memberFactionId,
                HEGEMONY_ALLIANCE),
            Map.of(HEGEMONY_ALLIANCE, colourFactionId),
            Map.of(HEGEMONY_ALLIANCE, "Hegemony Compact"));
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
