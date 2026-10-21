package kmu.maplayers.ownermap.owners;

/**
 * Who stands together in a contest: whether two owners are on one side.
 *
 * <p>A question apart from which owner a cell is painted for. Two owners a layer paints apart can
 * still be allies, and a band laid at contested length over two allies would say they were fighting
 * over the system. So a layer states who stands together beside its owners, and the band asks this
 * rather than comparing owner IDs.
 *
 * <p>The tier asks the question and never answers it. A layer with no sides to speak of answers
 * {@link #NONE}.
 */
@FunctionalInterface
public interface OwnerAffiliation {

    /**
     * The affiliation under which no two owners stand together - every owner is its own side.
     */
    OwnerAffiliation NONE = (firstOwnerId, secondOwnerId) -> false;

    /**
     * Whether two distinct owners stand on one side of a contest.
     *
     * @param firstOwnerId  one owner of the pair
     * @param secondOwnerId the other owner of the pair
     * @return true when the two are distinct owners on one side; false for one owner named twice,
     *         which is one side rather than two standing together
     */
    boolean areOwnersAllied(String firstOwnerId, String secondOwnerId);
}
