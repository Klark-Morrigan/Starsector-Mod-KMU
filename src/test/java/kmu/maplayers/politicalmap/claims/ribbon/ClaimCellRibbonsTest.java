package kmu.maplayers.politicalmap.claims.ribbon;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
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
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a claimed cell's band counts: which of a faction's colonies earn a segment, which are
 * left out and why, and which cells get a band at all.
 *
 * <p>The counting rule is the whole of this step, so the cases are posed on the two axes a colony
 * can fall off it. The mechanic's own side - a colony the economy never listed took part in no
 * term, while a concealed one told on the sibling count like any other. The player's side - a
 * colony nobody has found is left out, so a band cannot count out what the map declines to show.
 * The two are independent, and a case states one of them at a time.
 *
 * <p>Every row of the design's gate table that a claim can reach is stated here, decree included,
 * since the gate is what decides whether a cell is drawn on at all. The decree rows are the ones
 * worth posing: the cell is painted for a bloc holding nothing in the system, so the band is made
 * of rivals alone or of nothing.
 *
 * <p>The segment rule itself has its own suite, so what is asserted here is the counts reaching
 * it - read off the runs, those being the only place a count is visible.
 */
final class ClaimCellRibbonsTest {

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

    private static final FactionPalette HEGEMONY_PALETTE =
        new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK);

    private static final FactionPalette TRITACHYON_PALETTE =
        new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK);

    // The shades every bloc in these cases draws in, read by bloc id exactly as the live map reads
    // them. A bloc absent from the map has no colour to resolve, which is the drop case.
    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            HEGEMONY_PALETTE,
            HEGEMONY_ALLIANCE,
            HEGEMONY_PALETTE,
            TRITACHYON,
            TRITACHYON_PALETTE)
        ::get;

    // The design's own proportions - a market three widths long, parted by one width - paired
    // with the colours every case reads its runs back in.
    private static final RibbonPlanInputs STANDARD_INPUTS =
        new RibbonPlanInputs(
            PALETTES,
            new RibbonPlanRules(
                new RibbonSegmentLengths(3, 1),
                new UncontestedCellBands(false, false)));

    // The faction view, where a bloc is a single faction. The alliance case states its own.
    private static final HolderGrouping NO_ALLIANCES = HolderGrouping.identity();

    // No memory flag imposed a claimant, so the contest settled the system on its own. The decree
    // cases state the flag instead.
    private static final String NO_DECREE = null;

    // How the mechanic met a market. A concealed colony is skipped before scoring and counted by
    // the sibling term all the same; one the economy does not list is never reached at all.
    private static final ContestAdmission CONCEALED = new ContestAdmission(true, false);
    private static final ContestAdmission OFF_ECONOMY = new ContestAdmission(false, true);

    // Whether the player has found the colony. A concealed one they have raided is known and
    // concealed at once, which is the pair the counting rule has to keep apart.
    private static final boolean IS_KNOWN_TO_PLAYER = true;
    private static final boolean IS_UNFOUND_BY_PLAYER = false;

    // Every faction posed here may claim a system; the rule counts presence rather than candidacy,
    // so territoriality decides nothing about a band.
    private static final boolean IS_TERRITORIAL = true;

    // Where a market falls in the system's economy listing. No case here poses a tie - the rule
    // reads no score and settles nothing on the order - so the positions only have to be distinct,
    // as the economy's own numbering makes them.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;
    private static final int THIRD_LISTED = 3;
    private static final int FOURTH_LISTED = 4;

    // The size every posed colony carries, and the sibling term posed on it. Both are held
    // constant because a claim band counts holdings and reads nothing off a score: a case varying
    // either would be varying nothing the rule under test can see.
    private static final int MARKET_SIZE = 5;
    private static final int NO_SIBLING_MARKETS = 0;

    @Nested
    class PlanClaimCellRibbon {

        @Test
        void drawsNoRibbonWhereTheClaimantIsTheOnlyFactionStanding() {
            // The lone-claimant row of the gate table: the fill already says whose system it is,
            // and a band of the painter alone would only repeat it.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(buildStandingOn(HEGEMONY, FIRST_LISTED)));

            assertThat(planFor(HEGEMONY, contest))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonForAFactionTheContestNeverWeighed() {
            // Tri-Tachyon is in the system through unweighed colonies alone - a concealed base,
            // say - so the contest lists it at a nought with no market that carried a score. The
            // count above has nothing to start from there, so the cell reads as the claimant's
            // alone and stays bare rather than sprouting a rival's run of an invented length.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    ClaimStandingFixture.buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL)));

            assertThat(planFor(HEGEMONY, contest))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsBothFactionsWhereAnotherStandsBesideTheClaimant() {
            // The contested row: Hegemony took the system and Tri-Tachyon is in it, so the band
            // is a colony each, parted by the divider closing the Hegemony's run.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    buildStandingOn(TRITACHYON, SECOND_LISTED)));

            assertThat(planFor(HEGEMONY, contest).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsNoRibbonOverADecreedSystemNobodyIsPresentIn() {
            // The empty decree row: the Diktat holds an unsettled system by memory flag alone, so
            // there is no presence anywhere for a band to report.
            var contest = buildContest(DIKTAT, DIKTAT, List.of());

            assertThat(planFor(DIKTAT, contest))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsThePresentFactionAloneOverADecreedSystemItsHolderHoldsNothingIn() {
            // The decree row the gate is phrased on the painter for: the Diktat holds the system
            // by decree and nothing in it, so the band is Tri-Tachyon's two colonies by itself.
            var contest = buildContest(
                DIKTAT,
                DIKTAT,
                List.of(buildStandingOn(
                    TRITACHYON,
                    FIRST_LISTED,
                    buildMarket(SECOND_LISTED, ContestAdmission.WEIGHED, IS_KNOWN_TO_PLAYER))));

            assertThat(planFor(DIKTAT, contest).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void countsAConcealedSiblingThePlayerHasFound() {
            // A raided base is concealed and found at once. The mechanic's sibling term counted
            // it and the player knows it is there, so it earns a segment like any other colony.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    buildStandingOn(
                        TRITACHYON,
                        SECOND_LISTED,
                        buildMarket(THIRD_LISTED, CONCEALED, IS_KNOWN_TO_PLAYER))));

            assertThat(planFor(HEGEMONY, contest).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutASiblingTheEconomyDoesNotList() {
            // A station the economy never listed took part in no term of the contest, so the band
            // that reports what moved the score has nothing to say about it.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    buildStandingOn(
                        TRITACHYON,
                        SECOND_LISTED,
                        buildMarket(THIRD_LISTED, OFF_ECONOMY, IS_KNOWN_TO_PLAYER))));

            assertThat(planFor(HEGEMONY, contest).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutASiblingThePlayerHasNotFound() {
            // The colony told on the score, but the map declines to show it - so a band counting
            // it out would state the very holding the rest of the map is keeping back.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    buildStandingOn(
                        TRITACHYON,
                        SECOND_LISTED,
                        buildMarket(THIRD_LISTED, CONCEALED, IS_UNFOUND_BY_PLAYER))));

            assertThat(planFor(HEGEMONY, contest).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsNoRibbonWhereTheOnlyOtherFactionIsAnAllyOfThePainter() {
            // Two allies hold the system between them and nobody else is in it. They fold into the
            // one bloc the cell was painted for before the gate is asked anything, so the cell is
            // as bare as a lone claimant's - where a gate asked ahead of the fold would read an
            // ally as a rival and band every system an alliance shares.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    buildStandingOn(PERSEAN, SECOND_LISTED)));

            var plan = ClaimCellRibbons.planClaimCellRibbon(
                HEGEMONY_ALLIANCE,
                contest,
                buildAllianceOf(HEGEMONY, PERSEAN),
                STANDARD_INPUTS);

            assertThat(plan)
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void foldsAlliedFactionsIntoOneRunInTheBlocsOwnShades() {
            // Two allies hold one system between them. The cell is painted for their bloc, so the
            // band is that bloc's three colonies in one run rather than two neighbouring runs -
            // and the rival beside them is what the presence gate opens on.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(
                        HEGEMONY,
                        FIRST_LISTED,
                        buildMarket(SECOND_LISTED, ContestAdmission.WEIGHED, IS_KNOWN_TO_PLAYER)),
                    buildStandingOn(TRITACHYON, THIRD_LISTED),
                    buildStandingOn(PERSEAN, FOURTH_LISTED)));

            var plan = ClaimCellRibbons.planClaimCellRibbon(
                HEGEMONY_ALLIANCE,
                contest,
                buildAllianceOf(HEGEMONY, PERSEAN),
                STANDARD_INPUTS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutABlocWithNoShadesToDrawIn() {
            // The degenerate case of a bloc whose colour faction has gone from the sector: it
            // drops out of the band rather than drawing colourless, exactly as an unresolved
            // claim drops out of the fills.
            var contest = buildContest(
                NO_DECREE,
                HEGEMONY,
                List.of(
                    buildStandingOn(HEGEMONY, FIRST_LISTED),
                    buildStandingOn(DIKTAT, SECOND_LISTED),
                    buildStandingOn(TRITACHYON, THIRD_LISTED)));

            assertThat(planFor(HEGEMONY, contest).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void keepsTheContestsOwnRankingRatherThanHoistingTheClaimant() {
            // Tri-Tachyon outscores the Diktat that holds the system by decree, and leads the band
            // for it: under a decree no score settled the claim, so nothing is put at the head to
            // say one did.
            var contest = buildContest(
                DIKTAT,
                DIKTAT,
                List.of(
                    buildStandingOn(TRITACHYON, SECOND_LISTED),
                    buildStandingOn(HEGEMONY, FIRST_LISTED)));

            assertThat(planFor(DIKTAT, contest).segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }
    }

    // The one call every case but the alliance one makes: the faction view, the shared palettes,
    // and the design's own segment lengths, leaving a case to state the contest and the painter.
    private static RibbonPlan planFor(String paintingBlocId, SystemClaimBreakdown contest) {
        return ClaimCellRibbons.planClaimCellRibbon(
            paintingBlocId,
            contest,
            NO_ALLIANCES,
            STANDARD_INPUTS);
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
            List<FactionClaimStanding> standings) {

        return new SystemClaimBreakdown(decreeFactionId, claimantFactionId, standings);
    }

    // A faction standing at the given place in the listing, holding the given other colonies
    // beside the one it stands on. The standing market is the one thing no case varies: only a
    // market the mechanic weighed can take a standing, and such a market is held in the open and
    // so is one the player knows of - which is why the counting rule reads no flag of it.
    private static WeighedClaimStanding buildStandingOn(
            String factionId,
            int listingPosition,
            MarketClaimBreakdown... otherMarkets) {

        return new WeighedClaimStanding(
            factionId,
            IS_TERRITORIAL,
            buildMarket(listingPosition, ContestAdmission.WEIGHED, IS_KNOWN_TO_PLAYER),
            List.of(otherMarkets));
    }

    // One colony, stated by the two things the counting rule reads of it: how the mechanic met it,
    // and whether the player has found it. Its name and its score are carried because a market
    // needs them, and nothing here reads either.
    private static MarketClaimBreakdown buildMarket(
            int listingPosition,
            ContestAdmission admission,
            boolean isKnownToPlayer) {

        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate("Colony " + listingPosition),
            listingPosition,
            isKnownToPlayer,
            admission,
            MARKET_SIZE,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }
}
