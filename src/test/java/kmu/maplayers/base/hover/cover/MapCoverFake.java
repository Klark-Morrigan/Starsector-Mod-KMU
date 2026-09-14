package kmu.maplayers.base.hover.cover;

/**
 * A cover that is over the cursor because a test said so, and that remembers whether it was asked -
 * which is what lets the reader's short-circuit be pinned rather than assumed.
 */
final class MapCoverFake implements MapCover {

    private final boolean isCovering;

    private boolean hasBeenAsked;

    MapCoverFake(boolean isCovering) {
        this.isCovering = isCovering;
    }

    @Override
    public boolean isCoveringCursor() {
        hasBeenAsked = true;
        return isCovering;
    }

    boolean hasBeenAsked() {
        return hasBeenAsked;
    }
}
