package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.coreui.CoreUiDialogView;

import java.util.function.BooleanSupplier;

/**
 * Whether something other than the sidebar has claimed the screen this frame - the half of the panel's
 * gate that reads the same wherever the panel draws, as against the half asking whether a given host's
 * own screen is up.
 *
 * <p>Two things claim it, and they claim it for one reason. The sidebar is painted after the whole core
 * UI, so anything the game raises over a screen is raised *underneath* the panel: the panel covers it,
 * undimmed and unaware, while its own hotkeys and hit-testing go on taking input the thing above was
 * opened to receive. Standing the panel down settles both halves at once, and is the better look besides,
 * each of these dimming its own backdrop.
 *
 * <ul>
 *   <li>A text-entry console, which takes the keyboard for the length of a command - which is what frees
 *       the layer shortcut keys to type rather than switch tabs.</li>
 *   <li>A modal a core screen has raised in front of itself - a confirmation prompt, a picker - which
 *       takes every event outside its own box and dims the rest of the screen behind it.</li>
 * </ul>
 *
 * <p>Bundled rather than passed to a host one read at a time, because a host has no use for either
 * separately: it asks one question, and the answer is the same on every screen. That also keeps the
 * reason a panel stood down in one place, so a third claimant is added here rather than at each host.
 *
 * <p>Both reads fail open - what cannot be established is not a claim - which is theirs to guarantee
 * rather than this class's to enforce, and each documents it. The composition only has to not add a
 * failure of its own, which is why it holds no state and takes no reading of its own.
 *
 * <p>Order is cheapest first rather than likeliest first. The console read is a settled flag over a
 * static holder; the modal read walks the core UI's children. The likelier order would be the reverse -
 * neither is up on most frames - but it would spend a tree walk to save a field read.
 */
public final class ScreenClaim {

    /**
     * The one live pairing, shared by every host: the console read and the modal read the running game
     * answers. This is where those bindings are named, so a host depends on the question alone.
     */
    public static final ScreenClaim INSTANCE = new ScreenClaim(
        ConsoleCommandsOverlay.INSTANCE,
        CoreUiDialogView::isModalDialogShowing);

    // Whether a console has taken the keyboard. Held as the class rather than behind a role of its own,
    // which is that class's own stated stance: there is one console, so a role here would have exactly
    // one implementation. What varies underneath it is the console state, which it takes standing in.
    private final ConsoleCommandsOverlay consoleOverlay;

    // Whether a core screen has raised a modal in front of itself. A supplier rather than a named type,
    // the live read being one static method and a suite wanting nothing more than the two answers.
    private final BooleanSupplier isModalDialogShowing;

    ScreenClaim(ConsoleCommandsOverlay consoleOverlay, BooleanSupplier isModalDialogShowing) {
        this.consoleOverlay = consoleOverlay;
        this.isModalDialogShowing = isModalDialogShowing;
    }

    /**
     * @return whether anything has claimed the screen this frame, on which the panel neither draws nor
     *         routes input; false when nothing has, and false whenever a claim cannot be established
     */
    public boolean isScreenClaimed() {
        return consoleOverlay.isOpen() || isModalDialogShowing.getAsBoolean();
    }
}
