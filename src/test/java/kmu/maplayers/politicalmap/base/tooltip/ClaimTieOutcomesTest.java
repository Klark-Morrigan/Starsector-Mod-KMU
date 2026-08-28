package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;

import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
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
 * <p>Two kinds of market carry a score yet never compete - a hidden one, which the walk skips before
 * scoring, and one the economy does not list, which the walk never reaches - and each has its own
 * case. A non-territorial faction's markets are a third case rather than a third kind: they can never
 * take the lead, so a tie against the claimant is not judged, while the tie deciding which of them
 * stands for the faction still is.
 *
 * <p>A fourth reason a place goes unmarked is the box's rather than the mechanic's: a tie the walk
 * did settle is left unstated where the market on the other side of it is one the player has not
 * found, since the reader is then shown one half of an ordering and told an outcome about the half
 * they cannot see. Both comparisons are asserted that way round, and both are asserted marked again
 * under the dev reveal, which draws the other side back in.
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

    private static final boolean IS_TERRITORIAL = true;
    private static final boolean IS_HIDDEN = true;
    private static final boolean IS_NOT_HIDDEN = false;
    private static final boolean IS_OFF_ECONOMY = true;
    private static final boolean IS_NOT_OFF_ECONOMY = false;

    // A market's other terms, none of which any case here turns on: the score is stated as the size
    // outright, so a tie is posed by one number rather than assembled from three.
    private static final int NO_SIBLING_MARKETS = 0;

    // Whether the player has found the colony. Independent of everything the admission beside it
    // says: the mechanic weighs a market held in the open whether or not anybody has reached it, so
    // an open market and an unfound one are the same market as often as not.
    private static final boolean IS_KNOWN_TO_PLAYER = true;
    private static final boolean IS_UNFOUND_BY_PLAYER = false;

    // Whether a market the player has not found may be drawn: withheld in play, stated in full under
    // the dev reveal. What parts a tie the box can show both sides of from one it cannot.
    private static final boolean WITHHOLDING_UNFOUND_MARKETS = false;
    private static final boolean LISTING_UNFOUND_MARKETS = true;

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

            assertThat(resolveOutcome(breakdown, standing, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(resolveOutcome(breakdown, standing, sibling))
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

            assertThat(resolveOutcome(breakdown, standing, sibling))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(resolveOutcome(breakdown, standing, otherSibling))
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

            assertThat(resolveOutcome(breakdown, standing, base))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(resolveOutcome(breakdown, standing, standingMarket))
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

            assertThat(resolveOutcome(breakdown, standing, academy))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(resolveOutcome(breakdown, standing, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeLeavesAStandingTiedOnlyWithAWithheldSiblingUnmarked() {
            // The tie was drawn and the mechanic settled it, but only one side of it reaches the
            // list - so a mark would credit the standing market with beating a colony that appears
            // nowhere, which is a finding the reader can neither check nor see the other half of.
            var standingMarket = buildMarket(FIRST_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var unfoundSibling = buildUnfoundMarket(SECOND_LISTED, TIED_SCORE);
            var standing = buildStandingOver(
                TRITACHYON,
                IS_TERRITORIAL,
                standingMarket,
                unfoundSibling);

            assertThat(resolveOutcome(buildContest(HEGEMONY, standing), standing, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeLeavesASiblingUnmarkedWhereTheMarketThatBeatItIsWithheld() {
            // The other way round, and the worse reading of the two: a sibling stated as having lost
            // would be losing to a market the box declined to name, leaving a defeat on the list
            // with no victor anywhere above it.
            var unfoundStandingMarket = buildUnfoundMarket(FIRST_LISTED, TIED_SCORE);
            var sibling = buildMarket(SECOND_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var standing = buildStandingOver(
                TRITACHYON,
                IS_TERRITORIAL,
                unfoundStandingMarket,
                sibling);

            assertThat(resolveOutcome(buildContest(HEGEMONY, standing), standing, sibling))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeMarksATieWithAnUnfoundSiblingUnderTheDevReveal() {
            // The reveal draws both sides, so the ordering is in front of the reader again and the
            // mark has something to answer - the withholding being about what the player has found
            // rather than about what the mechanic settled.
            var standingMarket = buildMarket(FIRST_LISTED, TIED_SCORE, IS_NOT_HIDDEN);
            var unfoundSibling = buildUnfoundMarket(SECOND_LISTED, TIED_SCORE);
            var standing = buildStandingOver(
                TRITACHYON,
                IS_TERRITORIAL,
                standingMarket,
                unfoundSibling);
            var breakdown = buildContest(HEGEMONY, standing);

            assertThat(ClaimTieOutcomes.resolveOutcome(
                    breakdown,
                    standing,
                    standingMarket,
                    LISTING_UNFOUND_MARKETS))
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(ClaimTieOutcomes.resolveOutcome(
                    breakdown,
                    standing,
                    unfoundSibling,
                    LISTING_UNFOUND_MARKETS))
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveOutcomeLeavesAClaimantTiedOnlyWithAWithheldRivalUnmarked() {
            // The cross-faction half of the same rule. The rival is a faction the player has found
            // no colony of, so the box names neither it nor the market it stood on - and a claimant
            // marked as having won would be beating something the reader is never shown.
            var claimant = buildStanding(HEGEMONY, IS_TERRITORIAL, FIRST_LISTED, TIED_SCORE);
            var unfoundRival = buildUnfoundStanding(
                TRITACHYON,
                IS_TERRITORIAL,
                SECOND_LISTED,
                TIED_SCORE);

            assertThat(resolveStandingOutcome(
                    buildContest(HEGEMONY, claimant, unfoundRival),
                    claimant))
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveOutcomeLeavesARivalUnmarkedWhereTheClaimantsOwnMarketIsWithheld() {
            // The claim line still names the holder, but the market that took the system is withheld
            // - so a rival stated as having lost the system on the listing order would be pointing
            // at a colony that is on no list in the box.
            var unfoundClaimant = buildUnfoundStanding(
                HEGEMONY,
                IS_TERRITORIAL,
                FIRST_LISTED,
                TIED_SCORE);
            var rival = buildStanding(TRITACHYON, IS_TERRITORIAL, SECOND_LISTED, TIED_SCORE);

            assertThat(resolveStandingOutcome(
                    buildContest(HEGEMONY, unfoundClaimant, rival),
                    rival))
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

            assertThat(resolveOutcome(breakdown, rival, standingMarket))
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }
    }

    // What the given faction's own strongest market's place decided - the reading every case about the
    // cross-faction comparison is posed on.
    private static CellTooltipIndexOutcome resolveStandingOutcome(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing) {

        return resolveOutcome(breakdown, standing, standing.standingMarket());
    }

    // The reading in play, where a colony the player has not found is kept off the list. Every case
    // but the withheld ones is posed here, their markets all being colonies the player has found, so
    // a case states the reveal only where it is what the case is about.
    private static CellTooltipIndexOutcome resolveOutcome(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            MarketClaimBreakdown market) {

        return ClaimTieOutcomes.resolveOutcome(
            breakdown,
            standing,
            market,
            WITHHOLDING_UNFOUND_MARKETS);
    }

    // A contest the given faction won on the scores, holding the standings posed against it.
    private static SystemClaimBreakdown buildContest(
            String claimantFactionId,
            WeighedClaimStanding... standings) {

        return new SystemClaimBreakdown(null, claimantFactionId, List.of(standings));
    }

    // A faction standing on one market of the given score, holding nothing else in the system.
    private static WeighedClaimStanding buildStanding(
            String factionId,
            boolean isTerritorial,
            int listingPosition,
            int marketScore) {

        return new WeighedClaimStanding(
            factionId,
            isTerritorial,
            buildMarket(listingPosition, marketScore, IS_NOT_HIDDEN),
            List.of());
    }

    // A faction standing on the given market and holding the given others - the shape a case about the
    // within-faction comparison is posed with.
    private static WeighedClaimStanding buildStandingOver(
            String factionId,
            boolean isTerritorial,
            MarketClaimBreakdown standingMarket,
            MarketClaimBreakdown... otherMarkets) {

        return new WeighedClaimStanding(
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

        return buildMarket(
            listingPosition,
            marketScore,
            new ContestAdmission(isHiddenMarket, isOffEconomyMarket),
            IS_KNOWN_TO_PLAYER);
    }

    // A market weighed like any other, on a colony the player has not found - the shape a withheld
    // line takes. Held in the open, since that is what the mechanic weighs it for, and the whole
    // point of the case: what keeps it off the list is the fog rather than anything the contest did.
    private static MarketClaimBreakdown buildUnfoundMarket(int listingPosition, int marketScore) {
        return buildMarket(
            listingPosition,
            marketScore,
            ContestAdmission.WEIGHED,
            IS_UNFOUND_BY_PLAYER);
    }

    // A faction standing on one market of the given score that the player has not found, holding
    // nothing else - the rival shape a cross-faction tie the box cannot show both sides of is posed
    // with.
    private static WeighedClaimStanding buildUnfoundStanding(
            String factionId,
            boolean isTerritorial,
            int listingPosition,
            int marketScore) {

        return new WeighedClaimStanding(
            factionId,
            isTerritorial,
            buildUnfoundMarket(listingPosition, marketScore),
            List.of());
    }

    private static MarketClaimBreakdown buildMarket(
            int listingPosition,
            int marketScore,
            ContestAdmission admission,
            boolean isKnownToPlayer) {

        // Marked with no glyph: what identifies a market beyond its name is nothing a tie is judged
        // on, and this suite reads no line at all - only the outcome a place carries.
        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate("Market " + listingPosition),
            "market_" + listingPosition,
            listingPosition,
            isKnownToPlayer,
            admission,
            marketScore,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }
}
