package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;

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
 * <p>A listed market on a colony nobody has found is drawn with its name blocked out rather than left
 * off. Its effect is already showing in the numbers on screen - the claim, the faction's score, the
 * count of markets its faction was paid a point each for - so the row is what makes those account for
 * themselves, while the name is the one thing the box has no business stating.
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
     * <p>Knowledge is the whole of it. Whether the row exists at all is the listing rule's, asked and
     * answered before this one is reached, so restating any part of that here would be a second copy of
     * it free to disagree - and the disagreement would show as a market listed under a name the box had
     * already decided it could not state. What is left for this rule is the one question the listing
     * rule does not answer: whether the player has found the place.
     *
     * <p>Knowledge rather than discovery alone, because a listed market may be a concealed one - and
     * concealment is exactly the case where the two part company, a base being discovered the moment it
     * is raided and still unknown until somebody has seen it standing there.
     *
     * <p>The dev reveal needs no arm here. It is folded into knowledge upstream, where the breakdown is
     * read, so under the reveal every market arrives known and no row withholds anything.
     *
     * @param market the market a line is being drawn for
     * @return true when the line withholds the market's name
     */
    static boolean isRedactedMarket(MarketClaimBreakdown market) {
        return !market.isKnownToPlayer();
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
     *                  {@link CellTooltipEntryLine#NO_SCORE} where the row
     *                  carries no number
     * @return the line, its name blocked out
     */
    static CellTooltipEntryLine createRedactedLine(
            MarketClaimBreakdown market,
            String valueText) {

        return CellTooltipEntryLine.createRedactedLine(
            resolveStandInMark(),
            measureNameWordLengths(market.marketNameplate()),
            valueText);
    }

    /**
     * Builds that same line where the account does state the row's number - the ordinary case for a
     * withheld market, since what is kept back is the name and never the figure.
     *
     * <p>The number travels rather than words for it, so a listing too long to draw whole can be closed
     * by a row summing what it left out: a blocked-out row is one of the list's members and counts
     * towards that sum exactly as a named one does.
     *
     * @param market       the market whose name is withheld
     * @param countedValue what the block counts this line in
     * @return the line, its name blocked out and that number stated
     */
    static CellTooltipEntryLine createCountedRedactedLine(
            MarketClaimBreakdown market,
            int countedValue) {

        return CellTooltipEntryLine.createRedactedCountedLine(
            resolveStandInMark(),
            measureNameWordLengths(market.marketNameplate()),
            countedValue);
    }

    // The glyph a withheld market stands under, drawn in its line's own colour. Shared by both shapes
    // so a row that states its number and one that does not are marked alike.
    private static CellTooltipMark resolveStandInMark() {
        return CellTooltipMark.resolveMarkInLineColour(REDACTED_MARKET_SPRITE_PATH);
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
