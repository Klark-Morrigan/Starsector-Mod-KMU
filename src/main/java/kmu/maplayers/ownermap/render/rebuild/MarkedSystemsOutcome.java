package kmu.maplayers.ownermap.render.rebuild;

/**
 * What a refresh strategy did with a batch of marked systems, which is what the cache needs to
 * know to leave the map right on screen.
 */
public enum MarkedSystemsOutcome {

    /** The batch was folded into the standing map, which now draws what a rebuild would. */
    FOLDED,

    /**
     * The batch was not folded, so the next frame owes a full rebuild: nothing else remembers the
     * marks once they are drained.
     */
    REBUILD_OWED
}
