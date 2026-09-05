package kmu.maplayers.base.sidebar.runtime;

import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.coreui.CodexView;
import kmlib.starsector.ui.coreui.CoreUiDialogView;
import kmlib.starsector.ui.coreui.ModalDialogState;

import kmu.maplayers.base.chrome.arrange.MapLayerArrangementDialog;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Whether something other than the sidebar has claimed the screen this frame - the half of the panel's
 * gate that reads the same wherever the panel draws, as against the half asking whether a given host's
 * own screen is up.
 *
 * <p>Three things claim it, and they claim it for one reason. The sidebar is painted after the whole core
 * UI, so anything the game raises over a screen is raised *underneath* the panel: the panel covers it,
 * undimmed and unaware, while its own hotkeys and hit-testing go on taking input the thing above was
 * opened to receive. Standing the panel down settles both halves at once, and is the better look besides,
 * each of these dimming its own backdrop.
 *
 * <ul>
 *   <li>A text-entry console, which takes the keyboard for the length of a command - which is what frees
 *       the layer shortcut keys to type rather than switch tabs.</li>
 *   <li>The codex, whose panel covers a good part of the screen and whose screen-spanning backdrop takes
 *       the events over the rest of it. Not a case of the modal below it: it is raised outside the core
 *       UI entirely, which is why it needs a reading of its own - see {@link CodexView}.</li>
 *   <li>A modal a core screen has raised in front of itself - a confirmation prompt, a picker - which
 *       takes every event outside its own box and dims the rest of the screen behind it.</li>
 *   <li>This mod's own dialog for arranging the bar, which is a modal in every way but the one that
 *       matters to the reading above: it is a panel of ours standing in the core UI rather than one of
 *       the game's, so nothing about the game's modal base is true of it and the walk that finds those
 *       cannot find this. Read separately for that reason alone - it claims the screen for exactly the
 *       same reason, and the panel it stands over is the one it was opened from.</li>
 * </ul>
 *
 * <p>Bundled rather than passed to a host one read at a time, because a host has no use for any of them
 * separately: it asks one question, and the answer is the same on every screen. That also keeps the
 * reason a panel stood down in one place, so a further claimant is added here rather than at each host.
 *
 * <p>Every read fails open - what cannot be established is not a claim - which is theirs to guarantee
 * rather than this class's to enforce, and each documents it. The composition only has to not add a
 * failure of its own, which is why it holds no state and takes no reading of its own.
 *
 * <p>Order is cheapest first rather than likeliest first. The console read is a settled flag over a
 * static holder; the codex read is one hop off the app state; the modal read walks the core UI's
 * children. The likelier order would be the reverse - none of them is up on most frames - but it would
 * spend a tree walk to save a field read.
 */
public final class ScreenClaim {

    /**
     * The one live pairing, shared by every host: the console, codex and modal reads the running game
     * answers. This is where those bindings are named, so a host depends on the question alone.
     */
    public static final ScreenClaim INSTANCE = new ScreenClaim(
        ConsoleCommandsOverlay.INSTANCE,
        CodexView::isCodexShowing,
        MapLayerArrangementDialog.INSTANCE::isDialogRaised,
        CoreUiDialogView::resolveModalDialogState);

    // A claimant wholly in place, which is what anything that cannot report a fade of its own counts as.
    private static final float FULLY_CLAIMED = 1f;

    // Nothing claiming the screen, so nothing of a claimant is on it.
    private static final float UNCLAIMED = 0f;

    // Whether a console has taken the keyboard. Held as the class rather than behind a role of its own,
    // which is that class's own stated stance: there is one console, so a role here would have exactly
    // one implementation. What varies underneath it is the console state, which it takes standing in.
    private final ConsoleCommandsOverlay consoleOverlay;

    // Whether the codex stands over the screen. A bare presence read: the codex does fade in, but over
    // a few tenths of a second and on a panel the reading behind this never walks to, so there is no
    // curve here to ride and none worth riding.
    private final BooleanSupplier isCodexShowing;

