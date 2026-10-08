package kmu.maplayers.ownermap.owners.holders.base;

import kmlib.starsector.markets.colonies.Colony;

/**
 * What one bloc lives on in one system: how many colonies, and their summed raw size.
 *
 * <p>One value rather than a map apiece, because the band reads the count and the picker reads the
 * size, and the two must name the same blocs. Held side by side in one entry, neither can list a
 * bloc the other left out.
 *
 * @param colonyCount how many colonies the bloc lives on there
 * @param colonySize  those colonies' summed raw sizes
 */
public record BlocHabitation(
    int colonyCount,
    int colonySize) {

    // What one colony adds to the count. A band counts holdings rather than weighing them, so
    // every colony is worth exactly one segment whatever its size or standing.
    private static final int ONE_COLONY = 1;

    /** A bloc living on nothing, which is the fold's identity. */
    public static final BlocHabitation EMPTY = new BlocHabitation(0, 0);

    /**
     * @param colony a colony the bloc lives on
     * @return this habitation with one more colony, of that colony's size
     */
    public BlocHabitation addColony(Colony colony) {

        return new BlocHabitation(
            colonyCount + ONE_COLONY,
            colonySize + colony.market().getSize());
    }

    /**
     * Merges two members' habitations into the bloc they fold into.
     *
     * @param other the other member's habitation
     * @return both habitations summed
     */
    public BlocHabitation addHabitation(BlocHabitation other) {

        return new BlocHabitation(
            colonyCount + other.colonyCount,
            colonySize + other.colonySize);
    }
}
