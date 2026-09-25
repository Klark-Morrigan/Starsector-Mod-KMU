package kmu.maplayers.politicalmap.dominance.tooltip;

import kmlib.starsector.entities.EntityNameplate;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.ownermap.tooltip.ColonyQualifier;
import kmu.maplayers.ownermap.tooltip.SystemColonyReading;
import kmu.maplayers.ownermap.tooltip.TermTooltipLine;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolFactor;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolTierFactor;
import kmu.maplayers.politicalmap.dominance.weighting.StationFactor;
import kmu.maplayers.politicalmap.dominance.weighting.UnweighedColony;
import kmu.settings.HiddenMarketScalingChoice;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the arithmetic behind a faction's dominance score into the entries a block lists it as:
 * each of its colonies, beneath each the factors its weight was summed from, and at the foot the
 * colonies the pass never weighed.
 *
 * <p>What turns the numbers the map painted by into an account a player can follow. The score a
 * faction holds a system with is a sum of sums, and the only useful way to read a sum is to see it
 * broken apart - so a colony is listed under the faction holding it, a factor under the colony it
 * moved, and a patrol tier under the patrol factor it is part of. Depth is the subject matter's here
 * rather than the entry model's: the walk that lays these out reads the tier off how deep it went.
 *
 * <p>Pure over a breakdown and a rule with no Starsector types, so a whole box's worth of lines is
 * resolved without a live economy. How one line's numbers read is {@link MarketFactorText}'s; what a
 * colony's line calls out beyond its weight is {@link ColonyQualifier}'s.
 */
public final class MarketWeightRowResolver {

    // Colonies rank descending by the weight they folded in at, a tie falling to the lowest name so
    // the order is total and never depends on the economy walk order the breakdowns arrive in - the
    // same rule the standings above them are ranked by.
    private static final Comparator<MarketWeightBreakdown> MARKET_ORDER =
        Comparator
            .comparingInt(MarketWeightBreakdown::computeTotalWeight)
            .reversed()
            .thenComparing(breakdown -> breakdown.marketNameplate().displayName());

    // A colony the pass never weighed is ranked by name alone, having no weight to be ranked by -
    // which is the rule the weighed colonies fall back on at a tie, so one order runs down the
    // whole list rather than two.
    private static final Comparator<UnweighedColony> UNWEIGHED_ORDER =
        Comparator.comparing(colony -> colony.nameplate().displayName());

    // What a colony the pass never weighed folded in at. Nought rather than a blank column, because
    // the colony is on the list and the reader is being told what it counted for.
    private static final int NO_WEIGHT = 0;

    // A tier nobody fields is not listed. Its line would state patrols the colony does not have,
    // and the reader is looking for what the weight is made of, not what it is not.
    private static final int NO_PATROLS = 0;

    // Where the economy stands on a colony that reached one list or the other. The weight read
    // walks the economy's own set, so everything it weighed is listed by construction and
    // everything on the unweighed list is there precisely because it is not.
    private static final boolean IS_LISTED_BY_ECONOMY = true;
    private static final boolean IS_NOT_LISTED_BY_ECONOMY = false;

    // What this box leads a colony's line with, which is nothing: taking a system is the claim
    // contest's finding, and this box is an account of a different one.
    private static final String NO_LEADING_FINDING = null;

    private MarketWeightRowResolver() {
    }

