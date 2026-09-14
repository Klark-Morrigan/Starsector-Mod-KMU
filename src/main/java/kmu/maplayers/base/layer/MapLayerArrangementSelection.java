package kmu.maplayers.base.layer;

/**
 * Where the player's bar arrangement is read from and recorded to.
 *
 * <p>A role rather than a file, the same seam the fold and the active pick already take: what reads
 * an arrangement asks for a preference, and what writes one hands a preference over, neither
 * knowing whether it is kept across sessions or only for this one.
 */
public interface MapLayerArrangementSelection {

    /**
     * @return the arrangement the player has made, or {@link MapLayerArrangement#UNARRANGED} where
     *         they have made none and where the stored one cannot be read - an arrangement that
     *         states nothing being what an unreadable one is worth
     */
    MapLayerArrangement readArrangement();

    /**
     * Records {@code arrangement} as the player's own.
     *
     * @param arrangement the newly arranged bar
     */
    void recordArrangement(MapLayerArrangement arrangement);
}
