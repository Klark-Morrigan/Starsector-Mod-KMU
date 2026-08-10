package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which listing ties the box marks, which is to say: which comparisons vanilla's own walk
 * actually settles by the order the economy lists markets in.
 *
 * <p>The walk consults that order at two points, and both are asserted here - which market stands for
 * its faction, and which faction claims the system. Everything else that happens to share a score was
 * never compared over anything, and marking it would assert a contest that did not take place. Most of
 * this suite is therefore about what goes <em>un</em>marked, since a mark that appears where the
 * mechanic decided nothing is the failure that reads as a fact.
 *
 * <p>Three kinds of market carry a score yet never compete - a hidden one, which the walk skips before
 * scoring; one the economy does not list, which the walk never reaches; and a non-territorial
 * faction's, which can never take the lead - and each has its own case.
 *
 * <p>How a marked place then draws is the line vocabulary's ({@code CellTooltipRowsTest}); which
 * markets are drawn at all is {@link ClaimScoreRowResolverTest}'s.
 */
final class ClaimTieOutcomesTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    // The score every tie in this suite is posed at, and one clear of it for the cases about a
    // comparison that never arose.
    private static final int TIED_SCORE = 7;
    private static final int LESSER_SCORE = 4;

    // Where each market falls in the system's listing - the whole of what parts two tied markets, so
    // every case states it and the earlier number is the one that wins.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;
    private static final int THIRD_LISTED = 3;

    // No market here is marked with a map glyph. What identifies a market is nothing a tie is judged
    // on, and this suite reads no line at all - only the outcome a place carries.
    private static final Optional<EntityMapIcon> NO_MAP_ICON = Optional.empty();

    private static final boolean IS_TERRITORIAL = true;
    private static final boolean IS_HIDDEN = true;
    private static final boolean IS_NOT_HIDDEN = false;
    private static final boolean IS_OFF_ECONOMY = true;
    private static final boolean IS_NOT_OFF_ECONOMY = false;

    // A market's other terms, none of which any case here turns on: the score is stated as the size
    // outright, so a tie is posed by one number rather than assembled from three.
    private static final int NO_SIBLING_MARKETS = 0;
    private static final boolean IS_KNOWN_TO_PLAYER = true;

    @Nested
    class ResolveOutcome {

        @Test
        void resolveOutcomeMarksTheClaimantsStandingAsHavingWonATieForTheSystem() {
            // The comparison that settles the system: two factions' strongest markets on one score are
            // parted by the listing alone, so the claimant reads as having won it and the rival as
            // having lost - which is the only place either could learn why the system went that way.
            var claimant = buildStanding(HEGEMONY, IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var rival = buildStanding(TRITACHYON, IS_TERRITORIAL, SECOND_LISTED, TIED_SCORE);
            var breakdown = buildContest(HEGEMONY, claimant, rival);

            assertThat(resolveStandingOutcome(breakdown, claimant))
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(resolveStandingOutcome(breakdown, rival))
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveOutcomeLeavesAClaimantThatOutScoredEveryRivalUnmarked() {
            // Winning outright is not winning a tie. The listing decided nothing, and a mark would
            // offer a tie-break to look for that never took place.
            var claimant = buildStanding(HEGEMONY, IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var rival = buildStanding(TRITACHYON, IS_TERRITORIAL, SECOND_LISTED, LESSER_SCORE);
            var breakdown = buildContest(HEGEMONY, claimant, rival);

            assertThat(resolveStandingOutcome(breakdown, claimant))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(resolveStandingOutcome(breakdown, rival))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeLeavesATieWithANonTerritorialFactionUnmarked() {
            // Such a faction's score can never take the lead, so the claimant did not out-list it -
            // there was no rival in that tie to out-list. Marking either would invent a contest the
            // mechanic skipped.
            var claimant = buildStanding(HEGEMONY, IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var outsider = buildStanding(PIRATES, !IS_TERRITORIAL, SECOND_LISTED, TIED_SCORE);
            var breakdown = buildContest(HEGEMONY, claimant, outsider);

            assertThat(resolveStandingOutcome(breakdown, claimant))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(resolveStandingOutcome(breakdown, outsider))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeJudgesNoClaimantTieOverASystemHeldByDecree() {
            // The decree settled the system before a market was weighed, so the listing settled
            // nothing between the two equal standings beneath it.
            var claimant = buildStanding(HEGEMONY, IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var rival = buildStanding(TRITACHYON, IS_TERRITORIAL, SECOND_LISTED, TIED_SCORE);
            var breakdown = new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of(claimant, rival));

            assertThat(resolveStandingOutcome(breakdown, claimant))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(resolveStandingOutcome(breakdown, rival))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeJudgesNoClaimantTieOverASystemNobodyTook() {
            // No claimant means no contest was settled, so no standing won or lost one - a populated
            // system whose every faction is barred from claiming reaches exactly this.
            var outsider = buildStanding(PIRATES, !IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var breakdown = new SystemClaimBreakdown(null, null, List.of(outsider));

            assertThat(resolveStandingOutcome(breakdown, outsider))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeMarksTheMarketThatWonTheRightToStandForItsFaction() {
            // The second comparison the listing settles, and one that runs whether or not the faction
            // went on to take the system: two of its own markets on one score are parted by the order
            // the economy reached them in.
            var standingMarket = buildMarket(FIRST_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var sibling = buildMarket(SECOND_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var standing = buildStandingOver(TRITACHYON, IS_TERRITORIAL, standingMarket, sibling);
            var breakdown = buildContest(HEGEMONY, standing);

            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, sibling))
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveOutcomeLeavesAMarketBelowItsFactionsBestUnmarked() {
            // The standing selection is the one outcome the order decided here, so a market that never
            // came near it settled nothing by its place - and two lesser markets tied below the top
            // won and lost nothing between them.
            var standingMarket = buildMarket(FIRST_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var sibling = buildMarket(SECOND_LISTED, LESSER_SCORE, IS_NOT_HIDDEN);
            var otherSibling = buildMarket(THIRD_LISTED, LESSER_SCORE, IS_NOT_HIDDEN);
            var standing = buildStandingOver(
                TRITACHYON,
                IS_TERRITORIAL,
                standingMarket,
                sibling,
                otherSibling);
            var breakdown = buildContest(HEGEMONY, standing);

            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, sibling))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, otherSibling))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeLeavesAHiddenMarketUnmarkedHoweverItScored() {
            // The walk skips a hidden market before scoring, so it competes in neither comparison -
            // and a standing tied only with one won nothing, there having been no contest to win.
            var standingMarket = buildMarket(FIRST_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var base = buildMarket(SECOND_LISTED, TIED_SCORE, IS_HIDDEN);
            var standing = buildStandingOver(TRITACHYON, IS_TERRITORIAL, standingMarket, base);
            var breakdown = buildContest(HEGEMONY, standing);

            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, base))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeLeavesAMarketTheEconomyDoesNotListUnmarkedHoweverItScored() {
            // The walk covers the economy's markets, so one left off that listing is never reached at
            // all - a different reason from concealment, and the same answer: it took part in neither
            // comparison, and a standing tied only with it won nothing.
            var standingMarket = buildMarket(FIRST_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var academy = buildOffEconomyMarket(SECOND_LISTED, TIED_SCORE);
            var standing = buildStandingOver(TRITACHYON, IS_TERRITORIAL, standingMarket, academy);
            var breakdown = buildContest(HEGEMONY, standing);

            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, academy))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, standing, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeStatesTheClaimantContestAheadOfTheStandingSelection() {
            // A market can draw both comparisons at once. Having lost the system is the larger fact,
            // so it is what the place says: being its faction's own best is a smaller thing than
            // having been beaten to the system over it.
            var standingMarket = buildMarket(SECOND_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var sibling = buildMarket(THIRD_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var rival = buildStandingOver(TRITACHYON, IS_TERRITORIAL, standingMarket, sibling);
            var claimant = buildStanding(HEGEMONY, IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var breakdown = buildContest(HEGEMONY, claimant, rival);

            assertThat(ClaimTieOutcomes.resolveOutcome(breakdown, rival, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }
    }

    // What the given faction's own strongest market's place decided - the reading every case about the
    // cross-faction comparison is posed on.
    private static CellTooltipIndexOutcome resolveStandingOutcome(
            SystemClaimBreakdown breakdown,
            FactionClaimScore standing) {

        return ClaimTieOutcomes.resolveOutcome(breakdown, standing, standing.standingMarket());
    }

    // A contest the given faction won on the scores, holding the standings posed against it.
    private static SystemClaimBreakdown buildContest(
            String claimantFactionId,
            FactionClaimScore... standings) {

        return new SystemClaimBreakdown(null, claimantFactionId, List.of(standings));
    }

    // A faction standing on one market of the given score, holding nothing else in the system.
    private static FactionClaimScore buildStanding(
            String factionId,
            boolean isTerritorial,
            int listingPosition,
            int marketScore) {

        return new FactionClaimScore(
            factionId,
            isTerritorial,
            buildMarket(listingPosition, marketScore, IS_NOT_HIDDEN),
            List.of());
    }

    // A faction standing on the given market and holding the given others - the shape a case about the
    // within-faction comparison is posed with.
    private static FactionClaimScore buildStandingOver(
            String factionId,
            boolean isTerritorial,
            MarketClaimBreakdown standingMarket,
            MarketClaimBreakdown... otherMarkets) {

        return new FactionClaimScore(
            factionId,
            isTerritorial,
            standingMarket,
            List.of(otherMarkets));
    }

    // One market scoring the given number on its size alone, named for its place so a failed assertion
    // says which line of the contest it was about.
    private static MarketClaimBreakdown buildMarket(
            int listingPosition,
            int marketScore,
            boolean isHiddenMarket) {

        return buildMarket(listingPosition, marketScore, isHiddenMarket, IS_NOT_OFF_ECONOMY);
    }

    // A market the economy does not list - held in the open, so the only reason the walk passed over
    // it is the one the case posing it is about.
    private static MarketClaimBreakdown buildOffEconomyMarket(int listingPosition, int marketScore) {
        return buildMarket(listingPosition, marketScore, IS_NOT_HIDDEN, IS_OFF_ECONOMY);
    }

    private static MarketClaimBreakdown buildMarket(
            int listingPosition,
            int marketScore,
            boolean isHiddenMarket,
            boolean isOffEconomyMarket) {

        return new MarketClaimBreakdown(
            "Market " + listingPosition,
            NO_MAP_ICON,
            listingPosition,
            IS_KNOWN_TO_PLAYER,
            new ContestAdmission(isHiddenMarket, isOffEconomyMarket),
            marketScore,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }
}
