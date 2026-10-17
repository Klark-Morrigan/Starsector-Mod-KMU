package kmu.maplayers.base.visibility.colonies;

import kmlib.starsector.markets.MarketVisibility;
import kmlib.starsector.markets.colonies.Colonies;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a box may say about each of one location's colonies, asked by the colony's own ID: what kind
 * of place it is, whether the player has found it, and whether a concealed one is concealed in name
 * only.
 *
 * <p>For a reader that meets a colony by identity rather than as a colony - an account built from
 * a claim contest, where each row carries the ID of the market it was scored from and nothing of
 * the colony behind it. Such a reader cannot ask a colony set anything directly, and pairing a row
 * to a colony by display name would pair the wrong ones: vanilla names a station colony and its
 * defending station alike.
 *
 * <p>Folded once for a whole box, in one walk, rather than resolved per row. A box lists a system's
 * colonies several times over - once per faction standing - and a lookup taken per row would walk
 * the set again for each.
 *
 * <p>By ID rather than by identity, unlike the memo inside {@link ColonyKnowledge}: the whole point
 * is to answer a reader that holds an ID and no colony. Two markets sharing an ID would collide,
 * which the sector does not produce - an ID is what the game keys a market by.
 *
 * <p>Every colony present is folded, not merely the ones a projection admits. A reader asking has
 * already decided which rows it lists, and narrowing here would leave a listed row unanswered for
 * reasons the caller had nothing to do with.
 *
 * <p>An ID the fold never met answers in the direction that states no finding: the ordinary colony,
 * found, and a real secret. A box cannot call a colony a wreck, undiscovered or excused on the
 * strength of a fold nobody made.
 *
 * @param kindByColonyId        each colony's kind, keyed by its market ID
 * @param undiscoveredColonyIds the market IDs of the colonies whose entity the player has yet to
 *                              discover
 * @param openlyKnownColonyIds  the market IDs of the concealed colonies the sector openly points at
 */
public record ColonyFactsLookup(
    Map<String, ColonyKind> kindByColonyId,
    Set<String> undiscoveredColonyIds,
    Set<String> openlyKnownColonyIds) {

    /** Nothing walked - what a reader with no colony set behind it answers through. */
    public static final ColonyFactsLookup NONE = new ColonyFactsLookup(Map.of(), Set.of(), Set.of());

    /** Takes immutable copies, and reads an absent map or set as an empty one. */
    public ColonyFactsLookup {

        kindByColonyId = kindByColonyId == null
            ? Map.of()
            : Map.copyOf(kindByColonyId);
        undiscoveredColonyIds = undiscoveredColonyIds == null
            ? Set.of()
            : Set.copyOf(undiscoveredColonyIds);
        openlyKnownColonyIds = openlyKnownColonyIds == null
            ? Set.of()
            : Set.copyOf(openlyKnownColonyIds);
    }

    /**
     * Folds one location's colonies into what a box may say about each.
     *
     * <p>The kind is classified under the pass's own rule, so the kinds stated are the very kinds the
     * map is drawn under. Discovery is read off the market's own entity
     * ({@link MarketVisibility#isDiscoveredByPlayer}) rather than off any projection: a colony
     * withheld by a revelation gate has been discovered perfectly well, and one shown by a reveal
     * has not.
     *
     * @param colonies  the location's colonies, as one walk of it reported; null yields
     *                  {@link #NONE}
     * @param knowledge the pass's own classification; null leaves every kind unstated, which a
     *                  reader with no pass behind it has nothing to classify by
     * @return what may be said of those colonies, by colony ID
     */
    public static ColonyFactsLookup readFactsIn(Colonies colonies, ColonyKnowledge knowledge) {

        if (colonies == null) {
            return NONE;
        }

        var kindByColonyId = new HashMap<String, ColonyKind>();
        var undiscoveredColonyIds = new HashSet<String>();
        var openlyKnownColonyIds = new HashSet<String>();

        for (var colony : colonies.colonies()) {

            var colonyId = colony.market().getId();
            if (colonyId == null) {
                continue;
            }

            if (knowledge != null) {
                kindByColonyId.put(colonyId, knowledge.readKindOf(colony));
            }

            if (!MarketVisibility.isDiscoveredByPlayer(colony.market())) {
                undiscoveredColonyIds.add(colonyId);
            }

            // The concealment is asked first, so the registry is consulted for the handful of
            // colonies per sector a finding could ever be made about rather than for every place in
            // it. A concealment that was never claimed cannot be excused.
            if (colony.isHidden()
                    && OpenlyKnownColonyRegistry.isOpenlyKnownEntity(colony.market().getPrimaryEntity())) {

                openlyKnownColonyIds.add(colonyId);
            }
        }
        return new ColonyFactsLookup(kindByColonyId, undiscoveredColonyIds, openlyKnownColonyIds);
    }

    /**
     * What kind of place the colony with this ID is.
     *
     * @param colonyId the colony's market ID; an ID the lookup has never held reads as the
     *                 ordinary colony, which is the direction a classification errs in everywhere
     *                 else - overstating a place by one settlement rather than erasing one
     * @return that colony's kind
     */
    public ColonyKind readKindOf(String colonyId) {
        return kindByColonyId.getOrDefault(colonyId, ColonyKind.COLONY);
    }

    /**
     * Whether the player has found the entity the colony with this ID sits on.
     *
     * @param colonyId the colony's market ID; an ID the fold never met reads as found
     * @return true when the colony's entity has been found
     */
    public boolean isDiscoveredColony(String colonyId) {
        return !undiscoveredColonyIds.contains(colonyId);
    }

    /**
     * Whether the colony with this ID conceals itself only in the sector's bookkeeping.
     *
     * <p>Answers what a box may say about a colony and nothing about what it may show, for the reason
     * {@link OpenlyKnownColonyRegistry} states.
     *
     * @param colonyId the colony's market ID; an ID the fold never met, or none, reads as a secret
     * @return true when the colony is one the sector openly points at
     */
    public boolean isOpenlyKnownColony(String colonyId) {

        return colonyId != null
            && openlyKnownColonyIds.contains(colonyId);
    }
}
