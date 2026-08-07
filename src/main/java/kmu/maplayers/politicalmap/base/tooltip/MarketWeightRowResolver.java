package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.settings.HiddenMarketScalingChoice;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the arithmetic behind a faction's dominance score into the entries a block lists it as:
 * each of its colonies, and beneath each the factors its weight was summed from.
 *
 * <p>What turns the numbers the map painted by into an account a player can follow. The score a
 * faction holds a system with is a sum of sums, and the only useful way to read a sum is to see it
 * broken apart - so a colony is listed under the faction holding it, a factor under the colony it
 * moved, and a patrol tier under the garrison it is part of, each level being one step of the same
 * arithmetic.
 *
 * <p>Depth is the subject matter's here rather than the entry model's, which is the point of the
 * model nesting at all: the walk that lays these out reads the tier off how deep it went, so this
 * resolver states only what breaks down into what.
 *
 * <p>A factor that did not run has no line. Which of them ran is already settled by the breakdown
 * read - the station and patrol parts are absent when the player has the factor off - and only the
 * stability line, which is a cause rather than a factor, is gated here on the rule that makes it
 * one. So the box shows exactly the factors that moved the number, and a player who has switched a
 * factor off is not shown a line insisting it counted for nothing.
 *
 * <p>Pure over a breakdown and a rule with no Starsector types, so a whole box's worth of lines is
 * exercised on hand-built parts. How one line's numbers read is {@link MarketFactorText}'s.
 */
public final class MarketWeightRowResolver {

    // Colonies rank descending by the weight they folded in at, a tie falling to the lowest name so
    // the order is total and never depends on the economy walk order the breakdowns arrive in - the
    // same rule the standings above them are ranked by.
    private static final Comparator<MarketWeightBreakdown> MARKET_ORDER =
        Comparator
            .comparingInt(MarketWeightBreakdown::computeTotalWeight)
            .reversed()
            .thenComparing(MarketWeightBreakdown::marketName);

    // A colony and its factors are named rather than crested: the faction line above already carries
    // the crest, and repeating it down every line below would read as a second holder each time.
    private static final String NO_CREST = null;

    // A tier nobody fields is not listed. Its line would state a garrison the colony does not have,
    // and the reader is looking for what the weight is made of, not what it is not.
    private static final int NO_PATROLS = 0;

    private MarketWeightRowResolver() {
    }

