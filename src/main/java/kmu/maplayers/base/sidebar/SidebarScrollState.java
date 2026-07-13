package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.ScrollState;

/**
 * The one scroll position the map sidebar owns. The sidebar has exactly one scrolling control (the bloc
 * list), so its scroll position is one app-wide {@link ScrollState} the render pass (which reads it) and
 * the input pass (which scrolls and drags it) share. The reusable state and its clamp semantics live in
 * the KMLib {@link ScrollState}; this only names the instance the sidebar owns, until the render/input
 * harness moves to KMLib and owns it directly.
 */
public final class SidebarScrollState {
    /** The map sidebar's single scroll position. */
    public static final ScrollState INSTANCE = new ScrollState();

    private SidebarScrollState() {
    }
}
