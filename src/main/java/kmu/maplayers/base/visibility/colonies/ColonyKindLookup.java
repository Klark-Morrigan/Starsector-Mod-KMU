package kmu.maplayers.base.visibility.colonies;

import kmlib.starsector.markets.colonies.Colonies;

import java.util.HashMap;
import java.util.Map;

/**
 * What kind of place each of one location's colonies is, asked by the colony's own id.
 *
 * <p>For a reader that meets a colony by identity rather than as a colony - an account built from
 * a claim contest, where each row carries the ID of the market it was scored from and nothing of
 * the colony behind it. Such a reader cannot ask a colony set anything directly, and pairing a row
 * to a colony by display name would pair the wrong ones: vanilla names a station colony and its
 * defending station alike.
 *
 * <p>Folded once for a whole box rather than resolved per row. A box lists a system's colonies
 * several times over - once per faction standing - and a lookup taken per row would walk the set
 * again for each, while a kind resolved per row would be that many independent statements of what
 * a derelict is.
 *
 * <p>By ID rather than by identity, unlike the memo inside {@link ColonyKnowledge}: the whole point
 * is to answer a reader that holds an ID and no colony. Two markets sharing an ID would collide,
 * which the sector does not produce - an ID is what the game keys a market by.
 *
 * @param kindByColonyId each colony's kind, keyed by its market ID
 */
public record ColonyKindLookup(Map<String, ColonyKind> kindByColonyId) {

    /** Nothing known of anywhere - what a reader with no colony walk behind it answers through. */
    public static final ColonyKindLookup NONE = new ColonyKindLookup(Map.of());

    /** Takes an immutable copy, and reads an absent map as an empty one. */
    public ColonyKindLookup {
        kindByColonyId = kindByColonyId == null ? Map.of() : Map.copyOf(kindByColonyId);
    }

    /**
     * Folds one location's colonies into their kinds.
     *
     * <p>Every colony present, not merely the ones a projection admits. What the lookup answers is
     * what a place <em>is</em>, and a reader asking it has already decided which rows it is
     * listing - narrowing here would leave a listed row unanswered for reasons the caller had
     * nothing to do with.
     *
     * @param colonies  the location's colonies, as one walk of it reported; null yields
     *                  {@link #NONE}
     * @param knowledge the pass's own classification, so the kinds here are the very kinds the map
     *                  is drawn under; null yields {@link #NONE}
     * @return the kinds of those colonies, by colony ID
     */
    public static ColonyKindLookup readKindsIn(Colonies colonies, ColonyKnowledge knowledge) {

        if (colonies == null || knowledge == null) {
            return NONE;
        }
        var kindByColonyId = new HashMap<String, ColonyKind>();

        for (var colony : colonies.colonies()) {

            var colonyId = colony.market().getId();

            if (colonyId != null) {
                kindByColonyId.put(colonyId, knowledge.readKindOf(colony));
            }
        }
        return new ColonyKindLookup(kindByColonyId);
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
}
