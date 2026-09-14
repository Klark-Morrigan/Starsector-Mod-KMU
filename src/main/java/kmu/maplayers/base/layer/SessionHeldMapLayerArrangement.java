package kmu.maplayers.base.layer;

/**
 * A {@link MapLayerArrangementSelection} read once and held for the session, over a store that is slower
 * than the path reading it.
 *
 * <p>The bar is laid out on every frame the sidebar draws and walked again on every keypress it routes, and
 * each of those asks what the player arranged. The store beneath is a file on disk, so asked straight it
 * would be opened and parsed sixty times a second for an answer that changes only when the player says so -
 * and they say so here, the dialog writing through this rather than around it.
 *
 * <p>Read at the first ask rather than at construction, so mod load spends no disk read and a store not yet
 * reachable when the composition root ran is not what the whole session is left holding. The first ask is a
 * bar being drawn, which is a sector map away from any of that.
 *
 * <p>A file edited by hand while the game runs is therefore not picked up until the next start. That is the
 * price of the read above, and it is the right way round: the arrangement is the player's own, made in the
 * dialog, and a session that re-read the file would still be showing them what the dialog last wrote.
 */
public final class SessionHeldMapLayerArrangement implements MapLayerArrangementSelection {

    // What the arrangement is actually kept in, and the only thing that outlives the session.
    private final MapLayerArrangementSelection storedArrangement;

    // What has been read or written since, or null before the first ask. Null rather than a flag, an
    // arrangement never being null once read - the store answers the unarranged one where it has nothing.
    private MapLayerArrangement heldArrangement;

    /**
     * @param storedArrangement where the arrangement is kept between sessions
     */
    public SessionHeldMapLayerArrangement(MapLayerArrangementSelection storedArrangement) {
        this.storedArrangement = storedArrangement;
    }

    @Override
    public MapLayerArrangement readArrangement() {

        if (heldArrangement == null) {
            heldArrangement = storedArrangement.readArrangement();
        }
        return heldArrangement;
    }

    @Override
    public void recordArrangement(MapLayerArrangement arrangement) {

        storedArrangement.recordArrangement(arrangement);

        // Held whether or not the store took it, so a write that failed still shows the player the bar
        // they just arranged for the rest of the session - which is what the store's own failure line
        // already tells them to expect.
        heldArrangement = arrangement;
    }
}
