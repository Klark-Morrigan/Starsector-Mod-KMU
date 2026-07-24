package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.intel.IntelScreenView;
import kmlib.starsector.ui.intel.VanillaIntelScreenView;
import kmlib.starsector.ui.render.gl.BoxEdge;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.sidebar.LiveSidebarPlacement;

import java.util.EnumSet;
import java.util.Set;

/**
 * The intel screen's binding for the political-map sidebar: it shows while the intel screen's embedded map
 * preview (the "visor") is lit, overlays the panel on that visor anchored to its top-left, and has no
 * keyboard role. This is the intel-screen sibling of {@link MapSidebarHost}; the two feed the shared {@link
 * SidebarRenderer} / {@link SidebarInput} and differ only in gate, anchor, controller, keys, and which
 * frame edges they stroke.
 *
 * <p>Its panel opens docked, so the rail never covers the visor uninvited - the player expands it by the
 * collapse handle when they want the controls. Because it sits flush against the visor's edges, it omits the
 * border edges it shares with the visor - the left always, and the bottom when the box reaches the visor's
 * bottom - so its frame reads as part of the visor rather than a second box drawn over it. The "is the
 * sidebar live" gate is exactly
 * "is the visor lit": {@link IntelScreenView#getVisorRect()} returns the visor rectangle while it is lit and
 * {@code null} when the intel tab is not showing or a large-description item has blanked the preview, so a
 * non-null rectangle is both the gate and the anchor. The visor rectangle reaches the game's concrete intel
 * panel through the KMLib seam, which fails closed to {@code null}, so a missing link simply hides the
 * sidebar.
 */
public final class IntelSidebarHost implements SidebarHost {
    /** The one intel-screen host; the render and input listeners registered for the intel screen reference it. */
    public static final IntelSidebarHost INSTANCE = new IntelSidebarHost();

    // How close the box's bottom must sit to the visor's bottom to count as flush, in pixels: the box lands
    // on round(visor.y) when its content fills the visor's height, so a one-pixel tolerance absorbs that
    // rounding while a box floating clear of the visor bottom stays well outside it.
    private static final float BOTTOM_FLUSH_TOLERANCE = 1f;

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
    public Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement) {
        return decideBorderEdges(
                placement.body().box().y(),
                intelScreen.getVisorRect());
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

    // Which frame edges to stroke for a box anchored to the visor's top-left. The left is always dropped
    // (the box is flush against the visor's left edge, so its left border would double the visor's frame),
    // the top and right are always kept (they sit inside the visor and delineate the sidebar), and the
    // bottom is dropped only when the box's bottom reaches the visor's bottom (flush there too, within the
    // rounding tolerance) - kept when the box floats clear of it or there is no visor to measure against.
    static Set<BoxEdge> decideBorderEdges(float boxBottomY, Rectangle visorRect) {
        var edges = EnumSet.of(BoxEdge.TOP, BoxEdge.RIGHT);
        var isBottomFlush = visorRect != null
                && boxBottomY <= visorRect.y() + BOTTOM_FLUSH_TOLERANCE;
        if (!isBottomFlush) {
            edges.add(BoxEdge.BOTTOM);
        }
        return edges;
    }
}
