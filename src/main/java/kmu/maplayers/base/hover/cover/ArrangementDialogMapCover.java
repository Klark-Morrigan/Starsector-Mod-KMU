package kmu.maplayers.base.hover.cover;

import kmu.maplayers.base.chrome.arrange.MapLayerArrangementDialog;

import java.util.function.BooleanSupplier;

/**
 * The cover this mod's own bar-arranging dialog lays over the map. It stands across the screen, dims
 * what is behind it and claims the events outside its own box, so while it is up nothing the cursor
 * rests on is the map.
 *
 * <p>Its own cover rather than a case of {@link ModalDialogMapCover}, and the difference is the whole
 * reason this class exists. That one recognises a modal by the accessor the game's own modal base
 * carries; this dialog is a panel of ours stood in the core UI's tree, descends from nothing of the
 * game's, and so is invisible to that walk however modal it behaves. Reading it through its own state
 * is the only reading there is.
 *
 * <p>Nor can the sidebar's cover stand in for it, though the panel does stand down under this dialog
 * for the same reason: that is exactly what leaves the cells the panel is no longer over answering the
 * cursor as if nothing were on screen, while the map goes on being drawn behind the dim.
 *
 * <p>No cursor test. The dialog claims the whole screen outside its own box, so where the pointer is
 * does not come into it - the same reasoning the modal, pause menu and console covers stand on.
 *
 * <p>Fails open per the role's rule, and cannot fail otherwise: the reading is a field on a dialog this
 * mod owns, so there is no tree to walk and nothing to be unable to establish.
 */
public final class ArrangementDialogMapCover implements MapCover {

    private final BooleanSupplier isArrangementDialogShowing;

    /** Reads the live dialog - the pairing a running game gets. */
    public ArrangementDialogMapCover() {
        this(MapLayerArrangementDialog.INSTANCE::isDialogRaised);
    }

    ArrangementDialogMapCover(BooleanSupplier isArrangementDialogShowing) {
        this.isArrangementDialogShowing = isArrangementDialogShowing;
    }

    @Override
    public boolean isCoveringCursor() {
        // A field read on a panel this mod put on screen itself, which is what puts it among the
        // cheapest covers rather than beside the walks that go looking for somebody else's widgets.
        return isArrangementDialogShowing.getAsBoolean();
    }
}
