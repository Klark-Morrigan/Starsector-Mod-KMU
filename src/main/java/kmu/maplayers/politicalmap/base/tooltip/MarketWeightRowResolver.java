package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.colonies.ColonyKind;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.StationFactor;
import kmu.maplayers.politicalmap.base.dominance.UnweighedColony;
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
 * so it identifies the line without competing with the numbers the box exists to state.
 *
 * <p>The station line takes one on the same terms, being the one line beneath a colony named for a
 * thing on the map rather than for a term of arithmetic - and the mark settles more there than it does
 * a level up, a system's stations being told apart on the map by their glyph as much as by their name.
 * Every other line beneath a colony carries no mark at all: a stability or a size has nothing on the
 * map to point at, so a glyph there would stand in for a number.
 *
 * <p>Where that station shares its colony's name - which only a colony on a station can - the line
 * says which of the two it is about. The economy holds a station colony as two entities vanilla
 * names alike, so the account would otherwise print one name at two levels and leave the reader to
 * work out that the second is not the first repeated.
 *
 * <p>A colony the pass never weighed is listed all the same, at the foot of the list and at nought.
 * The player can see the station on the map in a faction's colours, so an account of the system that
 * omitted it would be withholding something they are looking straight at - and the nought is the
 * whole of what the account has to say about it: it is there, and it moved nothing.
 *
 * <p>Where such a colony is a world people left rather than a place somebody keeps, the line says
 * so ({@link ColonyKindQualifier}). A ruin and a derelict hulk reach this list identically - both
 * unowned, both off-economy, both at nought - and nothing else on either line would tell them
 * apart.
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
            .thenComparing(breakdown -> breakdown.marketNameplate().displayName());

    // A colony the pass never weighed is ranked by name alone, having no weight to be ranked by -
    // which is the rule the weighed colonies fall back on at a tie, so one order runs down the
    // whole list rather than two.
    private static final Comparator<UnweighedColony> UNWEIGHED_ORDER =
        Comparator.comparing(colony -> colony.nameplate().displayName());

    // A term of arithmetic is named rather than marked: a stability, a size or a patrol tier has
    // nothing on the map to point at, so a glyph there would stand in for a number. The two lines that
    // do lead with one - a colony's and its station's - name things the player can go and find.
    private static final CellTooltipMark NO_MARK = null;

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
     *                          identified and nothing more, no weight having been worked out for
     *                          them
     * @param rules             the weighting rules the pass resolved under, which decide whether
     *                          stability is a cause worth stating
     * @param notes             how old the box's news of each colony is, so a colony nobody is
     *                          looking at says when it was last seen
     * @return one entry per colony, the weighed ones ranked ahead of the unweighed; empty when the
     *         faction holds no colony at all in the system
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            List<MarketWeightBreakdown> breakdowns,
            List<UnweighedColony> unweighedColonies,
            DominanceRules rules,
            ColonyObservationNotes notes) {

        var entries = new ArrayList<CellTooltipEntry>();

        breakdowns
            .stream()
            .sorted(MARKET_ORDER)
            .forEach(breakdown -> entries.add(resolveMarketEntry(breakdown, rules, notes)));

        // Last whatever they would rank at, because they never ranked: sorted in among the weighed
        // colonies by a nought they were never given, they would sit above a colony that was
        // weighed and came to nothing, which is a comparison neither number can bear.
        unweighedColonies
            .stream()
            .sorted(UNWEIGHED_ORDER)
            .forEach(colony -> entries.add(resolveUnweighedEntry(colony, notes)));

        return List.copyOf(entries);
    }

    // One colony as the entry it is listed as: its name and the weight it folded in at, over the
    // factors that weight is the sum of.
    private static CellTooltipEntry resolveMarketEntry(
            MarketWeightBreakdown breakdown,
            DominanceRules rules,
            ColonyObservationNotes notes) {

        return CellTooltipEntry
            .createEntry(remarkOnColony(
                createMapEntityLine(
                    breakdown.marketNameplate(),
                    breakdown.marketNameplate().displayName(),
                    KmlibNumbers.formatGroupedInteger(breakdown.computeTotalWeight())),
                breakdown.marketId(),
                notes))
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
    private static CellTooltipEntry resolveUnweighedEntry(
            UnweighedColony colony,
            ColonyObservationNotes notes) {

        // The kind is called out here and on no weighed line above, and the absence is the
        // subject matter rather than an omission: a dead world is off-economy by construction, so
        // the pass can never have weighed one, and the kinds that are weighed are already told
        // apart by the numbers beneath them.
        return CellTooltipEntry.createEntry(qualifyByKind(
            remarkOnColony(
                createMapEntityLine(
                        colony.nameplate(),
                        colony.nameplate().displayName(),
                        KmlibNumbers.formatGroupedInteger(NO_WEIGHT))
                    .statesUncountedValue(),
                colony.marketId(),
                notes),
            colony.kind()));
    }

    // Runs a colony's line on into what its kind calls out, where the kind states anything.
    //
    // Layered after the remark rather than before it because the two are stated in different
    // shades and at different ends of the line - the remark quiet, run on after the name; the
    // qualifier a finding, at the end - so neither can displace the other.
    private static CellTooltipEntryLine qualifyByKind(
            CellTooltipEntryLine line,
            ColonyKind kind) {

        return ColonyKindQualifier
            .resolveKindQualifier(kind)
            .map(line::qualifiedWith)
            .orElse(line);
    }

    // Runs a colony's line on into when it was last seen, where nobody is looking at it now.
    //
    // Applied to both kinds of colony line through one helper, because how current the box's news
    // of a colony is has nothing to do with whether the economy lists it - and the derelict that
    // most needs the remark is exactly the kind no weight was worked out for.
    //
    // Nothing beneath a colony takes one: a stability or a patrol tier is arithmetic over the
    // colony's own line, so a date there would be answering for the line above it twice.
    private static CellTooltipEntryLine remarkOnColony(
            CellTooltipEntryLine line,
            String marketId,
            ColonyObservationNotes notes) {

        return notes
            .resolveLastSeenNote(marketId)
            .map(line::notedWith)
            .orElse(line);
    }

    // A line naming something the sector map draws - a colony or the station defending it - led by
    // the glyph the map marks it with, and carrying whatever the account counted it for.
    //
    // The icon is what ties a name in this list back to something the player is looking at. A name
    // alone does that only for a reader who already remembers it, while the glyph is the one thing
    // the box and the map can share at a glance. How it is coloured is the mark's own rule.
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
            CellTooltipMark.resolveMarkForMapIcon(entity.mapIcon()),
            statedName,
            valueText);
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
        // something the player can find on the map - and leads with that station's own glyph for the
        // same reason the colony line above it does, this being the only line in the breakdown whose
        // subject the map draws.
        breakdown.station().ifPresent(station -> entries.add(CellTooltipEntry.createEntry(
            createMapEntityLine(
                station.stationNameplate(),
                resolveStationName(breakdown, station),
                MarketFactorText.formatStation(station)))));

        breakdown.patrols().ifPresent(patrols -> entries.add(resolvePatrolEntry(patrols)));
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
                NO_MARK,
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
        return CellTooltipEntryLine.createLine(NO_MARK, labelText, valueText);
    }
}
