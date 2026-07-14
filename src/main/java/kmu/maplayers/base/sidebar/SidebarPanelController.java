package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.input.TabPanelController;

/**
 * The one panel controller the map sidebar owns. The sidebar is a single tab panel, so its scroll and drag
 * runtime state is one app-wide {@link TabPanelController} the render pass (which reads its scroll position
 * to lay the panel out) and the input pass (which feeds it pointer events) share. The reusable input and
 * scroll behaviour lives in the KMLib {@link TabPanelController} (which routes the header tabs and delegates
 * the body to a {@code PanelController}); this only names the instance the sidebar owns.
 */
public final class SidebarPanelController {
    /** The map sidebar's single tab-panel controller. */
    public static final TabPanelController INSTANCE = new TabPanelController();

    private SidebarPanelController() {
    }
}
