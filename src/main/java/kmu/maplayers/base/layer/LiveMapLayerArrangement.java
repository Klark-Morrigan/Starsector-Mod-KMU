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
 * arrangement asks a role, and which role that is comes from the composition root. An install whose
 * composition root bound none reads as unarranged, which is the row the roster already gives - the same
 * answer a store that cannot be read returns, and the reason the bar draws before any of this is wired.
 *
 * <p>Read on every ask rather than settled once, because the row is assembled per frame over a roster that
 * is never settled. What keeps that off the disk is the store bound to it, the running game's being one
 * that holds what it read for the session.
 */
public final class LiveMapLayerArrangement {

    // Where the player's arrangement is read from. Null until a composition root binds one, which is the
    // reading every install gives before its own load has run and the one a foreign install keeps.
    private static MapLayerArrangementSelection arrangementSelection;

    private LiveMapLayerArrangement() {
    }

    /**
     * Binds where the player's arrangement is read from and recorded to. Called by the composition root,
     * the one place a concrete store is named.
     *
     * @param arrangementSelection the store to read the arrangement from and write it back to
     */
    public static void registerArrangementSelection(MapLayerArrangementSelection arrangementSelection) {
        LiveMapLayerArrangement.arrangementSelection = arrangementSelection;
    }

    /**
     * The store itself, for the one caller that writes as well as reads: the control the player
     * arranges the bar with, which has to put its changes back where the bar reads them from.
     *
     * <p>Beside the read rather than replacing it, because everything on the frame path only reads and
     * has no business holding something it could write through. Null rather than a store that discards
     * writes: a control with nowhere to record to should not open and pretend, and every other caller
     * already has the reading above.
     *
     * @return the bound store, or null while none is bound
     */
    public static MapLayerArrangementSelection resolveArrangementSelection() {
        return arrangementSelection;
    }

    /**
     * @return the arrangement the player has made, or {@link MapLayerArrangement#UNARRANGED} while no
     *         store is bound
     */
    public static MapLayerArrangement resolveArrangement() {
        return arrangementSelection == null
            ? MapLayerArrangement.UNARRANGED
            : arrangementSelection.readArrangement();
    }

    /**
     * Unbinds the store, leaving the reading an install that has bound none gives.
     *
     * <p>Package-private, unlike the binding above: nothing in a running game takes its own preferences
     * back off, so a caller able to unbind could only ever drop an arrangement the player still has stored
     * and cannot see the loss of.
     */
    static void forgetArrangementSelection() {
        arrangementSelection = null;
    }
}
