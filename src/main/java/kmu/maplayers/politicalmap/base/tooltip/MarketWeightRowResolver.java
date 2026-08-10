package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.UnweighedColony;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.settings.HiddenMarketScalingChoice;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the arithmetic behind a faction's dominance score into the entries a block lists it as:
 * each of its colonies, and beneath each the factors its weight was summed from.
 *
 * <p>What turns the numbers the map painted by into an account a player can follow. The score a
 * faction holds a system with is a sum of sums, and the only useful way to read a sum is to see it
 * broken apart - so a colony is listed under the faction holding it, a factor under the colony it
 * moved, and a patrol tier under the patrol factor it is part of, each level being one step of the
 * same arithmetic.
 *
 * <p>Depth is the subject matter's here rather than the entry model's, which is the point of the
 * model nesting at all: the walk that lays these out reads the tier off how deep it went, so this
 * resolver states only what breaks down into what.
 *
 * <p>Every colony line leads with the glyph the sector map marks that colony's entity with, weighed or
 * not, so a reader can tie a name in the list back to something they are looking at rather than to
 * something they have to remember. It is drawn in the colony name's own colour rather than the map's,
 * so it identifies the line without competing with the numbers the box exists to state. The lines
 * beneath a colony carry no mark at all: a stability or a size is a term of arithmetic with nothing on
 * the map to point at.
 *
 * <p>A colony the pass never weighed is listed all the same, at the foot of the list and at nought.
 * The player can see the station on the map in a faction's colours, so an account of the system that
 * omitted it would be withholding something they are looking straight at - and the nought is the
 * whole of what the account has to say about it: it is there, and it moved nothing.
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

    // A colony the pass never weighed is ranked by name alone, having no weight to be ranked by -
    // which is the rule the weighed colonies fall back on at a tie, so one order runs down the
    // whole list rather than two.
    private static final Comparator<UnweighedColony> UNWEIGHED_ORDER =
        Comparator.comparing(UnweighedColony::marketName);

    // A factor line is named rather than marked: a stability or a size is a term of arithmetic with
    // nothing on the map to point at, so a glyph there would stand in for a number. The colony line
    // above them leads with the map's own icon, which is a thing the player can go and find.
    private static final String NO_CREST = null;

    // What a colony whose entity the game marks with no glyph leads with. Named rather than passed as
    // a bare null, so the colony line below reads as "this colony has no icon" instead of as an
    // unexplained absence.
    private static final String NO_ICON = null;

    // What a colony the pass never weighed folded in at. Nought rather than a blank column, because
    // the colony is on the list and the reader is being told what it counted for.
    private static final int NO_WEIGHT = 0;

    // A tier nobody fields is not listed. Its line would state patrols the colony does not have,
    // and the reader is looking for what the weight is made of, not what it is not.
    private static final int NO_PATROLS = 0;

    private MarketWeightRowResolver() {
    }

    /**
     * Resolves the colonies behind one faction's score into the entries listed beneath it, strongest
     * first, each carrying the factors its own weight was summed from - closed by the colonies the
     * pass never weighed, which are named at nought and break down into nothing.
     *
     * @param breakdowns        the faction's counted colonies in the hovered system, in any order
     * @param unweighedColonies the faction's colonies in the system that the economy does not list,
     *                          which no weight was worked out for
     * @param rules             the weighting rules the pass resolved under, which decide whether
     *                          stability is a cause worth stating
     * @return one entry per colony, the weighed ones ranked ahead of the unweighed; empty when the
     *         faction holds no colony at all in the system
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            List<MarketWeightBreakdown> breakdowns,
            List<UnweighedColony> unweighedColonies,
            DominanceRules rules) {

        var entries = new ArrayList<CellTooltipEntry>();

        breakdowns
            .stream()
            .sorted(MARKET_ORDER)
            .forEach(breakdown -> entries.add(resolveMarketEntry(breakdown, rules)));

        // Last whatever they would rank at, because they never ranked: sorted in among the weighed
        // colonies by a nought they were never given, they would sit above a colony that was
        // weighed and came to nothing, which is a comparison neither number can bear.
        unweighedColonies
            .stream()
            .sorted(UNWEIGHED_ORDER)
            .forEach(colony -> entries.add(resolveUnweighedEntry(colony)));

        return List.copyOf(entries);
    }

    // One colony as the entry it is listed as: its name and the weight it folded in at, over the
    // factors that weight is the sum of.
    private static CellTooltipEntry resolveMarketEntry(
            MarketWeightBreakdown breakdown,
            DominanceRules rules) {

        return CellTooltipEntry
            .createEntry(createColonyLine(
                breakdown.marketName(),
                breakdown.marketIcon(),
                KmlibNumbers.formatGroupedInteger(breakdown.computeTotalWeight())))
            .nesting(resolveFactorEntries(breakdown, rules));
    }

    // A colony the pass never weighed, as the entry it is listed as: named as loudly as the colonies
    // above it, at nought, and breaking down into no factors - none of them ran, so there is nothing
    // beneath it to state.
    //
    // The nought says the whole of it, exactly as the claims box's does. It is the account's
    // statement about the colony rather than anything the colony scored, so it reads in the quiet
    // shade: in the list's own colour it would pass for a weight competed with and lost on, which is
    // the one thing it is not.
    private static CellTooltipEntry resolveUnweighedEntry(UnweighedColony colony) {
        return CellTooltipEntry.createEntry(createColonyLine(
                colony.marketName(),
                colony.marketIcon(),
                KmlibNumbers.formatGroupedInteger(NO_WEIGHT))
            .statesUncountedValue());
    }

    // One colony's own line: its name led by the glyph the map marks its entity with, and whatever
    // the account counted it for.
    //
    // The icon is what ties a name in this list back to something the player is looking at. A name
    // alone does that only for a reader who already remembers it, while the glyph is the one thing
    // the box and the map can share at a glance.
    //
    // It is drawn in the colony name's own colour rather than in the shade the map paints it. Those
    // shades are authored to tell one world from another across a black sector map, and carried into
    // a text box unchanged they arrive brighter than every number the account is actually about - a
    // row of coloured glyphs down the list reads as the finding, when what it is is a bullet point.
    //
    // Read off the breakdown the colony arrived in rather than looked up here, so the glyph shown is
    // the glyph of the very colony whose number sits beside it.
    private static CellTooltipEntryLine createColonyLine(
            String marketName,
            Optional<EntityMapIcon> marketIcon,
            String valueText) {

        return CellTooltipEntryLine
            .createLine(
                marketIcon.map(EntityMapIcon::spritePath).orElse(NO_ICON),
                marketName,
                valueText)
            .readsMarkInLineColour();
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

    // The patrol factor as a line over the tiers making it up: one heading number the reader can
    // check against the map, then what each kind of patrol in it counted for.
    private static CellTooltipEntry resolvePatrolEntry(PatrolFactor patrols) {
        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(
                NO_CREST,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS),
                MarketFactorText.formatPatrols(patrols)))
            .nesting(resolveTierEntries(patrols));
    }

    // The patrol tiers heaviest last, as the settings list them, so a reader comparing two
    // colonies' patrols reads them in one order.
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

    // A factor line that breaks down no further, which is every one of them bar the patrols.
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
