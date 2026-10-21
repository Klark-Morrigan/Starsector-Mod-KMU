package kmu.maplayers.base.layer;

/**
 * The bar arrangement the running game reads, and the one place a store is bound to it.
 *
 * <p>Apart from {@link MapLayerRegistry} for the reason {@link MapLayerScreens} is: the roster is what is
 * installed, this is what the player made of it. Apart from the screens because an arrangement is not any
 * screen's - one bar layout serves both, being a preference about the interface rather than a fact about a
 * campaign, so it has no scope to resolve through.
 *
 * <p>The store is bound rather than reached, so nothing on the frame path names a file: what reads an
 * arrangement asks a role, and which role that is comes from the composition root. One bound to no store
 * reads as unarranged, which is the row the roster already gives - the same answer a store that cannot be
 * read returns.
 *
 * <p>Read on every ask rather than settled once, because the row is assembled per frame and the player can
 * change it at any time. What keeps that off the disk is the store bound to it, the running game's being one
 * that holds what it read for the session.
 */
public final class LiveMapLayerArrangement {

    // Where the player's arrangement is read from, or null where none is bound.
    private final MapLayerArrangementSelection arrangementSelection;

    /**
     * @param arrangementSelection the store to read the arrangement from and write it back to, or null
     *                             for a bar that reads as unarranged and offers nowhere to record to
     */
    public LiveMapLayerArrangement(MapLayerArrangementSelection arrangementSelection) {
        this.arrangementSelection = arrangementSelection;
    }

    /**
     * The store itself, for the one caller that writes as well as reads: the control the player
     * arranges the bar with, which has to put its changes back where the bar reads them from.
     *
     * <p>Beside the read rather than replacing it, because everything on the frame path only reads and
     * has no business holding something it could write through. Null rather than a store that discards
     * writes: a control with nowhere to record to should not open and pretend, and every other caller
     * already has the reading below.
     *
     * @return the bound store, or null where none is bound
     */
    public MapLayerArrangementSelection resolveArrangementSelection() {
        return arrangementSelection;
    }

    /**
     * @return the arrangement the player has made, or {@link MapLayerArrangement#UNARRANGED} where no
     *         store is bound
     */
    public MapLayerArrangement resolveArrangement() {

        return arrangementSelection == null
            ? MapLayerArrangement.UNARRANGED
            : arrangementSelection.readArrangement();
    }
}
