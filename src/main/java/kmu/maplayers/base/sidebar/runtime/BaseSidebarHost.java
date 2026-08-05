package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.widgets.tabs.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.tabs.TabStrip;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarFoldSelection;
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
 * <p>The controller is replaced on each load rather than mutated, because the two folds a panel can open at
 * are exactly the two constructors the widget already offers, so no reach into the collapse animation is
 * needed to seed it. Replacing also clears the previous save's scroll offset and fold together, which
 * matters because hosts are process-lifetime singletons: without the reseed, one save's panel state would
 * carry into the next save loaded in the same run.
 */
public abstract class BaseSidebarHost implements SidebarHost {
    
    // Where this host's panel fold is read from and recorded to. Supplied by the concrete host, so the
    // frozen memory key and the fold the screen opens at stay with the screen that owns them.
    private final SidebarFoldSelection foldSelection;

    // The screen's active-layer pick: which tab is lit here, and what a shortcut key or a tab press moves.
    // Supplied by the concrete host, so each screen keeps its own pick and a switch on one leaves the other
    // untouched.
    private final ActiveLayerSelection layerSelection;

    // The panel's scroll and collapse state. Seeded from the fold selection at construction so the panel is
    // safe to draw before any save is loaded, then replaced per load by restoreFoldFromSave.
    private TabPanelController controller;

    protected BaseSidebarHost(SidebarFoldSelection foldSelection, ActiveLayerSelection layerSelection) {
        this.foldSelection = foldSelection;
        this.layerSelection = layerSelection;
        this.controller = createControllerAtFold(foldSelection.isRailDocked());
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
     * Opens the panel at the fold the loaded save was left at, replacing the controller with one seeded at
     * that end so the panel is already there on the first frame rather than sliding into place. Replacing
     * also clears the previous save's scroll offset in the same move.
     */
    @Override
    public final void restoreFoldFromSave() {
        controller = createControllerAtFold(foldSelection.isRailDocked());
    }

    /**
     * @return this host's screen's active-layer pick, for a subclass to lay the panel out around the lit
     *         tab. The same selection the shortcut key writes, so the layout and the jump never disagree
     *         on which pick is this screen's
     */
    protected final ActiveLayerSelection getLayerSelection() {
        return layerSelection;
    }

    // A controller opened at the given fold. The docked seed and the expanded default are the widget's own
    // two constructors, so the fold a host opens at is chosen here rather than animated into.
    private static TabPanelController createControllerAtFold(boolean isRailDocked) {
        return isRailDocked
            ? TabPanelController.createStartingDocked()
            : new TabPanelController();
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