    /**
     * Resolves the colonies behind one faction's score into the entries listed beneath it, strongest
     * first, each carrying the factors its own weight was summed from.
     *
     * @param breakdowns the faction's counted colonies in the hovered system, in any order
     * @param rules      the weighting rules the pass resolved under, which decide whether stability
     *                   is a cause worth stating
     * @return one entry per colony in ranked order, each carrying its factor lines; empty when the
     *         faction holds no counted colony in the system
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            List<MarketWeightBreakdown> breakdowns,
            DominanceRules rules) {

        return breakdowns
            .stream()
            .sorted(MARKET_ORDER)
            .map(breakdown -> resolveMarketEntry(breakdown, rules))
            .toList();
    }

    // One colony as the entry it is listed as: its name and the weight it folded in at, over the
    // factors that weight is the sum of.
    private static CellTooltipEntry resolveMarketEntry(
            MarketWeightBreakdown breakdown,
            DominanceRules rules) {

        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(
                NO_CREST,
                breakdown.marketName(),
                KmlibNumbers.formatGroupedInteger(breakdown.computeTotalWeight())))
            .nesting(resolveFactorEntries(breakdown, rules));
    }

    // The factors of one colony, in the order the weight read applied them - the stability that
    // decides every cut first, so the deductions below it read as consequences of a stated cause
    // rather than as three unexplained subtractions.
    private static List<CellTooltipEntry> resolveFactorEntries(
            MarketWeightBreakdown breakdown,
            DominanceRules rules) {

        var entries = new ArrayList<CellTooltipEntry>();

        // Stability is stated only where it can cost the colony something: with the master
        // weighting off it moves no factor, and a line for it would read as a cause of cuts that
        // are all zero.
        if (rules.isStabilityWeighted()) {
            entries.add(createFactorEntry(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_STABILITY),
                MarketFactorText.formatStability(breakdown.marketStability())));
        }
        entries.add(resolveBaseSizeEntry(breakdown, rules));

        // The station line names the station rather than the factor, so the number ties to
        // something the player can find on the map.
        breakdown.station().ifPresent(station -> entries.add(createFactorEntry(
            station.stationName(),
            MarketFactorText.formatStation(station))));

        breakdown.patrols().ifPresent(patrols -> entries.add(resolvePatrolEntry(patrols)));
        return entries;
    }

    // The base-size factor's line. A hidden colony calls that out on the line itself rather than on
    // one of its own, since being hidden is not a further factor but the reason two of them rate
    // this colony the way they do.
    //
    // Where the fixed token replaced the colony's own size, the line opens on that real size in the
    // quiet shade. Without it the value states a size the colony does not have and nothing to read
    // it against, which is the one case a player is most likely to take for a bug in the map.
    private static CellTooltipEntry resolveBaseSizeEntry(
            MarketWeightBreakdown breakdown,
            DominanceRules rules) {

        var isFixedRating = isFixedRating(breakdown, rules);
        var line = createFactorLine(
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE),
            MarketFactorText.formatBaseSize(breakdown.baseSize(), isFixedRating));

        if (isFixedRating) {
            line = line.derivesValueFrom(
                MarketFactorText.formatRawSizeWorking(breakdown.baseSize().rawMarketSize()));
        }
        if (breakdown.isHiddenMarket()) {
            line = line.qualifiedWith(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_HIDDEN));
        }
        return CellTooltipEntry.createEntry(line);
    }

    // Whether the size that entered the weight is the fixed token a hidden colony folds in at
    // rather than its own. Read off the rule and the colony together, because the token applies
    // only where both hold - a visible colony always counts by its real size.
    private static boolean isFixedRating(MarketWeightBreakdown breakdown, DominanceRules rules) {
        return breakdown.isHiddenMarket()
            && rules.baseSize().hiddenMarketScaling() == HiddenMarketScalingChoice.FIXED;
    }

    // The garrison as a line over the tiers making it up: one heading number the reader can check
    // against the map, then what each kind of patrol in it counted for.
    private static CellTooltipEntry resolvePatrolEntry(PatrolFactor patrols) {
        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(
                NO_CREST,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS),
                MarketFactorText.formatPatrols(patrols)))
            .nesting(resolveTierEntries(patrols));
    }

    // The garrison's tiers heaviest last, as the settings list them, so a reader comparing two
    // colonies' garrisons reads them in one order.
    private static List<CellTooltipEntry> resolveTierEntries(PatrolFactor patrols) {
        
        var entries = new ArrayList<CellTooltipEntry>();

        appendTierEntry(entries, KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_SMALL, patrols.small());
        appendTierEntry(entries, KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_MEDIUM, patrols.medium());
        appendTierEntry(entries, KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_LARGE, patrols.large());

        return entries;
    }

    // One tier's line, and nothing at all for a tier the colony fields none of. The count sits in
    // the label beside the tier's name because it is what the tier is, while the value column
    // carries what it was worth - the same split every line in the box reads by.
    //
    // The value is stated in its two halves rather than as one number, because a tier's is two
    // things joined by a separator: what one patrol of the tier is worth, and what the tier came to.
    // Handed over apart, the box draws the rate quieter than the total it explains.
    private static void appendTierEntry(
            List<CellTooltipEntry> entries,
            String tierNameKey,
            PatrolTierFactor tier) {

        if (tier.count() <= NO_PATROLS) {
            return;
        }
        var line = createFactorLine(
                KmuStrings.format(
                    KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_TIER,
                    KmuStrings.get(tierNameKey),
                    tier.count()),
                MarketFactorText.formatPatrolTierTotal(tier))
            .derivesValueFrom(MarketFactorText.formatPatrolTierWorking(tier));

        entries.add(CellTooltipEntry.createEntry(line));
    }

    // A factor line that breaks down no further, which is every one of them bar the garrison.
    private static CellTooltipEntry createFactorEntry(String labelText, String valueText) {
        return CellTooltipEntry.createEntry(createFactorLine(labelText, valueText));
    }

    // The shape every line beneath a colony takes: named, uncrested, and carrying its number. Shared
    // by the lines that go on to state something more - a hidden colony's size, a tier's rate - so
    // that stating more is one refinement rather than a second spelling of the line itself.
    private static CellTooltipEntryLine createFactorLine(String labelText, String valueText) {
        return CellTooltipEntryLine.createLine(NO_CREST, labelText, valueText);
    }
}
