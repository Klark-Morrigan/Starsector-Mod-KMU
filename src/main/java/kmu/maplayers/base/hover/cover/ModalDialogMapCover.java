package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.coreui.CoreUiDialogView;

import java.util.function.BooleanSupplier;

/**
 * The cover a modal raised over a core screen lays over the map - a confirmation prompt, a picker.
 * It takes every event outside its own box and dims the rest of the screen behind it, so while one
 * is up nothing the cursor rests on is the map.
 *
 * <p>Its own cover rather than a case of the two beside it, because it is neither: the pause menu is
 * raised over the campaign and reported as such, and a console belongs to an optional mod, while
 * this is stood up inside the core UI by the screen the player is already looking at. Nothing the
 * campaign publishes reports it, which is why the reading is
 * {@link CoreUiDialogView#isModalDialogShowing()} rather than a flag.
 *
 * <p>Nor can the sidebar's cover stand in for it, though the panel does stand down under the same
 * modal: that is exactly what makes the sidebar go quiet on these frames, leaving the cells the
 * panel is no longer over to answer the cursor as if nothing were on screen. The map goes on being
 * drawn and the terrain pass goes on running behind the dim, so an ungated hover lights cells under
 * the prompt and floats a box over it.
 *
 * <p>No cursor test, for the same reason the pause menu and the console need none: a modal claims
 * the whole screen outside its own box, so where the pointer is does not come into it.
 *
 * <p>Fails open per the role's rule - a core UI that cannot be walked reports no modal - which the
 * reading behind it guarantees and documents.
 */
public final class ModalDialogMapCover implements MapCover {

    private final BooleanSupplier isModalDialogShowing;

    /** Reads the live core UI - the pairing a running game gets. */
    public ModalDialogMapCover() {
        this(CoreUiDialogView::isModalDialogShowing);
    }

    ModalDialogMapCover(BooleanSupplier isModalDialogShowing) {
        this.isModalDialogShowing = isModalDialogShowing;
    }

    @Override
    public boolean isCoveringCursor() {
        // A walk of the core UI's own children, which is what puts it above the settled flags and
        // below the covers that resolve a layout or walk down into a tab.
        return isModalDialogShowing.getAsBoolean();
    }
}
