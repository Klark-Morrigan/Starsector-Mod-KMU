package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.intel.IntelScreenView;
import kmlib.starsector.ui.intel.VanillaIntelScreenView;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.sidebar.LiveSidebarPlacement;

/**
 * The intel screen's binding for the political-map sidebar: it shows while the intel screen's embedded map
 * preview (the "visor") is lit, docks the panel in the desc column beside that visor, and has no keyboard
 * role. This is the intel-screen sibling of {@link MapSidebarHost}; the two feed the shared {@link
 * SidebarRenderer} / {@link SidebarInput} and differ only in gate, anchor, controller, and keys.
 *
 * <p>Its panel opens docked, so the rail never covers the intel screen's desc column uninvited - the player
 * expands it by the collapse handle when they want the controls. The "is the sidebar live" gate is exactly
 * "is the visor lit": {@link IntelScreenView#getVisorRect()} returns the visor rectangle while it is lit and
 * {@code null} when the intel tab is not showing or a large-description item has blanked the preview, so a
 * non-null rectangle is both the gate and the anchor. The visor rectangle reaches the game's concrete intel
 * panel through the KMLib seam, which fails closed to {@code null}, so a missing link simply hides the
 * sidebar.
 */
public final class IntelSidebarHost implements SidebarHost {
    /** The one intel-screen host; the render and input listeners registered for the intel screen reference it. */
    public static final IntelSidebarHost INSTANCE = new IntelSidebarHost();

    // Reads whether the intel tab is up and the lit visor's screen rectangle - the seam into the game's
    // concrete intel panel, failing closed to null when there is no lit visor to draw over.
    private final IntelScreenView intelScreen = new VanillaIntelScreenView();

    // The intel panel's scroll and collapse state, opening docked so the rail stays out of the desc column
    // until the player expands it. Its own controller, separate from the on-map panel's.
    private final TabPanelController controller = TabPanelController.createStartingDocked();

    private IntelSidebarHost() {
    }

    @Override
    public boolean isOverlayShowing() {
        // The sidebar is live exactly while the visor is lit; a null rectangle covers the intel tab not
        // showing and a blanked preview both.
        return intelScreen.getVisorRect() != null;
    }

    @Override
    public TabPanelPlacement resolvePlacement() {
        var visorRect = intelScreen.getVisorRect();
        if (visorRect == null) {
            return null;
        }
        return LiveSidebarPlacement.resolveIntelPlacement(visorRect, controller);
    }

    @Override
    public TabPanelController getController() {
        return controller;
    }

    @Override
    public void handleKeyPress(InputEventAPI event) {
        // No keyboard role on the intel screen: the layer shortcuts are the on-map host's, and the intel
        // screen has its own key bindings, so a key press falls through to the screen untouched.
    }

    @Override
    public String describeViewState() {
        if (!intelScreen.isIntelTabOpen()) {
            return "intel tab not showing";
        }
        return intelScreen.getVisorRect() != null ? "intel tab; visor lit" : "intel tab; visor blanked";
    }
}