    /**
     * Resolves the colonies behind one faction's score into the entries listed beneath it, strongest
     * first, each carrying the factors its own weight was summed from - closed by the colonies the
     * pass never weighed, which are named at nought and break down into nothing.
     *
     * @param breakdowns        the faction's counted colonies in the hovered system, in any order
     * @param unweighedColonies the faction's colonies in the system that the economy does not list,
     *                          identified and nothing more, no weight having been worked out for
     *                          them
     * @param reading           what the box knows about the hovered system beside these colonies -
     *                          the weighting rule, the colony reading and how deep to go. The
     *                          colonies themselves are always listed, this resolver being consulted
     *                          at all only where they are
     * @return one entry per colony, the weighed ones ranked ahead of the unweighed; empty when the
     *         faction holds no colony at all in the system
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            List<MarketWeightBreakdown> breakdowns,
            List<UnweighedColony> unweighedColonies,
            WeightAccountReading reading) {

        var entries = new ArrayList<CellTooltipEntry>();

        breakdowns
            .stream()
            .sorted(MARKET_ORDER)
            .forEach(breakdown -> entries.add(resolveMarketEntry(breakdown, reading)));

        // Last whatever they would rank at, because they never ranked: sorted in among the weighed
        // colonies by a nought they were never given, they would sit above a colony that was
        // weighed and came to nothing, which is a comparison neither number can bear.
        unweighedColonies
            .stream()
            .sorted(UNWEIGHED_ORDER)
            .forEach(colony -> entries.add(
                resolveUnweighedEntry(colony, reading.colonyReading())));

        return List.copyOf(entries);
    }

    // One colony as the entry it is listed as: its name and the weight it folded in at, over the
    // factors that weight is the sum of.
    //
    // Its line says whatever the box has found out about the place that its weight does not. A
    // concealed colony is called out here rather than on the size term its concealment moved, that
    // being a fact about the place and not about one factor of the sum.
    //
    // The kind comes off the box's walk of the system rather than off the breakdown, the weight
    // read having no reason to carry one: everything it weighs is a place somebody keeps. It is
    // asked all the same, so what a line may call out is decided in one place for both lists.
    private static CellTooltipEntry resolveMarketEntry(
            MarketWeightBreakdown breakdown,
            WeightAccountReading reading) {

        var colonyReading = reading.colonyReading();
        var line = createCountedMapEntityLine(
            breakdown.marketNameplate(),
            breakdown.marketNameplate().displayName(),
            breakdown.computeTotalWeight());

        return CellTooltipEntry
            .createEntry(colonyReading.describeColony(
                line,
                breakdown.marketId(),
                colonyReading.readQualifierFacts(
                    breakdown.marketId(),
                    breakdown.isHiddenMarket(),
                    IS_LISTED_BY_ECONOMY,
                    NO_LEADING_FINDING)))
            .nesting(resolveFactorEntries(breakdown, reading));
    }

    // A colony the pass never weighed, as the entry it is listed as: named as loudly as the colonies
    // above it, at nought, and breaking down into no factors - none of them ran, so there is nothing
    // beneath it to state. Listed at all because the player can see the station on the map in a
    // faction's colours, and an account omitting it would withhold what they are looking straight
    // at. A collapsed colony and a derelict both reach this list unowned and at nought, so the words
    // after the name are all that tells them apart.
    //
    // The nought says the whole of it, exactly as the claims box's does. It is the account's
    // statement about the colony rather than anything the colony scored, so it reads in the quiet
    // shade: in the list's own colour it would pass for a weight competed with and lost on, which is
    // the one thing it is not.
    private static CellTooltipEntry resolveUnweighedEntry(
            UnweighedColony colony,
            SystemColonyReading colonyReading) {

        var line = createCountedMapEntityLine(
                colony.nameplate(),
                colony.nameplate().displayName(),
                NO_WEIGHT)
            .statesUncountedValue();

        // The kind and the concealment are read off the colony itself rather than off the walk
        // beside it, both having travelled here from the very selection that met the colony - so
        // the line's findings can only ever be about the colony it names.
        return CellTooltipEntry.createEntry(colonyReading.describeColony(
            line,
            colony.marketId(),
            colonyReading.readQualifierFacts(
                colony.marketId(),
                colony.kind(),
                colony.isHiddenMarket(),
                IS_NOT_LISTED_BY_ECONOMY,
                NO_LEADING_FINDING)));
    }

    // A line naming something the sector map draws - a colony or the station defending it - led by
    // the glyph the map marks it with, and carrying whatever the account counted it for.
    //
    // Shared by the two levels that take a mark rather than spelled out at each, so the box arrives
    // at one rule for a line about a thing on the map instead of one rule per level.
    //
    // Takes the subject's nameplate whole rather than its two halves, so the glyph can only have come
    // from the entity the line is about - and read off the breakdown that entity arrived in rather
    // than looked up here, so that entity is the one the number belongs to.
    //
    // The stated name is passed beside it because one of the three lines says more than the entity's
    // bare name, and it is derived from this very nameplate at each call site, so the pair still
    // cannot come from two different entities.
    private static CellTooltipEntryLine createMapEntityLine(
            EntityNameplate entity,
            String statedName,
            String valueText) {

        return CellTooltipEntryLine.createLine(
            resolveMapEntityMark(entity),
            statedName,
            valueText);
    }

    // The same line where what it carries is a weight the account adds up - a colony's, or the nought
    // of one nothing was worked out for. The number travels rather than words for it, so a listing of
    // colonies too long to draw whole can be closed by a row summing the ones it left out.
    private static CellTooltipEntryLine createCountedMapEntityLine(
            EntityNameplate entity,
            String statedName,
            int countedValue) {

        return CellTooltipEntryLine.createCountedLine(
            resolveMapEntityMark(entity),
            statedName,
            countedValue);
    }

    // The glyph the map marks an entity with, read off the entity the line is about. Shared by both
    // shapes of line so a colony and a station are marked alike whether or not the number beside them
    // is one the account sums. Only those two levels take one: a stability or a size has nothing on
    // the map to point at, so a glyph there would stand in for a number.
    private static CellTooltipMark resolveMapEntityMark(EntityNameplate entity) {
        return CellTooltipMark.resolveMarkForMapIcon(entity.mapIcon());
    }

    // The factors of one colony, in the order the weight read applied them - the stability that
    // decides every cut first, so the deductions below it read as consequences of a stated cause
    // rather than as three unexplained subtractions.
    //
    // Nothing at all where the level stops at the colonies. Every line here is a number worded for
    // the reader, and a colony's factors run to four of them, so a system's worth of them composed
    // and then cut is the whole of what the composition level would have paid the deepest level's
    // price for.
    //
    // A factor that did not run has no line. The breakdown read already leaves out the station and
    // patrol parts the player has switched off, so only the stability line - a cause rather than a
    // factor - is gated here, and a player is never shown a line insisting a factor counted for
    // nothing.
    private static List<CellTooltipEntry> resolveFactorEntries(
            MarketWeightBreakdown breakdown,
            WeightAccountReading reading) {

        if (!reading.detailLevel().isReadingAtLeast(HoverTooltipDetailLevel.MARKET_STATS)) {
            return List.of();
        }
        var rules = reading.rules();
        var entries = new ArrayList<CellTooltipEntry>();

        // Stability is stated only where it can cost the colony something: with the master
        // weighting off it moves no factor, and a line for it would read as a cause of cuts that
        // are all zero.
        if (rules.isStabilityWeighted()) {
            entries.add(TermTooltipLine.buildTermEntry(
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_STABILITY),
                MarketFactorText.formatStability(breakdown.marketStability())));
        }
        entries.add(resolveBaseSizeEntry(breakdown, rules));

        // The station line names the station rather than the factor, so the number ties to
        // something the player can find on the map - and leads with that station's own glyph for the
        // same reason the colony line above it does, this being the only line in the breakdown whose
        // subject the map draws.
        breakdown.station().ifPresent(station -> entries.add(CellTooltipEntry.createEntry(
            createMapEntityLine(
                station.stationNameplate(),
                resolveStationName(breakdown, station),
                MarketFactorText.formatStation(station)))));

        breakdown.patrols().ifPresent(patrols ->
            entries.add(resolvePatrolEntry(patrols, reading.detailLevel())));

        return entries;
    }

    // How the station line names its station: its own name, or that name told apart from the colony
    // line above it where the two would otherwise read as one name printed twice.
    //
    // Both conditions have to hold. A colony on a station is one entity to the player and two to the
    // economy - the colony and the military station defending it - and vanilla names them alike, so
    // the account states the same words at two levels for two different things. A planet colony that
    // happens to share its station's name is a different case: the two are visibly separate places on
    // the map, so a clarifier there would be answering a question the reader never had.
    private static String resolveStationName(
            MarketWeightBreakdown breakdown,
            StationFactor station) {

        var stationName = station.stationNameplate().displayName();

        if (breakdown.isStationMarket()
                && stationName.equals(breakdown.marketNameplate().displayName())) {
            return MarketFactorText.formatMilitaryStationName(stationName);
        }
        return stationName;
    }

    // The base-size factor's line. Concealment is not stated here although it is the reason two of
    // the factors rate the colony as they do: it is a finding about the colony rather than about
    // this term, and the colony's own line above calls it out for both boxes at once.
    //
    // Where the fixed token replaced the colony's own size, the line opens on that real size in the
    // quiet shade. Without it the value states a size the colony does not have and nothing to read
    // it against, which is the one case a player is most likely to take for a bug in the map.
    private static CellTooltipEntry resolveBaseSizeEntry(
            MarketWeightBreakdown breakdown,
            DominanceRules rules) {

        var isFixedRating = isFixedRating(breakdown, rules);
        var line = TermTooltipLine.buildTermLine(
            KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE),
            MarketFactorText.formatBaseSize(breakdown.baseSize(), isFixedRating));

        if (isFixedRating) {
            line = line.derivesValueFrom(
                MarketFactorText.formatRawSizeWorking(breakdown.baseSize().rawMarketSize()));
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
    private static CellTooltipEntry resolvePatrolEntry(
            PatrolFactor patrols,
            HoverTooltipDetailLevel detailLevel) {

        return CellTooltipEntry
            .createEntry(TermTooltipLine.buildTermLine(
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS),
                MarketFactorText.formatPatrols(patrols)))
            .nesting(resolveTierEntries(patrols, detailLevel));
    }

    // The patrol tiers heaviest last, as the settings list them, so a reader comparing two
    // colonies' patrols reads them in one order.
    //
    // Nothing at all short of the deepest level: the split is the one tier below the factors, so
    // the level that shows a colony's stats is the last one that has no use for it.
    private static List<CellTooltipEntry> resolveTierEntries(
            PatrolFactor patrols,
            HoverTooltipDetailLevel detailLevel) {

        if (!detailLevel.isReadingAtLeast(HoverTooltipDetailLevel.PATROL_DETAILS)) {
            return List.of();
        }
        var entries = new ArrayList<CellTooltipEntry>();

        appendTierEntry(entries, KmuStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_SMALL, patrols.small());
        appendTierEntry(entries, KmuStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_MEDIUM, patrols.medium());
        appendTierEntry(entries, KmuStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_LARGE, patrols.large());

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
        var line = TermTooltipLine.buildTermLine(
                KmuStringKeys.format(
                    KmuStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_TIER,
                    KmuStringKeys.get(tierNameKey),
                    tier.count()),
                MarketFactorText.formatPatrolTierTotal(tier))
            .derivesValueFrom(MarketFactorText.formatPatrolTierWorking(tier));

        entries.add(CellTooltipEntry.createEntry(line));
    }
}
