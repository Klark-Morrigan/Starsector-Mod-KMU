package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimMarketFixture;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins which markets a claim row may not name, and what such a row shows in the name's place.
 *
 * <p>The rule is asserted across the admissions as well as the knowledge flag, though only the flag
 * decides it. That is the point being pinned: the rule answers the same way whatever the contest made
 * of the market, because which rows exist is the listing rule's question and a copy of it here would be
 * free to disagree with the original.
 *
 * <p>What the line looks like is pinned here alone. Which rows a whole account draws this way, what
 * number one of them carries and what it declines to break into are the resolver's decisions and are
 * pinned by {@link ClaimScoreRowResolverTest} - so the shape is stated once and the account is read
 * for the account's own answers.
 */
final class RedactedMarketLinesTest {

    // The colony the cases are posed on, named in two words of differing length so the blocks standing
    // in for it pin a shape rather than a single number.
    private static final String WITHHELD_MARKET = "Kapteyn Starworks";

    // Whether the player knows of the market at all, which is the whole of the rule. The admission is
    // varied beside it to pin that it changes nothing here.
    private static final boolean IS_KNOWN_TO_PLAYER = true;
    private static final boolean IS_UNKNOWN_TO_PLAYER = false;

    // What the account hands a line to state in its value column. Any string serves - what is pinned is
    // that the line carries it unchanged, the number itself being the resolver's answer and not this
    // rule's.
    private static final String A_STATED_SCORE = "12";

    @Nested
    class IsRedactedMarket {

        @Test
        void isRedactedMarketWithholdsTheNameOfAWeighedMarketThePlayerDoesNotKnow() {
            // Both halves for it at once: the contest weighed the market, so the row has to be there
            // for the numbers on screen to add up, and nobody has found the colony, so the name is not
            // the box's to state.
            assertThat(RedactedMarketLines.isRedactedMarket(
                    buildMarket(ContestAdmission.WEIGHED, IS_UNKNOWN_TO_PLAYER)))
                .isTrue();
        }

        @Test
        void isRedactedMarketNamesAWeighedMarketThePlayerKnowsOf() {
            // Nothing to withhold. The colony is one the player can already see on the map, so blocking
            // its name out would keep back what the map is showing.
            assertThat(RedactedMarketLines.isRedactedMarket(
                    buildMarket(ContestAdmission.WEIGHED, IS_KNOWN_TO_PLAYER)))
                .isFalse();
        }

        @Test
        void isRedactedMarketNamesAMarketTheContestPassedOverThatThePlayerKnowsOf() {
            // A concealed colony somebody has seen standing there. The contest never weighed it, so the
            // row accounts for nothing - it is listed because the player knows of it, and is named for
            // the same reason.
            assertThat(RedactedMarketLines.isRedactedMarket(
                    buildMarket(ContestAdmission.HIDDEN, IS_KNOWN_TO_PLAYER)))
                .isFalse();
        }

        @Test
        void isRedactedMarketWithholdsTheNameOfASiblingCountedMarketThePlayerDoesNotKnow() {
            // A concealed colony nobody has found, which the sibling term counts all the same - so its
            // faction's block is paid a point the block's own rows have to account for. The row is
            // there for that count to add up, and the name is no more the box's to state than a
            // weighed market's would be.
            assertThat(RedactedMarketLines.isRedactedMarket(
                    buildMarket(ContestAdmission.HIDDEN, IS_UNKNOWN_TO_PLAYER)))
                .isTrue();
        }

        @Test
        void isRedactedMarketWithholdsTheNameOfAnUnlistedMarketThePlayerDoesNotKnow() {
            // The shape that never reaches a row at all: off the economy's books and unknown, so the
            // listing rule drops it before this one is asked. Answered on knowledge alone regardless,
            // because restating the listing rule's own question here is what would let the two drift
            // into disagreeing about a market either could answer for.
            assertThat(RedactedMarketLines.isRedactedMarket(
                    buildMarket(ContestAdmission.OFF_ECONOMY, IS_UNKNOWN_TO_PLAYER)))
                .isTrue();
        }
    }

    @Nested
    class CreateRedactedLine {

        @Test
        void createRedactedLineStandsWordBlocksInForTheName() {
            // The shape says how many words there were and how long each ran, and nothing about which
            // letters. The name itself never reaches the line, so there is nothing on it a later change
            // could draw.
            assertThat(buildRedactedLine().redactedWordLengths())
                .containsExactly(7, 9);
        }

