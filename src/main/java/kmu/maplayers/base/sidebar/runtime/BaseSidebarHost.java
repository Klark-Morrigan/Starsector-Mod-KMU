package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.sound.VanillaUiSoundPlayer;
import kmlib.starsector.ui.widgets.tabs.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabStrip;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenLayerTabs;
import kmu.maplayers.base.sidebar.PersistedSidebarFold;
import kmu.maplayers.base.sidebar.SidebarFoldSelection;
import kmu.maplayers.base.sidebar.style.SidebarStyles;

import java.util.List;

/**
 * The plumbing every sidebar host shares: the panel's controller, the fold selection behind it, the screen's
 * picks - which layer is active there and whether its layers show at all - the reseed that opens the panel at
 * the fold a loaded save was left at, and the shortcut key that jumps to a layer. A concrete host supplies
 * its own fold selection and its own screen's picks - both under that screen's own scope, and its own
 * opening default - and answers the questions that actually differ between screens: whether its screen is up, where it anchors,
 * which frame edges it strokes, and how that screen's own view state reads.
 *
 * <p>The shortcut jump lives here because the panel draws one body of tabs wherever it shows, so the key
 * that reaches a tab should not depend on which screen the player is looking at. It writes the host's own
 * selection, so a shortcut moves the tab of the screen it was pressed on and leaves the other screen's
 * where it was - and it walks that screen's own row, so a tab withheld from this screen answers no key
 * here while going on answering on a screen that still offers it. Which key reaches which tab is each
 * layer's own answer, asked afresh at every press; a layer with none is skipped, so an unbound tab never
 * captures a key.
 *
 * <p>Whether the sidebar is live at all is settled here too, since only one part of that answer differs
 * between screens: a host says whether its own screen is up, while standing down for whatever else has
 * claimed the screen, and for the screen's layers being switched off, are the same rules wherever the panel
 * draws. It is settled twice over - once crisply for input, and once as an alpha for the draw - because
 * something that fades is owed the pointer at once and the pixels only as it arrives. Both compose the same
 * readings, so a host still answers for its screen alone.
 *
 * <p>The show-or-hide pick is folded in here rather than at the renderer and the input listener separately,
 * for the reason it is folded into the active-layer answer rather than into each pass driven by it: the
 * panel is part of what the layers put on a screen, so it goes with the rest of that footprint on one read
 * taken where the gate already is.
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

    // The end of the screen's own show-or-hide ramp - none of the layers left on it. The same number as
    // the alpha above and deliberately not the same name: one is how much of the panel is painted, the
    // other how far a pick has travelled, and a reading is compared against the one it is measured in.
    private static final float FULLY_HIDDEN = 0f;

    // Where this host's panel fold is read from and recorded to. Supplied by the concrete host, so the
    // frozen memory key and the fold the screen opens at stay with the screen that owns them.
    private final SidebarFoldSelection foldSelection;

    // The screen's picks: which tab is lit here, whether the layers are on this screen at all, and the
    // scope its keys compose through. Supplied by the concrete host as one value, so each screen keeps its
    // own state, a switch or a hide on one leaves the other untouched, and no host can be wired to one
    // screen's tab and another's hiding.
    private final ScreenLayerPicks screenPicks;

    // Whether anything else has claimed the screen this frame. Handed in rather than composed here, so a
    // host depends on the one question and not on which things can answer it - among them an optional mod.
    private final ScreenClaim screenClaim;

    // The panel's scroll and collapse state. Seeded from the fold selection at construction so the panel is
    // safe to draw before any save is loaded, then replaced per load by restoreFoldFromSave.
    private TabPanelController controller;

    // The placement this frame's draw laid out, held so the passes that come after it read the panel that
    // was actually drawn rather than laying out one of their own. Null whenever nothing is on screen: the
    // draw drops it as it stands down, so no pass can hit-test a panel that has left.
    private TabPanelPlacement drawnPlacement;

    /**
     * A host whose fold persists in the save under the same screen its picks are held for, which is what
     * every host on a real screen wants: composed here rather than at each host so the fold and the picks
     * cannot be built for two different screens, an arrangement that draws one screen's panel while
     * folding another's.
     *
     * @param screenPicks      this host's screen's picks, whose scope the fold is stored under
     * @param isDockedDefault  the fold a save holding no choice yet opens this host's panel at
     * @param screenClaim      whether anything else has claimed the screen this frame
     */
    protected BaseSidebarHost(
            ScreenLayerPicks screenPicks,
            boolean isDockedDefault,
            ScreenClaim screenClaim) {

        this(
            new PersistedSidebarFold(screenPicks.memoryScope(), isDockedDefault),
            screenPicks,
            screenClaim);
    }

    /**
     * A host over any fold at all, for the one case the constructor above cannot serve: a fold that is not
     * the save's. The seam stays open because where a fold is read from and recorded to is a role
     * ({@link SidebarFoldSelection}) rather than a fact about sector memory.
     *
     * @param foldSelection where this host's fold is read from and recorded to
     * @param screenPicks   this host's screen's picks
     * @param screenClaim   whether anything else has claimed the screen this frame
     */
    protected BaseSidebarHost(
            SidebarFoldSelection foldSelection,
            ScreenLayerPicks screenPicks,
            ScreenClaim screenClaim) {

        this.foldSelection = foldSelection;
        this.screenPicks = screenPicks;
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

    /**
     * The host's own screen state, prefixed with this screen's show-or-hide pick whenever the layers are
     * not on it - so "the sidebar is gone" is answerable from the log rather than from a second question
     * to the player.
     *
     * <p>The settled state and the ramp are named apart because they are different reports: a panel that
     * stays away was switched off, while one caught part-way through the ramp was on its way out when the
     * line was written.
     *
     * @return this host's view state, for the sidebar's view-state log
     */
    @Override
    public final String describeViewState() {

        var hostScreenState = describeHostScreenViewState();
        var visibility = screenPicks.layerVisibility();

        if (visibility.areLayersShown()) {
            return hostScreenState;
        }
        // The fade is consulted only for a screen already switched off, so a shown screen's line costs
        // the pick alone: the reading behind it takes a clock and the hide-pace setting with it.
        var hidingState = visibility.resolveShownFade() > FULLY_HIDDEN
            ? "layers hiding; "
            : "layers hidden; ";

        return hidingState + hostScreenState;
    }

    /**
     * Lays this host's panel out and publishes it as the panel on screen, which is what makes one layout a
     * frame enough: the passes that follow the draw read what it published instead of laying out again, so
     * a click and a hover answer to the box the player is actually looking at rather than to one computed
     * a moment later from settings that may have moved under them.
     *
     * @return the placement just laid out, or {@code null} when there is nothing to draw
     */
    @Override
    public final TabPanelPlacement refreshPlacement() {
        drawnPlacement = computePlacement();
        return drawnPlacement;
    }

    @Override
    public final TabPanelPlacement getDrawnPlacement() {
        return drawnPlacement;
    }

    @Override
    public final void clearDrawnPlacement() {
        drawnPlacement = null;
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
     * <p>A layer's tab sits at its place in the row the panel draws, and both are taken from the one read
     * that says which layers this screen is offered - so the index the binder matched is the index the panel
     * blinks, and a tab this screen is not offered answers no key rather than switching to a layer the
     * player cannot see lit.
     *
     * @param event the key-down event
     */
    @Override
    public final void handleKeyPress(InputEventAPI event) {
        var layers = ScreenLayerTabs.resolveTabbedLayers(screenPicks);
        var tabIndex = TabPanelHotkeys.findTabForKey(
            event.getEventValue(),
            resolveLayerKeycodes(layers));

        if (tabIndex == TabStrip.NO_TAB) {
            return;
        }
        screenPicks.layerSelection().selectLayer(layers.get(tabIndex));
        controller.startHotkeyBlinkAt(tabIndex);
        event.consume();
    }

    /**
     * Whether the sidebar is live on this host's screen this frame: its screen is showing, this screen's
     * layers are switched on, and nothing else has claimed the screen. What can claim it, and why any of it
     * stands the whole panel down, is {@link ScreenClaim}'s.
     *
     * <p>Composed here rather than left to each host because this one answer gates the draw, the input
     * routing and the hit-test alike - readings that would each have to remember the claim and the pick for
     * themselves, and a further screen a further chance to forget one. What differs per screen is only
     * {@link #isHostScreenShowing()}.
     *
     * <p>The pick is taken crisply rather than off the ramp, so a panel switched off stops answering the
     * pointer on the frame the player switched it off, whatever of it is still dissolving.
     *
     * @return whether the sidebar draws and routes on this host's screen this frame
     */
    @Override
    public final boolean isOverlayShowing() {
        // Cheapest first. The claim settles the console with a field read; the pick behind it is a lookup
        // in sector memory; the screen read walks live widgets. The likelier order would put the screen
        // first - the panel's screens are off far more often than anything claims them or the layers are
        // hidden - but it would spend a walk to save a read.
        return !screenClaim.isScreenClaimed()
            && screenPicks.layerVisibility().areLayersShown()
            && isHostScreenShowing();
    }

    /**
     * The paint's half of the same pair, so a claimant that fades - or a screen whose layers are on their
     * way off it - takes the panel with it rather than cutting it away. Composed here for the reason the
     * gate is: one answer, and only {@link #isHostScreenShowing()} differs per screen.
     *
     * @return the panel's alpha this frame
     */
    @Override
    public final float resolveOverlayFade() {

        // What the claim has not taken. Asked in the gate's own order and short-circuited at the same
        // place: a panel already faded out paints nothing whichever screen it is on, so the readings after
        // it are skipped exactly as they are there.
        var claimFade = FULLY_PAINTED - screenClaim.resolveClaimStrength();
        if (claimFade <= HIDDEN) {
            return HIDDEN;
        }
        // The two dissolves compose rather than one winning, which is what lets a modal raised over a
        // panel already thinning darken over it: neither has to know the other is running.
        var fade = claimFade * screenPicks.layerVisibility().resolveShownFade();

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
     * Lays this host's panel out from the live screen, settings and active layer. The publishing half is
     * {@link #refreshPlacement()}'s, so a host states how its own panel is anchored and nothing about when
     * that layout is spent or who reads it afterwards.
     *
     * @return the placement for this frame, or {@code null} when there is nothing to draw
     */
    protected abstract TabPanelPlacement computePlacement();

    /**
     * @return a short description of this host's own screen state, in its own words - the screen half of
     *         the view-state line, the show-or-hide half being every host's alike and prefixed by
     *         {@link #describeViewState()}
     */
    protected abstract String describeHostScreenViewState();

    /**
     * @return this host's screen's picks, for a subclass to lay the panel out around the lit tab and the
     *         tabs this screen is offered. The same value the shortcut key reads and writes, so the layout
     *         and the jump never disagree on which screen's state they are answering for
     */
    protected final ScreenLayerPicks getScreenPicks() {
        return screenPicks;
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

    // Each offered layer's bound keycode in row order, so a matched index maps back to its layer. Taken from
    // the layer rather than read out of KMU's settings here, so a layer from another mod claims a key
    // through whatever its own mod stores bindings in; the binder skips a non-positive keycode, so an
    // unbound layer contributes a row without ever matching a press.
    private static List<Integer> resolveLayerKeycodes(List<MapLayer> layers) {
        return layers.stream()
            .map(MapLayer::resolveShortcutKeycode)
            .toList();
    }
}
