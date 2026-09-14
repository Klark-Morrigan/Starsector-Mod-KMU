package kmu.maplayers.base.visibility.colonies;

import kmlib.starsector.markets.MarketVisibility;
import kmlib.starsector.markets.colonies.Colonies;

import java.util.HashSet;
import java.util.Set;

/**
 * Which of one location's colonies the player has yet to discover, asked by the colony's own id.
 *
 * <p>For the reader {@link ColonyKindLookup} serves and folded on the same walk: an account built
 * from a contest meets a colony as an ID beside a number, so a fact about the colony itself has to
 * be read from the system and matched back. Discovery is such a fact - it lives on the entity,
 * which no row carries.
 *
 * <p>The undiscovered ones rather than the discovered ones, so an ID the fold never met reads as
 * discovered. The answer is spent on calling a colony out as undiscovered, and a finding stated
 * about a colony nobody folded would be a claim the box has nothing behind.
 *
 * <p>Every colony present, not merely the ones a projection admits, on the same reasoning the kinds
 * are: a reader asking this has already decided which rows it lists, and narrowing here would leave
 * a listed row unanswered for reasons the caller had nothing to do with. A colony nobody has
 * discovered is on the list wherever the box that built it said so, and says as much.
 *
 * @param undiscoveredColonyIds the market IDs of the colonies whose entity the player has yet to
 *                              discover
 */
public record ColonyDiscoveryLookup(Set<String> undiscoveredColonyIds) {

    /** Nothing walked - what a reader with no colony set behind it answers through. */
    public static final ColonyDiscoveryLookup NONE = new ColonyDiscoveryLookup(Set.of());

    /** Takes an immutable copy, and reads an absent set as an empty one. */
    public ColonyDiscoveryLookup {
        undiscoveredColonyIds = undiscoveredColonyIds == null
            ? Set.of()
            : Set.copyOf(undiscoveredColonyIds);
    }

    /**
     * Folds one location's colonies into the ones the player has yet to discover.
     *
     * <p>Read off the market's own entity ({@link MarketVisibility#isDiscoveredByPlayer}) rather
     * than off any projection, because what is wanted is the entity's flag alone: a colony withheld
     * by a revelation gate has been discovered perfectly well, and one shown by a reveal has not.
     *
     * @param colonies the location's colonies, as one walk of it reported; null yields {@link #NONE}
     * @return the undiscovered ones among them, by colony ID
     */
    public static ColonyDiscoveryLookup readDiscoveriesIn(Colonies colonies) {

        if (colonies == null) {
            return NONE;
        }
        var undiscoveredColonyIds = new HashSet<String>();

        for (var colony : colonies.colonies()) {

            var colonyId = colony.market().getId();

            if (colonyId != null && !MarketVisibility.isDiscoveredByPlayer(colony.market())) {
                undiscoveredColonyIds.add(colonyId);
            }
        }
        return new ColonyDiscoveryLookup(undiscoveredColonyIds);
    }

    /**
     * Whether the player has found the entity the colony with this ID sits on.
     *
     * @param colonyId the colony's market ID; an ID the fold never met reads as found, a finding
     *                 about a colony nothing walked being one the box cannot support
     * @return true when the colony's entity has been found
     */
    public boolean isDiscoveredColony(String colonyId) {
        return !undiscoveredColonyIds.contains(colonyId);
    }
}
