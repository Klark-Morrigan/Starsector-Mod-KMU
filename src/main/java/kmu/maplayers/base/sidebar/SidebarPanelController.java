package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.input.PanelController;

/**
 * The one panel controller the map sidebar owns. The sidebar is a single panel, so its scroll and drag
 * runtime state is one app-wide {@link PanelController} the render pass (which reads its scroll position to
 * lay the panel out) and the input pass (which feeds it pointer events) share. The reusable input and
 * scroll behaviour lives in the KMLib {@link PanelController}; this only names the instance the sidebar
 * owns.
 */
public final class SidebarPanelController {
    /** The map sidebar's single panel controller. */
    public static final PanelController INSTANCE = new PanelController();

    private SidebarPanelController() {
    }
}