        @Test
        void createRedactedLineStatesNoNameBesideTheBlocks() {
            // The exclusive half of the line's own rule, asserted here because this is the caller that
            // has the name in hand and is meant to drop it.
            var line = buildRedactedLine();

            assertThat(line.hasRedactedName())
                .isTrue();
            assertThat(line.labelText())
                .isNull();
        }

        @Test
        void createRedactedLineOpensOnTheStandInGlyph() {
            // Every other market line opens on an image run, so a line opening on its name would be set
            // apart twice over by the one fact about it. The map's own glyph cannot serve - it says
            // what sort of place the colony is, which is exactly what the line withholds.
            assertThat(buildRedactedLine().mark().spritePath())
                .isEqualTo("graphics/fx/question_mark.png");
        }

        @Test
        void createRedactedLineDrawsTheStandInGlyphInTheLinesOwnColour() {
            // The glyph is a shorthand for the name beside it rather than a picture of anything, so it
            // reads with the line like every other market glyph.
            assertThat(buildRedactedLine().mark().isInLineColour())
                .isTrue();
        }

        @Test
        void createRedactedLineIgnoresTheGlyphTheMapMarksTheColonyBy() {
            // Posed on a colony the map does mark, since dropping an authored glyph is a decision
            // rather than an absence: carried through, it would say what sort of place the row declines
            // to name.
            var markedMarket = ClaimMarketFixture
                .startMarket(WITHHELD_MARKET)
                .setNameplate(new EntityNameplate(
                    WITHHELD_MARKET,
                    Optional.of(new EntityMapIcon(
                        "graphics/warroom/icon_planet.png",
                        new Color(120, 200, 90)))))
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(RedactedMarketLines.createRedactedLine(markedMarket, A_STATED_SCORE)
                    .mark()
                    .spritePath())
                .isEqualTo("graphics/fx/question_mark.png");
        }

        @Test
        void createRedactedLineCarriesTheNumberTheAccountHandsIt() {
            // What a row may state is the account's answer rather than this rule's, so the value
            // arrives already decided and is laid in the same column as every other line's.
            assertThat(buildRedactedLine().valueText())
                .isEqualTo(A_STATED_SCORE);
        }

        @Test
        void createRedactedLineCarriesAnEmptyColumnWhereTheAccountStatesNoNumber() {
            // The ordinary reading: the column collapses rather than showing a figure for a place the
            // player has not found.
            assertThat(RedactedMarketLines
                    .createRedactedLine(buildWithheldMarket(), CellTooltipEntryLine.NO_SCORE)
                    .valueText())
                .isEqualTo(CellTooltipEntryLine.NO_SCORE);
        }

        @Test
        void createRedactedLineRefusesAMarketWithNoNameToWithhold() {
            // Blocked out to nothing, such a market would draw as a glyph over blank space and read as
            // a name the box lost. The named line refuses the same market at its own construction, so
            // neither shape quietly stands in for a colony nothing named.
            var namelessMarket = ClaimMarketFixture
                .startMarket(WITHHELD_MARKET)
                .setNameplate(EntityNameplate.createUnmarkedNameplate(null))
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThatThrownBy(() ->
                    RedactedMarketLines.createRedactedLine(namelessMarket, A_STATED_SCORE))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // The line every case about the shape reads, over the colony the box may not name.
    private static CellTooltipEntryLine buildRedactedLine() {
        return RedactedMarketLines.createRedactedLine(buildWithheldMarket(), A_STATED_SCORE);
    }

    // The colony the box may not name: weighed by the contest, and unknown to the player.
    private static MarketClaimBreakdown buildWithheldMarket() {
        return buildMarket(ContestAdmission.WEIGHED, IS_UNKNOWN_TO_PLAYER);
    }

    // One market stated by the two facts the rule turns on, everything else being the fixture's
    // ordinary colony - so a case varies exactly what it is about.
    private static MarketClaimBreakdown buildMarket(
            ContestAdmission admission,
            boolean isKnownToPlayer) {

        return ClaimMarketFixture
            .startMarket(WITHHELD_MARKET)
            .setAdmission(admission)
            .setKnownToPlayer(isKnownToPlayer)
            .buildMarket();
    }
}
