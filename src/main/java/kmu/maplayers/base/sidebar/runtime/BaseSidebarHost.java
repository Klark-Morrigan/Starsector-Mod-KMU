package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.sound.VanillaUiSoundPlayer;
import kmlib.starsector.ui.widgets.tabs.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.tabs.TabStrip;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarFoldSelection;
import kmu.maplayers.base.sidebar.style.SidebarStyles;
import kmu.settings.KmuMapLayerSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * The plumbing every sidebar host shares: the panel's controller, the fold selection behind it, the screen's
 * active-layer pick, the reseed that opens the panel at the fold a loaded save was left at, and the shortcut
 * key that jumps to a layer. A concrete host supplies its own fold selection and its own layer selection -
 * its own frozen keys and its own opening default - and answers the questions that actually differ between
 * screens: when the sidebar is live, where it anchors, which frame edges it strokes, and how its view state
 * reads.
 *
 * <p>The shortcut jump lives here because the panel offers the same tabs on every screen that shows it, so
 * the key that reaches a tab should not depend on which screen the player is looking at. It writes the
 * host's own selection, so a shortcut moves the tab of the screen it was pressed on and leaves the other
 * screen's where it was. Which keycodes those are is the player's to change through the layer settings,
 * which is also the way out of a clash with a screen's own bindings.
 *
 * <p>Whether the sidebar is live at all is settled here too, since only half of that answer differs between
 * screens: a host says whether its own screen is up, and standing down for whatever else has claimed the
 * screen is the same rule wherever the panel draws. It is settled twice over - once crisply for input, and
 * once as an alpha for the draw - because a claimant that fades is owed the pointer at once and the pixels
 * only as it arrives. Both compose the same two readings, so a host still answers for its screen alone.
 *
 * <p>The controller is replaced on each load rather than mutated, because the two folds a panel can open at
 * are exactly the two constructors the widget already offers, so no reach into the collapse animation is
 * needed to seed it. Replacing also clears the previous save's scroll offset and fold together, which
 * matters because hosts are process-lifetime singletons: without the reseed, one save's panel state would
 * carry into the next save loaded in the same run.
 */
public abstract class BaseSidebarHost implements SidebarHost {

    // The two ends of the panel's own alpha: wholly painted, and gone. A claim's strength is subtracted
    // from the first, so what a claim reports is named where claims are and never mirrored here.
    private static final float FULLY_PAINTED = 1f;
    private static final float HIDDEN = 0f;

    // Where this host's panel fold is read from and recorded to. Supplied by the concrete host, so the
    // frozen memory key and the fold the screen opens at stay with the screen that owns them.
    private final SidebarFoldSelection foldSelection;

    // The screen's active-layer pick: which tab is lit here, and what a shortcut key or a tab press moves.
    // Supplied by the concrete host, so each screen keeps its own pick and a switch on one leaves the other
    // untouched.
    private final ActiveLayerSelection layerSelection;

    // Whether anything else has claimed the screen this frame. Handed in rather than composed here, so a
    // host depends on the one question and not on which things can answer it - among them an optional mod.
    private final ScreenClaim screenClaim;

    // The panel's scroll and collapse state. Seeded from the fold selection at construction so the panel is
    // safe to draw before any save is loaded, then replaced per load by restoreFoldFromSave.
    private TabPanelController controller;

    protected BaseSidebarHost(
            SidebarFoldSelection foldSelection,
            ActiveLayerSelection layerSelection,
            ScreenClaim screenClaim) {

        this.foldSelection = foldSelection;
        this.layerSelection = layerSelection;
        this.screenClaim = screenClaim;
        // The seed takes the library's own balance rather than the player's, being the one controller
        // nothing can be heard through: a host is a process-lifetime singleton built before any sector
        // exists, so no panel is on screen for a pointer to reach until the load below replaces it. What
        // that buys is a host whose construction reads no setting at all, which is what keeps a singleton
        // built at class load from depending on the settings mod having loaded first.
        this.controller = createControllerAtFold(
            foldSelection.isRailDocked(),
            UiSoundScheme.createVanillaSoundScheme());
    }

    @Override
    public final TabPanelController getController() {
        return controller;
    }

    @Override
    public final SidebarFoldSelection getFoldSelection() {
        return foldSelection;
    }

    /**
     * Switches to the layer whose bound key was pressed, blinks that layer's tab, and consumes the event, so
     * the key does not also trigger a binding on the screen underneath sharing it. A key bound to no layer is
     * left alone and falls through untouched.
     *
     * <p>The blink is what tells the player the press landed. A tab press has the pointer on the tab to say
     * where the switch came from; a keypress has nothing on screen at all, so without it a shortcut that
     * reached an already-shown layer would look like a key the panel ignored.
     *
     * <p>A layer's tab sits at its registry index - the tabs row is built from the same registry in the same
     * order - so the index the binder matched is the index the panel blinks.
     *
     * @param event the key-down event
     */
    @Override
    public final void handleKeyPress(InputEventAPI event) {
        var layers = MapLayerRegistry.getLayers();
        var tabIndex = TabPanelHotkeys.findTabForKey(
            event.getEventValue(),
            layerKeycodes(layers));

        if (tabIndex == TabStrip.NO_TAB) {
            return;
        }
        layerSelection.selectLayer(layers.get(tabIndex));
        controller.startHotkeyBlinkAt(tabIndex);
        event.consume();
    }

