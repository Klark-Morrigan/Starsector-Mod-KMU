package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.BoxEdge;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.intel.IntelScreenView;
import kmlib.starsector.ui.intel.VanillaIntelScreenView;
import kmlib.starsector.ui.render.gl.WidgetStyle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabStyle;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.PersistedSidebarFold;
import kmu.maplayers.base.sidebar.style.SidebarStyles;

import java.util.EnumSet;
import java.util.Set;

/**
 * The intel screen's binding for the map-layer sidebar: it shows while the intel screen's embedded map
 * preview (the "visor") is lit and drawing the ordinary map, and overlays the panel on that visor anchored
 * to its top-left. This is the intel-screen sibling of {@link MapSidebarHost}; the two feed the shared
 * {@link SidebarRenderer} / {@link SidebarInput} and differ only in gate, anchor, controller, and which
 * frame edges they stroke.
 *
 * <p>Its panel opens at the fold this save was left at, and a save holding no choice yet opens docked, so
 * the rail never covers the visor uninvited - the player expands it by the collapse handle when they want
 * the controls, and finds it that way again next session. Only the resting end persists; the collapse
 * animation's in-flight progress does not, since a half-slid rail is not a choice worth restoring.
 * Because it sits flush against the visor's edges, it omits the
 * border edges it shares with the visor - the left always, and the bottom when the box reaches the visor's
 * bottom - so its frame reads as part of the visor rather than a second box drawn over it. The "is the
 * sidebar live" gate is "is there a live canvas under it":
 * {@link IntelScreenView#getMapVisorRect()} returns the visor rectangle while it is lit and
 * {@code null} when the intel tab is not showing, one of the sub-tabs that share it (Planets, Factions) is up
 * instead, or a large-description item has blanked the preview, so a non-null rectangle is both the gate and
 * the anchor. Gating on the rectangle rather than on {@link IntelScreenView#isIntelTabOpen()} is what keeps
 * the sidebar off those sibling sub-tabs, which are the same core tab but carry no visor. The visor's own
 * Starscape filter ({@link IntelScreenView#isMapStarscapeModeOn()}) is no part of that gate: the layers
 * paint through several terrain surfaces, of which two draw in Starscape mode, so a lit visor has an
 * overlay under these controls in either look. It stays a reported signal because the intel screen carries its own
 * filter, separate from the full campaign map's, and which look a visor is wearing is worth a diagnostic
 * line. Every read reaches the game's concrete intel panel through the KMLib seam, which fails closed, so a
 * missing link simply hides the sidebar.
 */
public final class IntelSidebarHost extends BaseSidebarHost {
    
    /**
     * The one intel-screen host; the render and input listeners registered for the intel screen reference it.
     * This is where the live intel-screen binding is chosen, the host itself naming only the role.
     */
    public static final IntelSidebarHost INSTANCE = new IntelSidebarHost(new VanillaIntelScreenView());

    // How tall this screen's tab band stands: this sidebar overlays the visor under the vanilla map
    // toggles and reads tighter than the on-map one, so it crowds the preview less. Content-space - the
    // panel strokes its own top border above the band, so the drawn strip stands the border width taller.
    // Package-private so the departure is checkable without a live sector, which the style built from it
    // needs to resolve its colours.
    static final float HEADER_BAND_HEIGHT = 17f;

    // How close the box's bottom must sit to the visor's bottom to count as flush, in pixels: the box lands
    // on round(visor.y) when its content fills the visor's height, so a one-pixel tolerance absorbs that
    // rounding while a box floating clear of the visor bottom stays well outside it.
    private static final float BOTTOM_FLUSH_TOLERANCE = 1f;

    // Save-serialised key of this panel's resting fold; frozen once shipped, since renaming it silently
    // re-docks every existing save. Its own key rather than a widening of the active-layer pick: the pick
    // stores which tab is lit and the fold stores whether the body is folded away, and the two move
    // independently - the rail can be docked with a layer still the lit tab.
    private static final String INTEL_SIDEBAR_DOCKED_KEY = "$kmu_political_intel_sidebar_docked";

    // Reads whether the intel tab is up and the lit visor's screen rectangle - the seam into the game's
    // concrete intel panel, failing closed to null when there is no lit visor to draw over. Handed in
    // rather than constructed here, so the host depends on the intel-screen role and nothing else knows
    // which binding backs it; INSTANCE is where the live one is named.
    private final IntelScreenView intelScreen;

