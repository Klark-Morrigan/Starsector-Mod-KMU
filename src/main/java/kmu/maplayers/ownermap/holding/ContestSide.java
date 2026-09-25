package kmu.maplayers.ownermap.holding;

/**
 * One side of a contest over a system: the blocs standing with whoever holds it, or the blocs
 * standing against them.
 *
 * <p>Names which side a caller wants of {@link ContestSides}, and carries the test that decides it,
 * so selecting a side is a lookup rather than a branch: the two sides differ in nothing but which
 * answer they take, and stating that as data is what stops them drifting into two rules.
 */
public enum ContestSide {

    /** The blocs the affiliation folds into the holder's own - empty wherever nothing groups
     *  factions, and wherever nobody holds the system. */
    ALLIED(true),

    /** The blocs the affiliation leaves standing against the holder - everyone the other side did
     *  not take. */
    RIVAL(false);

    private final boolean isSideStandingWithHolder;

    ContestSide(boolean isSideStandingWithHolder) {
        this.isSideStandingWithHolder = isSideStandingWithHolder;
    }

    /**
     * Whether this side takes a contestant, given how that contestant stands to the holder.
     *
     * @param isStandingWithHolder whether the affiliation folds the contestant into the holder's
     *                             bloc
     * @return true where the contestant belongs to this side
     */
    boolean isTakingContestant(boolean isStandingWithHolder) {
        return isStandingWithHolder == isSideStandingWithHolder;
    }
}
