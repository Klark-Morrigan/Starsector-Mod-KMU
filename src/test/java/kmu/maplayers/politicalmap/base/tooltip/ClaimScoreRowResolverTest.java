package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which lines a faction's claim standing breaks down into and what hangs beneath what: the colonies
 * under the faction holding them, and the terms of a score under the colony that scored it.
 *
 * <p>What a term that never arose looks like is most of what is asserted here, because it is the
 * difference between an account and a form: a colony with no sibling and no garrison has no line for
 * either, rather than two lines insisting they counted for nothing.
 *
 * <p>The mechanic behind the numbers is KMLib's and has its own suite there, so what is left is what
 * this resolver alone decides - which colony leads, which is marked, and which terms are stated.
 */
final class ClaimScoreRowResolverTest {

    private static final String HEGEMONY = "hegemony";

    // The colony every case stands its faction on, sized so a term added to it is plainly a separate
    // number rather than one that could be read out of the size.
    private static final String STANDING_MARKET = "Chicomoztoc";
    private static final int STANDING_SIZE = 7;

    // Vanilla's flat garrison bonus. Stated as a literal rather than read from the mechanic, so a case
    // asserting the line shows it cannot pass by restating whatever the reader happened to hand over.
    private static final int MILITARY_BONUS = 10;

    // How many other colonies a faction holds in the system, in the two readings the cases turn on:
    // none, so the term never arose, and two, so the line has a number to state.
    private static final int NO_SIBLING_MARKETS = 0;
    private static final int TWO_SIBLING_MARKETS = 2;

    private static final boolean IS_TERRITORIAL = true;

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveMarketRows {

        @Test
        void resolveMarketRowsLeadsWithTheColonyTheStandingRestsOnAndMarksIt() {
            // The faction's number above is this one colony's score, so the reader following it
            // downward has to be told which of the colonies listed here it came out of.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of(buildMarket("Culann", 3))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STANDING_MARKET, "Culann");
            assertThat(rows.get(0).line().qualifierText())
                .isEqualTo("standing");
        }

        @Test
        void resolveMarketRowsMarksNoColonyBesidesTheOneTheStandingRestsOn() {
            // A second marked line would say the faction stands on two colonies at once, which is
            // exactly what the mechanic does not do.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of(buildMarket("Culann", 3))));

            assertThat(rows.get(1).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsRanksTheRemainingColoniesStrongestFirst() {
            // The colonies read strongest first for the same reason the factions above them do: the
            // account of a standing opens on what came nearest to being it.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of(buildMarket("Culann", 3), buildMarket("Eventide", 5))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STANDING_MARKET, "Eventide", "Culann");
        }

        @Test
        void resolveMarketRowsBreaksATieByNameSoTheOrderNeverDependsOnTheEconomyWalk() {
            // Two colonies of a faction can score exactly the same, and left to the order the economy
            // handed them over the box would list them one way on one hover and the other on the next.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of(buildMarket("Eventide", 4), buildMarket("Culann", 4))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STANDING_MARKET, "Culann", "Eventide");
        }

        @Test
        void resolveMarketRowsStatesTheColonysOwnScoreBesideIt() {
            // The colony's line carries the number its terms below add up to, so the account can be
            // checked one level at a time rather than only at the faction.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildFullyScoredMarket(),
                List.of()));

            assertThat(rows.get(0).line().valueText())
                .isEqualTo("19");
        }

        @Test
        void resolveMarketRowsOpensAColonyOnTheSizeItsScoreStartsFrom() {
            // The size is the term the sum starts from, so it heads the terms and is stated even where
            // it is the whole of the score - a colony listing no term at all would read as a number
            // with no account behind it.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of()));

            assertThat(readLabelTexts(rows.get(0).children()))
                .containsExactly("Size");
            assertThat(rows.get(0).children().get(0).line().valueText())
                .isEqualTo("7");
        }

        @Test
        void resolveMarketRowsStatesTheSiblingTermOnlyWhereTheFactionHoldsAnotherColonyHere() {
            // The count is exactly the colonies listed beside it, which is what lets a reader check it
            // rather than take it on trust - and a "+0" on a lone colony would invite them to look for
            // a sibling that is not there.
            var withSiblings = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                new MarketClaimBreakdown(
                    STANDING_MARKET,
                    STANDING_SIZE,
                    TWO_SIBLING_MARKETS,
                    OptionalInt.empty()),
                List.of()));

            assertThat(readLabelTexts(withSiblings.get(0).children()))
                .containsExactly("Size", "Colonies");
            assertThat(withSiblings.get(0).children().get(1).line().valueText())
                .isEqualTo("+2");

            var alone = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of()));

            assertThat(readLabelTexts(alone.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveMarketRowsStatesTheGarrisonTermOnlyForAMilitaryColony() {
            // The bonus is a flat constant a garrison earns, so an absent one is a colony that is no
            // garrison rather than a garrison worth nothing - two different colonies a "+0" would
            // print alike.
            var garrisoned = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                new MarketClaimBreakdown(
                    STANDING_MARKET,
                    STANDING_SIZE,
                    NO_SIBLING_MARKETS,
                    OptionalInt.of(MILITARY_BONUS)),
                List.of()));

            assertThat(readLabelTexts(garrisoned.get(0).children()))
                .containsExactly("Size", "Military");
            assertThat(garrisoned.get(0).children().get(1).line().valueText())
                .isEqualTo("+10");

            var civilian = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of()));

            assertThat(readLabelTexts(civilian.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveMarketRowsListsATermsOwnAccountNoDeeper() {
            // The claim score is one addition deep. A term breaking down further would be inventing an
            // arithmetic the mechanic does not have.
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildFullyScoredMarket(),
                List.of()));

            assertThat(rows.get(0).children())
                .allSatisfy(term -> assertThat(term.children()).isEmpty());
        }

        @Test
        void resolveMarketRowsListsOnlyItsStandingForAFactionHoldingNothingElseHere() {
            var rows = ClaimScoreRowResolver.resolveMarketRows(buildStanding(
                buildMarket(STANDING_MARKET, STANDING_SIZE),
                List.of()));

            assertThat(rows)
                .hasSize(1);
        }
    }

    // A faction standing on the given colony and holding the given others in the system. Territorial
    // throughout: which block a standing is listed under is the box's to decide, and no case here is
    // about it.
    private static FactionClaimScore buildStanding(
            MarketClaimBreakdown standingMarket,
            List<MarketClaimBreakdown> otherMarkets) {

        return new FactionClaimScore(HEGEMONY, IS_TERRITORIAL, standingMarket, otherMarkets);
    }

    // A colony every term of the score arose on, for the cases about the whole sum rather than about
    // one term of it: its own size, two siblings beside it, and a garrison on it.
    private static MarketClaimBreakdown buildFullyScoredMarket() {
        return new MarketClaimBreakdown(
            STANDING_MARKET,
            STANDING_SIZE,
            TWO_SIBLING_MARKETS,
            OptionalInt.of(MILITARY_BONUS));
    }

    // A plain colony: it scores its size alone, with no sibling beside it and no garrison on it. The
    // baseline the cases above add one term at a time to.
    private static MarketClaimBreakdown buildMarket(String marketName, int marketSize) {
        return new MarketClaimBreakdown(
            marketName,
            marketSize,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }
}
