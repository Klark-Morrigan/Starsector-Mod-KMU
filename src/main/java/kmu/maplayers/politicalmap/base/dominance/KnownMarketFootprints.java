package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.markets.Markets;

import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds one star system's colonies into the per-faction footprints the dominance rule compares.
 *
 * <p>The selecting and folding half of the holder pipeline; what a single colony is worth is
 * {@link MarketWeights}'s. Which colonies are in the system is not its question either: it is
 * handed the system's {@link Colonies} and takes the known projection over it - the same
 * projection the ribbon counts and the cell classifies on - so a colony this weighs and a colony
 * another surface names are the one set read once rather than two walks that can part company. The
 * pure comparison of the footprints it produces is {@link SystemDominance}'s job; turning the
 * winner into draw colours is a later, separate step.
 *
 * <p>Every weight is worked out once, as a {@link MarketWeightBreakdown} the scalar
 * weight is then summed over. A caller that wants the number reads the footprint;
 * one that has to explain the number reads {@link #readBreakdownByFaction} for the
 * same markets' parts. Neither can show a total the other's arithmetic disagrees
 * with, because there is only the one arithmetic.
 *
 * <p>Beside those two sits a third read that weighs nothing:
 * {@link #readUnweighedColoniesByFaction}, the colonies present in the system that the economy
 * does not list. They reach a caller describing the system and no caller computing it, so they
 * come back named and identified and nothing more. The two sets partition the projection - every colony is either
 * economy-listed and weighed or unlisted and merely named - which is what a colony being read
 * once and sorted, rather than sought by two walks, buys: neither set can gain a colony the
 * other keeps, nor lose one both pass over.
 */
public final class KnownMarketFootprints {

    private KnownMarketFootprints() {
    }

    /**
     * Folds each faction's markets in one system into the footprint the dominance
     * rule compares.
     *
     * <p>Only the colonies the sector's economy lists are weighed. Every term of a dominance
     * weight is economy-fed - industries, conditions, computed stability - so an unlisted colony
     * has nothing for the arithmetic to read, and admitting one would hand its owner weight
     * nobody worked out. It is named instead, by {@link #readUnweighedColoniesByFaction}.
     *
     * <p>Condition-only markets (the placeholder market every uninhabited planet
     * carries for hazard and atmosphere conditions) never reach here: the colony set is
     * already selected on ownership, which is the rule that rejects them.
     *
     * @param colonies         the system's colony set, as one walk of it reported; empty yields
     *                         an empty map
     * @param rules            the dominance-weighting rules for this pass - whether stability
     *                         scales each rating and whether an attached station lifts it. The
     *                         player's LunaLib toggles, read once per pass by the caller so a
     *                         whole pass resolves under one rule
     * @param colonyVisibility what the player may be shown of a colony, read once per pass by
     *                         the caller; the fold runs over the known projection, so a colony
     *                         the rule withholds hands its owner no weight
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no folded market
     */
    public static Map<String, MarketFootprint> readByFaction(
            Colonies colonies,
            DominanceRules rules,
            ColonyVisibility colonyVisibility) {

        // The dominance-only projection of the fuller contribution read: a footprint-only caller
        // (the dominance resolve, the watcher's diff) drops the raw market size the picker's stats
        // need, so both share the one market walk and colony filter rather than defining a second.
        var footprintByFactionId = new LinkedHashMap<String, MarketFootprint>();

        for (var entry : readContributionsByFaction(
                    colonies,
                    rules,
                    colonyVisibility)
                .entrySet()) {

            footprintByFactionId.put(
                entry.getKey(),
                entry.getValue().footprint());
        }
        return footprintByFactionId;
    }

    /**
     * Folds each faction's markets in one system into its {@link FactionMarketContribution} - the
     * dominance footprint plus the raw summed colony size the picker's market-size metric reads -
     * from a single walk of the system's markets under the one "counts as a colony" filter.
     *
     * <p>The read {@link #readByFaction} projects down to when only the footprint is wanted, and the
     * picker's stats aggregation reads whole to also see raw market size. Keeping both concerns on
     * one walk means the colony filter and the weight read are defined once, not duplicated per
     * caller.
     *
     * @param colonies         the system's colony set, as one walk of it reported; empty yields
     *                         an empty map
     * @param rules            the dominance-weighting rules for this pass, read once per pass by
     *                         the caller so a whole pass resolves under one rule
     * @param colonyVisibility what the player may be shown of a colony, read once per pass by the
     *                         caller so every fold in the pass runs over the one set of colonies
     * @return each faction's contribution in the system, keyed by faction id; empty when the system
     *         holds no folded market
     */
    public static Map<String, FactionMarketContribution> readContributionsByFaction(
            Colonies colonies,
            DominanceRules rules,
            ColonyVisibility colonyVisibility) {

        var contributionByFactionId = new LinkedHashMap<String, FactionMarketContribution>();
        for (var market : readWeighedColonies(colonies, colonyVisibility)) {
            var factionId = market.getFaction().getId();
            var breakdown = MarketWeights.readBreakdown(market, rules);
            var contribution = contributionByFactionId.getOrDefault(
                factionId,
                FactionMarketContribution.EMPTY);

            // Whether the colony is on a planet is read off the breakdown rather than from the
            // market again: the tie-break here and the box that names a station colony's station
            // must not be able to disagree about what kind of place a colony is.
            contributionByFactionId.put(
                factionId,
                contribution.addMarket(
                    breakdown.computeTotalWeight(),
                    !breakdown.isStationMarket(),
                    market.getSize()));
        }
        return contributionByFactionId;
    }

    /**
     * Reads each faction's markets in one system as the arithmetic behind their dominance
     * weights, rather than as the weights alone.
     *
     * <p>The explaining half of {@link #readContributionsByFaction}: the same market walk under
     * the same "counts as a colony" filter and the same per-market weight read, kept whole
     * instead of summed away. What paints the map reads the totals; what has to justify a
     * painted system to the player reads the parts those totals are made of.
     *
     * @param colonies         the system's colony set, as one walk of it reported; empty yields
     *                         an empty map
     * @param rules            the dominance-weighting rules for this pass, read once per pass by
     *                         the caller so a whole pass resolves under one rule
     * @param colonyVisibility what the player may be shown of a colony, the same rule the totals
     *                         this explains were folded under
     * @return each faction's counted markets in the system with the breakdown of each market's
     *         weight, keyed by faction id and in the economy's own market order; empty when the
     *         system holds no counted market
     */
    public static Map<String, List<MarketWeightBreakdown>> readBreakdownByFaction(
            Colonies colonies,
            DominanceRules rules,
            ColonyVisibility colonyVisibility) {

        var breakdownsByFactionId = new LinkedHashMap<String, List<MarketWeightBreakdown>>();
        for (var market : readWeighedColonies(colonies, colonyVisibility)) {
            breakdownsByFactionId
                .computeIfAbsent(market.getFaction().getId(), factionId -> new ArrayList<>())
                .add(MarketWeights.readBreakdown(market, rules));
        }
        return breakdownsByFactionId;
    }

    /**
     * Reads each faction's colonies in one system that the economy does not list - the ones no
     * weight was ever worked out for.
     *
     * <p>Vanilla builds a colony that way on purpose, so a player can be looking at a station in a
     * faction's colours that {@link #readBreakdownByFaction} cannot see. A box accounting for the
     * system has to name it; the pass must not, every term of a dominance weight being economy-fed
     * - an unlisted market has no industries, no conditions and no computed stability, so admitting
     * one would hand its owner weight nobody worked out and could change which faction the map
     * paints the system for.
     *
     * <p>A separate read rather than a widening of the weighed one, so a caller that must not see
     * these colonies cannot be handed them by accident. Both are selected out of the one colony
     * set on the one fact that tells them apart - whether the economy lists the colony - so the
     * two are exact complements and no colony can be admitted by one and refused by the other.
     *
     * <p>Each colony comes back as an {@link UnweighedColony} rather than as a zeroed
     * {@link MarketWeightBreakdown}, and that is the guarantee the separation turns on: zero weight
     * is not absence on this side - a weightless colony still marks presence and paints its system
     * unopposed - so a value that could be summed into a footprint would leave the pass one
     * forgotten branch away from painting a system for a faction the mechanic never counted. An
     * identity and a nameplate cannot be summed into anything.
     *
     * @param colonies         the system's colony set, as one walk of it reported; empty yields
     *                         an empty map
     * @param colonyVisibility what the player may be shown of a colony, the same rule the weighed
     *                         half is selected under so the two stay exact complements
     * @return each faction's unlisted colonies in the system, identified and nothing more, keyed by
     *         faction id and in the system's own entity order; empty when every colony present is
     *         one the economy lists
     */
    public static Map<String, List<UnweighedColony>> readUnweighedColoniesByFaction(
            Colonies colonies,
            ColonyVisibility colonyVisibility) {

        var coloniesByFactionId = new LinkedHashMap<String, List<UnweighedColony>>();
        for (var market : readUnweighedColonies(colonies, colonyVisibility)) {
            coloniesByFactionId
                .computeIfAbsent(market.getFaction().getId(), factionId -> new ArrayList<>())
                .add(new UnweighedColony(market.getId(), Markets.readNameplate(market)));
        }
        return coloniesByFactionId;
    }

    /**
     * The colonies in one system this class weighs: the ones the economy lists, in the order it
     * lists them, under the known projection.
     *
     * <p>Named here rather than left implicit in each read because it is the whole of what
     * "dominance is economy-fed" amounts to, and it is asked from outside the weighing too - a
     * reader ordering the blocs this class ranked has to be ranking them over the same colonies,
     * or it can settle a contest among markets the weights were never folded from.
     *
     * @param colonies         the system's colony set, as one walk of it reported; empty yields
     *                         an empty list
     * @param colonyVisibility what the player may be shown of a colony, so a reader ordering the
     *                         blocs this class ranked reads the very colonies they were ranked on
     * @return the weighed colonies' markets, in the economy's own order
     */
    public static List<MarketAPI> readWeighedColonies(
            Colonies colonies,
            ColonyVisibility colonyVisibility) {

        return selectMarkets(colonies, colonyVisibility, true);
    }

    // The colonies present that the economy does not list - what the weighed read passes over, and
    // nothing it takes, the two dividing the projection between them.
    private static List<MarketAPI> readUnweighedColonies(
            Colonies colonies,
            ColonyVisibility colonyVisibility) {

        return selectMarkets(colonies, colonyVisibility, false);
    }

    // One side of the known projection's one division, the flag naming which. Written once with
    // the side as a parameter rather than twice with the test negated, so the two sides cannot
    // drift into overlapping or into leaving a colony out between them.
    //
    // The projection is taken here rather than by the caller because the rule is per-read: the
    // same system is read under the player's own rule for the map and under a widened one for a
    // box, and a projection cached across the pair would answer one of them wrongly.
    private static List<MarketAPI> selectMarkets(
            Colonies colonies,
            ColonyVisibility colonyVisibility,
            boolean shouldSelectListedByEconomy) {

        var markets = new ArrayList<MarketAPI>();

        for (var colony : colonies.readKnownColonies(colonyVisibility)) {
            if (colony.isListedByEconomy() == shouldSelectListedByEconomy) {
                markets.add(colony.market());
            }
        }
        return markets;
    }
}