    IntelSidebarHost(IntelScreenView intelScreen) {
        // Opens folded to the rail on a save that has never moved it, so the panel never covers the visor
        // uninvited - the player expands it by the collapse handle when they want the controls. The intel
        // screen's own pick goes with it: a switch on the map screen leaves it where it was, and reopening
        // the intel screen returns to this pick rather than inheriting the map's.
        super(
            new PersistedSidebarFold(INTEL_SIDEBAR_DOCKED_KEY, true),
            MapLayerRegistry.getIntelSelection());
        this.intelScreen = intelScreen;
    }

    @Override
    public boolean isOverlayShowing() {
        // One reading, asking whether there is a live canvas under the panel: the rectangle covers the
        // intel tab not showing and a blanked preview alike. Which look that canvas wears is not asked,
        // since the layer overlay these controls drive paints in both.
        return intelScreen.getMapVisorRect() != null;
    }

    @Override
    public TabPanelPlacement resolvePlacement() {
        var mapVisorRect = intelScreen.getMapVisorRect();
        if (mapVisorRect == null) {
            return null;
        }
        return LiveSidebarPlacement.resolveIntelPlacement(
            mapVisorRect,
            buildTabStyle(),
            getController(),
            getLayerSelection(),
            layoutBorderEdges());
    }

    @Override
    public WidgetStyle resolveWidgetStyle() {
        // The map's look for now, band height apart: this host owning it is what makes the intel screen's
        // own frame colour and chrome a change here rather than a branch in the shared render pass.
        return SidebarStyles.buildPlayerAccentedStyle(buildTabStyle());
    }

    @Override
    public Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement) {
        return decideBorderEdges(
            placement.body().box().y(),
            intelScreen.getMapVisorRect());
    }

    @Override
    public String describeViewState() {
        if (!intelScreen.isIntelTabOpen()) {
            return "intel tab not showing";
        }
        // The seam reports the visor's absence as one state, so the diagnostic names both ways it can
        // arise rather than claiming one: a sibling sub-tab (Planets, Factions) is showing, or a
        // large-description item has blanked the preview.
        if (intelScreen.getMapVisorRect() == null) {
            return "intel tab; no visor (sub-tab or blanked preview)";
        }
        // A lit visor is the whole gate, so this last pair reports which look it is wearing rather than a
        // reason for hiding: it is what says whether the Starscape surfaces are the ones that should be
        // painting under the panel.
        return intelScreen.isMapStarscapeModeOn()
            ? "intel tab; visor lit; Starscape on"
            : "intel tab; visor lit; Starscape off";
    }

    // Which frame edges the box reserves inset space for, decided before layout so a dropped edge collapses
    // its strip rather than leaving it bare. The left is always dropped (the box sits flush against the
    // visor's left edge, so reserving a left border would leave a gap between the visor and the content);
    // the top and right frame the sidebar inside the visor; the bottom keeps its reserved inset for now -
    // its stroke still drops when the box reaches the visor bottom (see decideBorderEdges), but collapsing
    // the bottom strip too is deferred. The left is dropped here and by decideBorderEdges alike, so the
    // reserved space and the stroke never disagree on it.
    static Set<BoxEdge> layoutBorderEdges() {
        return EnumSet.of(BoxEdge.TOP, BoxEdge.RIGHT, BoxEdge.BOTTOM);
    }

    // Which frame edges to stroke for a box anchored to the visor's top-left: the edges it reserved space
    // for, minus the bottom when the box's bottom reaches the visor's bottom (flush there, within the
    // rounding tolerance) so a shared bottom border does not double the visor's frame. Derived from the
    // reserved edges so the left, dropped there, is never stroked here. The bottom stroke drops on flush
    // while its reserved inset stays, so a flush box keeps a thin bottom strip until that collapse lands.
    static Set<BoxEdge> decideBorderEdges(float boxBottomY, Rectangle mapVisorRect) {
        var edges = EnumSet.copyOf(layoutBorderEdges());
        var isBottomFlush = mapVisorRect != null
            && boxBottomY <= mapVisorRect.y() + BOTTOM_FLUSH_TOLERANCE;
            
        if (isBottomFlush) {
            edges.remove(BoxEdge.BOTTOM);
        }
        return edges;
    }

    private static TabStyle buildTabStyle() {
        return SidebarStyles.buildTabStyle(HEADER_BAND_HEIGHT);
    }
}
