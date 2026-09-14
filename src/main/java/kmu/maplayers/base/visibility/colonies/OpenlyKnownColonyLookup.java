package kmu.maplayers.base.visibility.colonies;

import kmlib.starsector.markets.colonies.Colonies;

import java.util.HashSet;
import java.util.Set;

/**
 * Which of one location's concealed colonies are concealed in name only, asked by the colony's own
 * id.
 *
 * <p>For the reader {@link ColonyKindLookup} serves and folded on the same walk: an account built
 * from a contest meets a colony as an ID beside a number, and whether the place behind it is a
 * landmark lives on its entity - which no row carries.
 *
 * <p>Answers what a box may say about a colony and nothing about what it may show, for the reason
 * {@link OpenlyKnownColonyRegistry} states.
 *
 * @param openlyKnownColonyIds the market IDs of the concealed colonies the sector openly points at
 */
public record OpenlyKnownColonyLookup(
    Set<String> openlyKnownColonyIds) {

    /** Nothing walked - what a reader with no colony set behind it answers through. */
    public static final OpenlyKnownColonyLookup NONE = new OpenlyKnownColonyLookup(Set.of());

    /** Takes an immutable copy, and reads an absent set as an empty one. */
    public OpenlyKnownColonyLookup {

        openlyKnownColonyIds = openlyKnownColonyIds == null
            ? Set.of()
            : Set.copyOf(openlyKnownColonyIds);
    }

    /**
     * Folds one location's colonies into the concealed ones whose concealment is public knowledge.
     *
     * <p>Every colony present, not merely the ones a projection admits, on the same reasoning the
     * kinds are: a reader asking this has already decided which rows it lists, and narrowing here
     * would leave a listed row unanswered for reasons the caller had nothing to do with.
     *
     * @param colonies the location's colonies, as one walk of it reported; null yields {@link #NONE}
     * @return the openly known ones among them, by colony ID
     */
    public static OpenlyKnownColonyLookup readOpenlyKnownIn(Colonies colonies) {

        if (colonies == null) {
            return NONE;
        }
        var openlyKnownColonyIds = new HashSet<String>();

        for (var colony : colonies.colonies()) {

            var colonyId = colony.market().getId();

            // The concealment is asked first, so the registry is consulted for the handful of
            // colonies per sector a finding could ever be made about rather than for every place in
            // it. A concealment that was never claimed cannot be excused, so every other colony is
            // a walk step and no more.
            if (colonyId != null
                    && colony.isHidden()
                    && OpenlyKnownColonyRegistry.isOpenlyKnownEntity(
                        colony.market().getPrimaryEntity())) {

                openlyKnownColonyIds.add(colonyId);
            }
        }
        return new OpenlyKnownColonyLookup(openlyKnownColonyIds);
    }

    /**
     * Whether the colony with this ID conceals itself only in the sector's bookkeeping.
     *
     * @param colonyId the colony's market ID; an ID the fold never met reads as a secret, which is
     *                 the direction that states no finding - a box cannot excuse a concealment on
     *                 the strength of a fold nobody made
     * @return true when the colony is one the sector openly points at
     */
    public boolean isOpenlyKnownColony(String colonyId) {
        return colonyId != null && openlyKnownColonyIds.contains(colonyId);
    }
}
