package kmu.maplayers.ownermap.render.rebuild;

/**
 * How much of an owner map one frame owes.
 *
 * <p>Three values rather than two booleans, because a recut reseeds every cell the clusters group:
 * a stale cut with fresh content is not a state the map can be in, so it has no value to be.
 */
enum Staleness {

    /** Nothing owed: the frame stops at the decision and only folds what was marked. */
    NOTHING,

    /** The clusters rebuild over the cells already cut. */
    CONTENT,

    /** The cells recut, and the clusters rebuild over them. */
    CUT_AND_CONTENT;

    /** @return whether the cells have to be recut, which owes the content too */
    boolean isCellCutStale() {
        return this == CUT_AND_CONTENT;
    }
}
