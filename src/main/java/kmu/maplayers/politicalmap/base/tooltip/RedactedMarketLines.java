package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;

import java.util.List;
import java.util.Objects;

/**
 * Whether a claim row may name the market it is drawn for, and what stands in where it may not.
 *
 * <p>The sibling of {@link ListedClaimMarkets}, which settles only that a row is drawn at all. The two
 * rules are asked one after the other over the same market and answer different questions of it, so
 * each is stated apart from the account that spends it - and apart from each other, a market being
 * listed and a market being named having no rule in common.
 *
 * <p>A market the contest weighed on a colony nobody has found is drawn with its name blocked out
 * rather than left off. Its weight is already showing in the numbers on screen - the claim, the
 * faction's score, the difference between a listed market's total and the terms beneath it - so the row
 * is what makes those account for themselves, while the name is the one thing the box has no business
 * stating.
 *
 * <p>What stands in its place is the shape of the name: one block per word, as long as the word ran
 * ({@link kmlib.starsector.ui.text.RedactedSpan}). The name itself never reaches the line, only the
 * lengths - not for secrecy, the row's neighbours bounding what it scored anyway, but for the plainer
 * reason that a value carrying text nothing draws is a value some later change will draw.
 *
 * <p>Such a line opens on a stand-in glyph instead of the map's own. Every other market line opens on
 * an image run, so a line opening on its name would be set apart twice over by the one fact about it;
 * and the map's glyph says what sort of place the colony is, which is the very thing being withheld.
 * Nothing has to be done to match the width - an image run measures one line height whatever it
 * draws - and it reads in the line's own colour like any other market glyph.
 */
final class RedactedMarketLines {

    // Vanilla's stand-in glyph, which says only that something is there. Private here because there is
    // one such path and no second surface reading it to share a home with.
    private static final String REDACTED_MARKET_SPRITE_PATH = "graphics/fx/question_mark.png";

    private RedactedMarketLines() {
    }

    /**
     * Whether a market's row stands for its name instead of stating it.
     *
     * <p>Both halves are the row's warrant. What the contest weighed is already showing in numbers on
     * screen, so the row has to be there for those to add up; what the player has not found is not the
     * box's to name. A market failing either half is drawn as it always was - one the contest passed
     * over accounts for nothing on screen and is dropped by the listing rule instead, and one the
     * player knows of has nothing to withhold.
     *
     * <p>Knowledge is the whole of the second half rather than discovery alone, because the two agree
     * over exactly these markets: a market the contest weighed is held in the open and on the economy's
     * books, so no revelation gate stands over it and the composed answer is the entity's flag.
     *
     * @param market the market a line would be drawn for
     * @return true when the line withholds the market's name
     */
    static boolean isRedactedMarket(MarketClaimBreakdown market) {
        return market.isScoredOnItsOwnAccount() && !market.isKnownToPlayer();
    }

    /**
     * Builds the line a market the box may not name is drawn as: the stand-in glyph, the shape of the
     * name in place of the name, and whatever number the account decided this row may carry.
     *
     * <p>The number is the account's rather than this rule's. What a market is worth to the contest,
     * and which rows may state it, are answered where the contest is read; what is settled here is only
     * that a row withholding its name is still a row of the list, laid in the same two columns as its
     * neighbours.
     *
     * @param market    the market whose name is withheld
     * @param valueText what the block counts this line in, or
     *                  {@link kmu.maplayers.base.tooltip.CellTooltipRows#NO_SCORE} where the row
     *                  carries no number
     * @return the line, its name blocked out
     */
    static CellTooltipEntryLine createRedactedLine(
            MarketClaimBreakdown market,
            String valueText) {

        return CellTooltipEntryLine.createRedactedLine(
            CellTooltipMark.resolveMarkInLineColour(REDACTED_MARKET_SPRITE_PATH),
            measureNameWordLengths(market.marketNameplate()),
            valueText);
    }

    // The shape of a withheld name: how many characters each of its words ran to, in reading order.
    //
    // Derived here, off the nameplate, and the name dropped here - so what travels on is a count per
    // word and nothing a later change could draw. Character counts rather than the name's measured
    // width because counts are what the row shows: measuring the real name would drag it through the
    // layout to arrive at a number.
    //
    // A market with no name at all is refused rather than blocked out to nothing, which would draw as a
    // glyph over blank space and read as a name the box lost. The named line refuses the same market at
    // its own construction, so neither shape can quietly stand in for a colony nothing named.
    private static List<Integer> measureNameWordLengths(EntityNameplate nameplate) {
        return KmlibStrings
            .splitIntoWords(Objects.requireNonNull(nameplate.displayName(), "displayName"))
            .stream()
            .map(String::length)
            .toList();
    }
}
