package kmu.maplayers.base.sidebar.runtime;

import kmlib.starsector.ui.input.TabPanelController;

import kmu.maplayers.base.sidebar.SidebarFoldSelection;

/**
 * The plumbing every sidebar host shares: the panel's controller, the fold selection behind it, and the
 * reseed that opens the panel at the fold a loaded save was left at. A concrete host supplies its own fold
 * selection - its own key and its own opening default - and answers the questions that actually differ
 * between screens: when the sidebar is live, where it anchors, which frame edges it strokes, what a key
 * press means, and how its view state reads.
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

    // The panel's scroll and collapse state. Seeded from the fold selection at construction so the panel is
    // safe to draw before any save is loaded, then replaced per load by restoreFoldFromSave.
    private TabPanelController controller;

    protected BaseSidebarHost(SidebarFoldSelection foldSelection) {
        this.foldSelection = foldSelection;
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
     * Opens the panel at the fold the loaded save was left at, replacing the controller with one seeded at
     * that end so the panel is already there on the first frame rather than sliding into place. Call once on
     * game load: a host is a process-lifetime singleton built long before any sector exists, so its
     * construction cannot read the save and only a per-load reseed can.
     */
    public final void restoreFoldFromSave() {
        controller = createControllerAtFold(foldSelection.isRailDocked());
    }

    // A controller opened at the given fold. The docked seed and the expanded default are the widget's own
    // two constructors, so the fold a host opens at is chosen here rather than animated into.
    private static TabPanelController createControllerAtFold(boolean isRailDocked) {
        return isRailDocked
                ? TabPanelController.createStartingDocked()
                : new TabPanelController();
    }
}