    /**
     * Whether the sidebar is live on this host's screen this frame: its screen is showing and nothing else
     * has claimed the screen. What can claim it, and why any of it stands the whole panel down, is
     * {@link ScreenClaim}'s.
     *
     * <p>Composed here rather than left to each host because this one answer gates the draw, the input
     * routing and the hit-test alike - three readings that would each have to remember the claim for
     * themselves, and a further screen a further chance to forget it. What differs per screen is only
     * {@link #isHostScreenShowing()}.
     *
     * @return whether the sidebar draws and routes on this host's screen this frame
     */
    @Override
    public final boolean isOverlayShowing() {
        // The claim is asked first because it is the cheaper of the two on the frames that matter: it
        // settles the console with a field read, while every screen read here walks live widgets. The
        // likelier order would be the reverse - the panel's screens are off far more often than anything
        // claims them - but it would spend a walk to save a read.
        return !screenClaim.isScreenClaimed() && isHostScreenShowing();
    }

    /**
     * The paint's half of the same pair, so a claimant that fades takes the panel with it rather than
     * cutting it away. Composed here for the reason the gate is: one answer, and only
     * {@link #isHostScreenShowing()} differs per screen.
     *
     * @return the panel's alpha this frame
     */
    @Override
    public final float resolveOverlayFade() {

        // What the claim has not taken. Asked in the gate's own order and short-circuited at the same
        // place: a panel already faded out paints nothing whichever screen it is on, so the widget walk
        // is skipped exactly as it is there.
        var fade = FULLY_PAINTED - screenClaim.resolveClaimStrength();

        return fade <= HIDDEN || !isHostScreenShowing()
            ? HIDDEN
            : fade;
    }

    /**
     * Opens the panel at the fold the loaded save was left at, replacing the controller with one seeded at
     * that end so the panel is already there on the first frame rather than sliding into place. Replacing
     * also clears the previous save's scroll offset in the same move.
     */
    @Override
    public final void restoreFoldFromSave() {
        // The first controller a player can actually reach, so this is where the panel's own balance
        // arrives: composed from the sliders rather than taken from the library, and composed afresh on
        // each load so a level changed between saves is answered at the next one.
        controller = createControllerAtFold(
            foldSelection.isRailDocked(),
            SidebarStyles.buildSidebarSoundScheme());
    }

    /**
     * @return this host's screen's active-layer pick, for a subclass to lay the panel out around the lit
     *         tab. The same selection the shortcut key writes, so the layout and the jump never disagree
     *         on which pick is this screen's
     */
    protected final ActiveLayerSelection getLayerSelection() {
        return layerSelection;
    }

    /**
     * @return whether this host's own screen is up and carrying the panel this frame. The screen half of
     *         the gate and nothing more - what else on the machine may have claimed the keyboard is settled
     *         in {@link #isOverlayShowing()}, so a host answers only for the screen it binds to
     */
    protected abstract boolean isHostScreenShowing();

    // A controller opened at the given fold, answering by the given sound scheme. The docked seed and the
    // expanded default are the widget's own two constructors, so the fold a host opens at is chosen here
    // rather than animated into.
    //
    // The scheme is a parameter rather than composed here because the two callers want different ones and
    // for a reason that is not about sound: the seed is built during class initialisation, where reading a
    // setting would be reaching for another mod mid-load, and the reseed runs with a sector up, where the
    // player's own levels are there to be read. Both hand over a whole scheme, so neither can compose half
    // of one.
    private static TabPanelController createControllerAtFold(
        boolean isRailDocked,
        UiSoundScheme soundScheme) {

        // Bound once so the two folds differ in the fold alone: the sound wiring is the same either way,
        // and written twice it would be a place for the docked panel to drift from the expanded one.
        var soundPlayer = new VanillaUiSoundPlayer();

        return isRailDocked
            ? TabPanelController.createStartingDocked(soundPlayer, soundScheme)
            : new TabPanelController(soundPlayer, soundScheme);
    }

    // Each layer's bound keycode in registry order, so a matched index maps back to its layer. A cleared
    // shortcut reads as 0 (LWJGL's KEY_NONE); the binder treats a non-positive keycode as unbound and never
    // matches it.
    private static List<Integer> layerKeycodes(List<MapLayer> layers) {
        var keycodes = new ArrayList<Integer>(layers.size());
        for (var layer : layers) {
            keycodes.add(KmuMapLayerSettings.getMapLayerShortcut(
                layer.getShortcutSettingKey(),
                layer.getDefaultShortcutKeycode()));
        }
        return keycodes;
    }
}