    // Whether this mod's own bar-arranging dialog stands over the screen. A field read on the dialog
    // itself, which is why it sits with the other two rather than with the walk below: nothing has to be
    // searched for a panel this mod put there itself.
    private final BooleanSupplier isArrangementDialogShowing;

    // What a modal a core screen has raised in front of itself is doing - whether it is there, and how
    // far through its fade. One read rather than two, so the presence a claim stands input down on and
    // the fade it hands the draw cannot come off two walks taken either side of a modal being raised.
    private final Supplier<ModalDialogState> modalDialogState;

    ScreenClaim(
        ConsoleCommandsOverlay consoleOverlay,
        BooleanSupplier isCodexShowing,
        BooleanSupplier isArrangementDialogShowing,
        Supplier<ModalDialogState> modalDialogState) {

        this.consoleOverlay = consoleOverlay;
        this.isCodexShowing = isCodexShowing;
        this.isArrangementDialogShowing = isArrangementDialogShowing;
        this.modalDialogState = modalDialogState;
    }

    /**
     * Whether anything has claimed the screen this frame - the crisp answer, taken the moment a claimant
     * appears rather than as it settles in, which is what the panel's input and hit-testing stand down on.
     * A modal takes every event outside its own box from the frame it is raised, so waiting for its fade
     * would leave the panel routing over a dialog already eating the player's clicks.
     *
     * @return whether anything has claimed the screen, and false whenever a claim cannot be established
     */
    public boolean isScreenClaimed() {
        return isClaimantTakenAtFullStrength() || modalDialogState.get().isShowing();
    }

    /**
     * How far in whatever claimed the screen stands, for the panel's <em>paint</em> alone - 0 with the
     * screen unclaimed, 1 with a claimant wholly in place, and the values between while one is arriving
     * or leaving.
     *
     * <p>Separate from the crisp answer above because the two are owed different things. Input has to go
     * the instant a claimant appears; the panel dissolving in step with it is what stops the eye seeing a
     * cut. A modal reports its own fade and the panel rides it exactly, that same curve being what the
     * modal darkens the screen by - so the panel thins as the backdrop deepens instead of vanishing
     * ahead of it.
     *
     * <p>A console reports no fade of its own, so it counts as wholly in place from the moment it opens
     * and the panel goes at once. That is not a shortcoming to correct here: a claimant that snaps is one
     * the panel should snap with, and inventing a fade for it would put the panel halfway through a
     * dissolve the thing above it never performed.
     *
     * <p>The codex is read at full strength too, but on a different footing, and the difference is worth
     * keeping visible: it *does* fade in. Only the fade is a few tenths of a second long and sits on a
     * panel this never reaches - the reading is a flag off the app state, not a walk - so what the panel
     * would gain by riding it is a dissolve nobody has time to see. Dropping it at once reads as the
     * codex arriving, not as a cut. That is a judgement about how brief the fade is rather than a claim
     * that there is none, so a codex that grew a slower one would be worth reading properly.
     *
     * @return how far the claim stands, 0..1, and 0 whenever none can be established
     */
    public float resolveClaimStrength() {

        if (isClaimantTakenAtFullStrength()) {
            return FULLY_CLAIMED;
        }

        var modal = modalDialogState.get();

        return modal.isShowing()
            ? modal.brightness()
            : UNCLAIMED;
    }

    // The claimants there is no fade to follow on - the console having none at all, the codex having one
    // this cannot reach and would not be seen riding, and the bar-arranging dialog having none because it
    // is drawn by the engine's own panel fill, which arrives whole. Named for how they are read rather
    // than for how they arrive, since that is the one thing true of all three and it is what the answers
    // above act on.
    //
    // Named once because both of them turn on it and would otherwise each carry their own copy of which
    // claimants those are - a list that agreed only for as long as nobody added a claimant to one of
    // them. It is also the cheap half of both, so asking it first keeps the modal walk off the frames
    // either of these has already settled.
    private boolean isClaimantTakenAtFullStrength() {

        return consoleOverlay.isOpen()
            || isCodexShowing.getAsBoolean()
            || isArrangementDialogShowing.getAsBoolean();
    }
}
